data "aws_region" "current" {}
locals {
  name = "nigson-${var.environment}-identity"
  tags = { Service = "keycloak", Environment = var.environment, Policy = var.policy_revision, Operator = var.named_operator }
}
resource "aws_security_group" "public" {
  name   = "${local.name}-public"
  vpc_id = var.vpc_id
  tags   = local.tags
}
resource "aws_security_group" "admin" {
  name   = "${local.name}-admin"
  vpc_id = var.vpc_id
  tags   = local.tags
}
resource "aws_security_group" "server" {
  name   = "${local.name}-server"
  vpc_id = var.vpc_id
  tags   = local.tags
}
resource "aws_security_group" "database" {
  name   = "${local.name}-database"
  vpc_id = var.vpc_id
  tags   = local.tags
}
resource "aws_ecr_repository" "identity" {
  name                 = "nigson/${var.environment}/identity"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration { scan_on_push = true }
  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = var.kms_key_arn
  }
  tags = local.tags
}
resource "aws_vpc_security_group_ingress_rule" "public_tls" {
  security_group_id = aws_security_group.public.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}
resource "aws_vpc_security_group_ingress_rule" "admin_tls" {
  for_each          = toset(var.admin_cidrs)
  security_group_id = aws_security_group.admin.id
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}
resource "aws_vpc_security_group_ingress_rule" "from_load_balancer" {
  for_each                     = { public = aws_security_group.public.id, admin = aws_security_group.admin.id }
  security_group_id            = aws_security_group.server.id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 8443
  to_port                      = 8443
}
resource "aws_vpc_security_group_ingress_rule" "management" {
  for_each = merge(
    { public = aws_security_group.public.id, admin = aws_security_group.admin.id },
    { for id in var.metrics_security_group_ids : "metrics-${id}" => id }
  )
  security_group_id            = aws_security_group.server.id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 9000
  to_port                      = 9000
}
resource "aws_vpc_security_group_ingress_rule" "cluster" {
  for_each                     = toset(["7800", "57800"])
  security_group_id            = aws_security_group.server.id
  referenced_security_group_id = aws_security_group.server.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
}
resource "aws_vpc_security_group_ingress_rule" "database" {
  for_each                     = merge({ identity = aws_security_group.server.id }, var.owner_task_security_groups)
  security_group_id            = aws_security_group.database.id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}
