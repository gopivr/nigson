terraform {
  required_version = "= 1.12.2"
  required_providers {
    aws = { source = "hashicorp/aws", version = "6.15.0" }
  }
}

locals {
  tags = {
    Application = "nigson"
    Environment = var.environment
    ManagedBy   = "terraform"
    Policy      = var.policy_revision
  }
  enabled_services = { for name, service in var.services : name => service if service.enabled }
  public_services  = { for name, service in var.services : name => service if service.public }
  route_rules = merge({}, [for route_name, route in var.public_routes : {
    for pattern_index, pattern in route.path_patterns : "${route_name}-${pattern_index}" => {
      priority = route.priority + pattern_index
      service  = route.service
      pattern  = pattern
    }
  }]...)
  egress_rules = merge([for service_name, service in var.services : {
    for rule_name, rule in service.egress : "${service_name}-${rule_name}" => {
      service = service_name
      rule    = rule
    }
  }]...)
}

resource "aws_ecs_cluster" "main" {
  name = "nigson-${var.environment}"
  setting {
    name  = "containerInsights"
    value = "enhanced"
  }
  tags = local.tags
}

resource "aws_service_discovery_private_dns_namespace" "services" {
  name        = var.private_namespace
  description = "Private Nigson service discovery for ${var.environment}"
  vpc         = var.vpc_id
  tags        = local.tags
}

resource "aws_ecr_repository" "service" {
  for_each             = var.services
  name                 = "nigson/${var.environment}/${each.key}"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration { scan_on_push = true }
  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = var.kms_key_arn
  }
  tags = merge(local.tags, { Service = each.key })
}

resource "aws_ecr_lifecycle_policy" "service" {
  for_each   = var.services
  repository = aws_ecr_repository.service[each.key].name
  policy     = jsonencode({ rules = [{ rulePriority = 1, description = "Expire untagged layers after seven days", selection = { tagStatus = "untagged", countType = "sinceImagePushed", countUnit = "days", countNumber = 7 }, action = { type = "expire" } }] })
}

resource "aws_cloudwatch_log_group" "service" {
  for_each          = var.services
  name              = "/nigson/${var.environment}/${each.key}"
  kms_key_id        = var.kms_key_arn
  retention_in_days = var.log_retention_days
  tags              = merge(local.tags, { Service = each.key })
}

resource "aws_iam_role" "execution" {
  for_each           = var.services
  name               = "nigson-${var.environment}-${each.key}-execution"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole" }] })
  tags               = merge(local.tags, { Service = each.key, Role = "execution" })
}

resource "aws_iam_role_policy" "execution_base" {
  for_each = var.services
  name     = "nigson-${var.environment}-${each.key}-execution-base"
  role     = aws_iam_role.execution[each.key].id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage"], Resource = aws_ecr_repository.service[each.key].arn },
    { Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = "${aws_cloudwatch_log_group.service[each.key].arn}:*" }
  ] })
}

resource "aws_iam_role_policy" "execution_secret_reads" {
  for_each = { for name, service in var.services : name => service if length(service.secret_arns) > 0 }
  name     = "nigson-${var.environment}-${each.key}-named-secrets"
  role     = aws_iam_role.execution[each.key].id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["secretsmanager:GetSecretValue"], Resource = values(each.value.secret_arns) },
    { Effect = "Allow", Action = ["kms:Decrypt"], Resource = var.kms_key_arn, Condition = { StringEquals = { "kms:ViaService" = "secretsmanager.${var.region}.amazonaws.com" }, "ForAnyValue:StringEquals" = { "kms:EncryptionContext:SecretARN" = values(each.value.secret_arns) } } }
  ] })
}

resource "aws_iam_role" "task" {
  for_each           = var.services
  name               = "nigson-${var.environment}-${each.key}-task"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole" }] })
  tags               = merge(local.tags, { Service = each.key, Role = "task" })
}

resource "aws_security_group" "load_balancer" {
  name        = "nigson-${var.environment}-public-alb"
  description = "Public HTTPS ingress to the WAF-protected application ALB"
  vpc_id      = var.vpc_id
  ingress {
    description = "HTTP redirect only"
    protocol    = "tcp"
    from_port   = 80
    to_port     = 80
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "TLS ingress; WAF and ALB rules restrict application paths"
    protocol    = "tcp"
    from_port   = 443
    to_port     = 443
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress = []
  tags   = local.tags
}

resource "aws_security_group" "service" {
  for_each    = var.services
  name        = "nigson-${var.environment}-${each.key}"
  description = "Private application task ingress and explicit destination egress"
  vpc_id      = var.vpc_id
  egress      = []
  tags        = merge(local.tags, { Service = each.key })
}

resource "aws_vpc_security_group_ingress_rule" "public_service" {
  for_each                     = local.public_services
  security_group_id            = aws_security_group.service[each.key].id
  referenced_security_group_id = aws_security_group.load_balancer.id
  ip_protocol                  = "tcp"
  from_port                    = each.value.port
  to_port                      = each.value.port
  description                  = "Only the public ALB can reach ${each.key}"
}

resource "aws_vpc_security_group_egress_rule" "load_balancer" {
  for_each                     = local.public_services
  security_group_id            = aws_security_group.load_balancer.id
  referenced_security_group_id = aws_security_group.service[each.key].id
  ip_protocol                  = "tcp"
  from_port                    = each.value.port
  to_port                      = each.value.port
  description                  = "Forward only to registered public service ${each.key}"
}

resource "aws_vpc_security_group_egress_rule" "service" {
  for_each          = local.egress_rules
  security_group_id = aws_security_group.service[each.value.service].id
  cidr_ipv4         = each.value.rule.cidr_ipv4
  ip_protocol       = each.value.rule.protocol
  from_port         = each.value.rule.port
  to_port           = each.value.rule.port
  description       = each.value.rule.description
}

resource "aws_lb" "public" {
  name                       = "nigson-${var.environment}-public"
  internal                   = false
  load_balancer_type         = "application"
  subnets                    = var.edge_subnet_ids
  security_groups            = [aws_security_group.load_balancer.id]
  drop_invalid_header_fields = true
  desync_mitigation_mode     = "strictest"
  enable_deletion_protection = var.environment == "prod" ? true : var.alb_deletion_protection
  tags                       = local.tags
}

resource "aws_wafv2_web_acl_association" "public" {
  resource_arn = aws_lb.public.arn
  web_acl_arn  = var.waf_web_acl_arn
}

resource "aws_lb_target_group" "service" {
  for_each    = local.public_services
  name        = "nigson-${var.environment}-${each.key}"
  port        = each.value.port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id
  health_check {
    enabled             = true
    path                = each.value.health_path
    protocol            = "HTTP"
    matcher             = "200-399"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
  tags = merge(local.tags, { Service = each.key })
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.public.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.public.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.public_certificate_arn
  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Not found"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "deny_internal" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 1
  action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Not found"
      status_code  = "404"
    }
  }
  condition {
    path_pattern { values = ["/internal", "/internal/*", "/internal*"] }
  }
}

resource "aws_lb_listener_rule" "public_route" {
  for_each     = local.route_rules
  listener_arn = aws_lb_listener.https.arn
  priority     = each.value.priority
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.service[each.value.service].arn
  }
  condition {
    path_pattern { values = [each.value.pattern] }
  }
}

resource "aws_service_discovery_service" "service" {
  for_each = var.services
  name     = each.key
  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.services.id
    dns_records {
      ttl  = 10
      type = "A"
    }
    routing_policy = "MULTIVALUE"
  }
  tags = merge(local.tags, { Service = each.key })
}

resource "aws_ecs_task_definition" "service" {
  for_each                 = var.services
  family                   = "nigson-${var.environment}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = each.value.cpu
  memory                   = each.value.memory
  execution_role_arn       = aws_iam_role.execution[each.key].arn
  task_role_arn            = aws_iam_role.task[each.key].arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = each.value.architecture
  }
  volume { name = "tmp" }
  container_definitions = jsonencode([{
    name                   = each.key
    image                  = "${aws_ecr_repository.service[each.key].repository_url}@${each.value.image_digest}"
    essential              = true
    user                   = "10001:10001"
    readonlyRootFilesystem = true
    stopTimeout            = 30
    portMappings           = [{ containerPort = each.value.port, hostPort = each.value.port, protocol = "tcp" }]
    environment            = [for name, value in merge({ NIGSON_ENVIRONMENT = var.environment, SERVER_PORT = tostring(each.value.port) }, each.value.environment) : { name = name, value = value }]
    secrets                = [for name, arn in each.value.secret_arns : { name = name, valueFrom = arn }]
    mountPoints            = [{ sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }]
    healthCheck            = { command = each.value.probe_command, interval = 30, timeout = 5, retries = 3, startPeriod = 60 }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.service[each.key].name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = each.key
      }
    }
  }])
  tags = merge(local.tags, { Service = each.key })
}

resource "aws_ecs_service" "service" {
  for_each                           = var.services
  name                               = "nigson-${var.environment}-${each.key}"
  cluster                            = aws_ecs_cluster.main.id
  task_definition                    = aws_ecs_task_definition.service[each.key].arn
  desired_count                      = each.value.enabled ? each.value.desired_count : 0
  launch_type                        = "FARGATE"
  platform_version                   = "LATEST"
  enable_execute_command             = false
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  network_configuration {
    subnets          = var.application_subnet_ids
    security_groups  = [aws_security_group.service[each.key].id]
    assign_public_ip = false
  }
  dynamic "load_balancer" {
    for_each = each.value.public ? [each.key] : []
    content {
      target_group_arn = aws_lb_target_group.service[each.key].arn
      container_name   = each.key
      container_port   = each.value.port
    }
  }
  service_registries { registry_arn = aws_service_discovery_service.service[each.key].arn }
  depends_on = [aws_lb_listener.https, aws_wafv2_web_acl_association.public]
  lifecycle { ignore_changes = [desired_count] }
  tags = merge(local.tags, { Service = each.key })
}

resource "aws_appautoscaling_target" "service" {
  for_each           = local.enabled_services
  max_capacity       = each.value.max_count
  min_capacity       = each.value.min_count
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.service[each.key].name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  for_each           = local.enabled_services
  name               = "nigson-${var.environment}-${each.key}-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.service[each.key].resource_id
  scalable_dimension = aws_appautoscaling_target.service[each.key].scalable_dimension
  service_namespace  = aws_appautoscaling_target.service[each.key].service_namespace
  target_tracking_scaling_policy_configuration {
    target_value       = var.cpu_target_percent
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
    predefined_metric_specification { predefined_metric_type = "ECSServiceAverageCPUUtilization" }
  }
}

output "cluster_arn" { value = aws_ecs_cluster.main.arn }
output "load_balancer_arn" { value = aws_lb.public.arn }
output "load_balancer_dns_name" { value = aws_lb.public.dns_name }
output "service_security_group_ids" { value = { for name, group in aws_security_group.service : name => group.id } }
output "task_role_arns" { value = { for name, role in aws_iam_role.task : name => role.arn } }
output "repository_urls" { value = { for name, repository in aws_ecr_repository.service : name => repository.repository_url } }
output "private_service_names" { value = { for name, service in aws_service_discovery_service.service : name => "${name}.${var.private_namespace}" } }