resource "aws_vpc_security_group_egress_rule" "load_balancer" {
  for_each = {
    public_app = { sg = aws_security_group.public.id, port = 8443 }, public_health = { sg = aws_security_group.public.id, port = 9000 },
    admin_app  = { sg = aws_security_group.admin.id, port = 8443 }, admin_health = { sg = aws_security_group.admin.id, port = 9000 }
  }
  security_group_id            = each.value.sg
  referenced_security_group_id = aws_security_group.server.id
  ip_protocol                  = "tcp"
  from_port                    = each.value.port
  to_port                      = each.value.port
}
resource "aws_vpc_security_group_egress_rule" "server" {
  for_each = {
    database        = { sg = aws_security_group.database.id, port = 5432 }, cluster = { sg = aws_security_group.server.id, port = 7800 },
    cluster_failure = { sg = aws_security_group.server.id, port = 57800 }, endpoints = { sg = var.aws_endpoint_security_group_id, port = 443 }
  }
  security_group_id            = aws_security_group.server.id
  referenced_security_group_id = each.value.sg
  ip_protocol                  = "tcp"
  from_port                    = each.value.port
  to_port                      = each.value.port
}
resource "aws_vpc_security_group_egress_rule" "dns" {
  for_each          = toset(["udp", "tcp"])
  security_group_id = aws_security_group.server.id
  cidr_ipv4         = var.vpc_cidr
  ip_protocol       = each.value
  from_port         = 53
  to_port           = 53
}
resource "aws_db_subnet_group" "identity" {
  name       = local.name
  subnet_ids = var.database_subnet_ids
  tags       = local.tags
}
resource "aws_db_parameter_group" "identity" {
  name   = local.name
  family = "postgres18"
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
  parameter {
    name  = "log_statement"
    value = "none"
  }
  parameter {
    name  = "log_min_error_statement"
    value = "panic"
  }
  tags = local.tags
}
resource "aws_db_instance" "identity" {
  identifier                      = local.name
  engine                          = "postgres"
  engine_version                  = var.postgres_version
  instance_class                  = var.database_instance_class
  allocated_storage               = 20
  max_allocated_storage           = 100
  storage_type                    = "gp3"
  storage_encrypted               = true
  kms_key_id                      = var.kms_key_arn
  username                        = "nigson_identity_admin"
  manage_master_user_password     = true
  master_user_secret_kms_key_id   = var.kms_key_arn
  multi_az                        = true
  publicly_accessible             = false
  db_subnet_group_name            = aws_db_subnet_group.identity.name
  vpc_security_group_ids          = [aws_security_group.database.id]
  parameter_group_name            = aws_db_parameter_group.identity.name
  backup_retention_period         = var.backup_retention_days
  copy_tags_to_snapshot           = true
  deletion_protection             = true
  skip_final_snapshot             = false
  final_snapshot_identifier       = "${local.name}-operator-final-${substr(sha256(var.policy_revision), 0, 8)}"
  auto_minor_version_upgrade      = false
  allow_major_version_upgrade     = false
  enabled_cloudwatch_logs_exports = ["postgresql"]
  depends_on                      = [aws_cloudwatch_log_group.database]
  tags                            = local.tags
}
resource "aws_lb" "public" {
  name                       = local.name
  internal                   = false
  load_balancer_type         = "application"
  subnets                    = var.public_subnet_ids
  security_groups            = [aws_security_group.public.id]
  drop_invalid_header_fields = true
  desync_mitigation_mode     = "strictest"
  enable_deletion_protection = true
  tags                       = local.tags
}
resource "aws_lb" "admin" {
  name                       = "${local.name}-admin"
  internal                   = true
  load_balancer_type         = "application"
  subnets                    = var.private_subnet_ids
  security_groups            = [aws_security_group.admin.id]
  drop_invalid_header_fields = true
  desync_mitigation_mode     = "strictest"
  enable_deletion_protection = true
  tags                       = local.tags
}
resource "aws_wafv2_web_acl_association" "public" {
  resource_arn = aws_lb.public.arn
  web_acl_arn  = aws_wafv2_web_acl.identity.arn
}
resource "aws_lb_target_group" "identity" {
  for_each             = toset(["public", "admin"])
  name                 = "${local.name}-${each.key}"
  port                 = 8443
  protocol             = "HTTPS"
  target_type          = "ip"
  vpc_id               = var.vpc_id
  deregistration_delay = 30
  health_check {
    port                = "9000"
    protocol            = "HTTPS"
    path                = "/health/ready"
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 2
  }
  tags = local.tags
}
resource "aws_lb_listener" "public" {
  load_balancer_arn = aws_lb.public.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.public_certificate_arn
  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      status_code  = "403"
      message_body = "IDENTITY_PATH_DENIED"
    }
  }
}
resource "aws_lb_listener_rule" "oidc" {
  listener_arn = aws_lb_listener.public.arn
  priority     = 10
  condition {
    host_header { values = [var.public_hostname] }
  }
  condition {
    path_pattern { values = ["/realms/${var.realm}/*", "/resources/*", "/.well-known/openid-configuration"] }
  }
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.identity["public"].arn
  }
}
resource "aws_lb_listener" "admin" {
  load_balancer_arn = aws_lb.admin.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.admin_certificate_arn
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.identity["admin"].arn
  }
}
resource "aws_cloudwatch_log_group" "identity" {
  name              = "/nigson/${var.environment}/identity"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
  tags              = local.tags
}
resource "aws_iam_role" "execution" {
  name               = "${local.name}-execution"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole" }] })
  tags               = local.tags
}
resource "aws_iam_role_policy" "execution" {
  role = aws_iam_role.execution.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage"], Resource = aws_ecr_repository.identity.arn },
    { Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = "${aws_cloudwatch_log_group.identity.arn}:*" },
    { Effect = "Allow", Action = ["secretsmanager:GetSecretValue"], Resource = values(var.secret_refs) },
    { Effect = "Allow", Action = ["kms:Decrypt"], Resource = var.kms_key_arn, Condition = { StringEquals = { "kms:ViaService" = "secretsmanager.${data.aws_region.current.region}.amazonaws.com" }, "ForAnyValue:StringEquals" = { "kms:EncryptionContext:SecretARN" = values(var.secret_refs) } } }
  ] })
}
resource "aws_iam_role" "task" {
  name               = "${local.name}-task"
  assume_role_policy = aws_iam_role.execution.assume_role_policy
  tags               = local.tags
}
resource "aws_ecs_task_definition" "identity" {
  family                   = local.name
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "1024"
  memory                   = "2048"
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }
  volume { name = "identity-data" }
  volume { name = "identity-tmp" }
  container_definitions = jsonencode([{
    name         = "keycloak", image = var.identity_image, essential = true, user = "1000:0", readonlyRootFilesystem = true,
    command      = ["start", "--optimized"], stopTimeout = 120,
    portMappings = [for port in [8443, 9000, 7800, 57800] : { containerPort = port, protocol = "tcp" }],
    mountPoints  = [{ sourceVolume = "identity-data", containerPath = "/opt/keycloak/data", readOnly = false }, { sourceVolume = "identity-tmp", containerPath = "/tmp", readOnly = false }],
    environment = [for name, value in {
      APP_ENV                    = var.environment, KC_HOSTNAME = "https://${var.public_hostname}", KC_HOSTNAME_ADMIN = "https://${var.admin_hostname}",
      KC_DB_URL                  = "jdbc:postgresql://${aws_db_instance.identity.address}:5432/keycloak", KC_DB_USERNAME = "nigson_identity",
      KC_PROXY_TRUSTED_ADDRESSES = join(",", var.trusted_proxy_cidrs), NIGSON_IDENTITY_POLICY_REVISION = var.policy_revision
    } : { name = name, value = value }],
    secrets = [for name, ref in {
      KC_DB_PASSWORD          = var.secret_refs.database_password, NIGSON_IDENTITY_CERT_PEM = var.secret_refs.tls_certificate,
      NIGSON_IDENTITY_KEY_PEM = var.secret_refs.tls_key, NIGSON_IDENTITY_DB_CA_PEM = var.secret_refs.database_ca
    } : { name = name, valueFrom = ref }],
    linuxParameters  = { capabilities = { drop = ["ALL"] }, initProcessEnabled = true },
    logConfiguration = { logDriver = "awslogs", options = { awslogs-group = aws_cloudwatch_log_group.identity.name, awslogs-region = data.aws_region.current.region, awslogs-stream-prefix = "keycloak" } }
  }])
  tags = local.tags
}
resource "aws_ecs_service" "identity" {
  name                               = local.name
  cluster                            = var.ecs_cluster_arn
  task_definition                    = aws_ecs_task_definition.identity.arn
  desired_count                      = var.service_enabled ? 2 : 0
  launch_type                        = "FARGATE"
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  health_check_grace_period_seconds  = 180
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.server.id]
    assign_public_ip = false
  }
  dynamic "load_balancer" {
    for_each = aws_lb_target_group.identity
    content {
      target_group_arn = load_balancer.value.arn
      container_name   = "keycloak"
      container_port   = 8443
    }
  }
  depends_on = [aws_lb_listener.admin, aws_lb_listener_rule.oidc]
  tags       = local.tags
}
resource "aws_cloudwatch_metric_alarm" "replica" {
  alarm_name          = "${local.name}-replicas"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HealthyHostCount"
  dimensions          = { LoadBalancer = aws_lb.public.arn_suffix, TargetGroup = aws_lb_target_group.identity["public"].arn_suffix }
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 2
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = var.alarm_destination_arns
  tags                = local.tags
}
output "issuer" { value = "https://${var.public_hostname}/realms/${var.realm}" }
output "private_admin_url" { value = "https://${var.admin_hostname}" }
output "dedicated_database_address" { value = aws_db_instance.identity.address }
output "database_admin_secret_arn" { value = aws_db_instance.identity.master_user_secret[0].secret_arn }
output "identity_service_name" { value = aws_ecs_service.identity.name }
output "identity_security_group_id" { value = aws_security_group.server.id }
output "identity_database_security_group_id" { value = aws_security_group.database.id }
output "keycloak_task_definition_arn" { value = aws_ecs_task_definition.identity.arn }
output "public_alb_dns_name" { value = aws_lb.public.dns_name }
output "private_admin_alb_dns_name" { value = aws_lb.admin.dns_name }
# Public administrative isolation must not depend only on hostname-admin or ALB raw paths.
resource "aws_wafv2_web_acl" "identity" {
  name  = local.name
  scope = "REGIONAL"
  default_action {
    block {}
  }
  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${local.name}-acl"
    sampled_requests_enabled   = false
  }
  rule {
    name     = "reject-ambiguous-path"
    priority = 0
    action {
      block {}
    }
    statement {
      regex_match_statement {
        regex_string = "[%\\\\;]|(^|/)\\.{1,2}(/|$)"
        field_to_match {
          uri_path {}
        }
        text_transformation {
          priority = 0
          type     = "NONE"
        }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.name}-noncanonical"
      sampled_requests_enabled   = false
    }
  }
  rule {
    name     = "public-identity-only"
    priority = 1
    action {
      allow {}
    }
    statement {
      and_statement {
        statement {
          byte_match_statement {
            search_string         = var.public_hostname
            positional_constraint = "EXACTLY"
            field_to_match {
              single_header { name = "host" }
            }
            text_transformation {
              priority = 0
              type     = "LOWERCASE"
            }
          }
        }
        statement {
          regex_match_statement {
            regex_string = "^/(realms/${var.realm}/|resources/|\\.well-known/)"
            field_to_match {
              uri_path {}
            }
            text_transformation {
              priority = 0
              type     = "URL_DECODE"
            }
            text_transformation {
              priority = 1
              type     = "NORMALIZE_PATH_WIN"
            }
          }
        }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.name}-public"
      sampled_requests_enabled   = false
    }
  }
  tags = local.tags
}
resource "aws_cloudwatch_log_group" "database" {
  name              = "/aws/rds/instance/${local.name}/postgresql"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
  tags              = local.tags
}
# Dedicated bootstrap task runs only when both service replicas are stopped.
# Its credential permissions are not available to the permanent service execution role.
resource "aws_iam_role" "bootstrap_execution" {
  name               = "${local.name}-bootstrap-execution"
  assume_role_policy = aws_iam_role.execution.assume_role_policy
  tags               = local.tags
}
resource "aws_iam_role_policy" "bootstrap_execution" {
  role = aws_iam_role.bootstrap_execution.id
  policy = jsonencode({ Version = "2012-10-17", Statement = concat(jsondecode(aws_iam_role_policy.execution.policy).Statement, [
    { Effect = "Allow", Action = ["secretsmanager:GetSecretValue"], Resource = values(var.bootstrap_secret_refs) },
    { Effect = "Allow", Action = ["kms:Decrypt"], Resource = var.kms_key_arn, Condition = { StringEquals = { "kms:ViaService" = "secretsmanager.${data.aws_region.current.region}.amazonaws.com" }, "ForAnyValue:StringEquals" = { "kms:EncryptionContext:SecretARN" = values(var.bootstrap_secret_refs) } } }
  ]) })
}
resource "aws_ecs_task_definition" "bootstrap" {
  family                   = "${local.name}-bootstrap"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "1024"
  memory                   = "2048"
  execution_role_arn       = aws_iam_role.bootstrap_execution.arn
  task_role_arn            = aws_iam_role.task.arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }
  volume { name = "identity-data" }
  volume { name = "identity-tmp" }
  container_definitions = jsonencode([for container in jsondecode(aws_ecs_task_definition.identity.container_definitions) : merge(container, {
    command = ["bootstrap-admin", "user"],
    secrets = concat(container.secrets, [{ name = "KC_BOOTSTRAP_ADMIN_USERNAME", valueFrom = var.bootstrap_secret_refs.username }, { name = "KC_BOOTSTRAP_ADMIN_PASSWORD", valueFrom = var.bootstrap_secret_refs.password }])
  })])
  tags = local.tags
}
output "bootstrap_task_definition_arn" { value = aws_ecs_task_definition.bootstrap.arn }
output "public_identity_web_acl_arn" { value = aws_wafv2_web_acl.identity.arn }
output "identity_repository_url" { value = aws_ecr_repository.identity.repository_url }
