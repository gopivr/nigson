mock_provider "aws" {}

variables {
  environment                = "stage"
  region                     = "eu-west-1"
  product_phase              = 1
  policy_revision            = "compute-v1"
  vpc_id                     = "vpc-0123456789abcdef0"
  edge_subnet_ids            = ["subnet-edge-a", "subnet-edge-b"]
  application_subnet_ids     = ["subnet-app-a", "subnet-app-b"]
  private_namespace          = "svc.stage.nigson.internal"
  kms_key_arn                = "arn:aws:kms:eu-west-1:123456789012:key/01234567-89ab-cdef-0123-456789abcdef"
  waf_web_acl_arn            = "arn:aws:wafv2:eu-west-1:123456789012:regional/webacl/example/01234567-89ab-cdef-0123-456789abcdef"
  public_certificate_arn     = "arn:aws:acm:eu-west-1:123456789012:certificate/01234567-89ab-cdef-0123-456789abcdef"
  alb_deletion_protection    = false
  log_retention_days         = 30
  database_connection_budget = 120
  cpu_target_percent         = 65
  services = {
    commerce = {
      enabled      = true, public = true, port = 8081, cpu = 1024, memory = 2048, architecture = "X86_64"
      min_count    = 1, desired_count = 1, max_count = 4, db_connections_per_task = 10
      image_digest = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      health_path  = "/actuator/health/readiness", probe_command = ["CMD", "java", "-cp", "/app/probe", "HealthProbe"]
      environment  = {}, secret_arns = {}
      egress       = { aws_endpoints = { description = "AWS interface endpoints", cidr_ipv4 = "10.40.0.0/16", protocol = "tcp", port = 443 } }
    }
    experience = {
      enabled      = true, public = true, port = 8082, cpu = 1024, memory = 2048, architecture = "X86_64"
      min_count    = 1, desired_count = 1, max_count = 4, db_connections_per_task = 10
      image_digest = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
      health_path  = "/actuator/health/readiness", probe_command = ["CMD", "java", "-cp", "/app/probe", "HealthProbe"]
      environment  = {}, secret_arns = {}
      egress       = { aws_endpoints = { description = "AWS interface endpoints", cidr_ipv4 = "10.40.0.0/16", protocol = "tcp", port = 443 } }
    }
    integration = {
      enabled      = true, public = false, port = 8083, cpu = 1024, memory = 2048, architecture = "X86_64"
      min_count    = 1, desired_count = 1, max_count = 2, db_connections_per_task = 10
      image_digest = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
      health_path  = "/actuator/health/readiness", probe_command = ["CMD", "java", "-cp", "/app/probe", "HealthProbe"]
      environment  = {}, secret_arns = {}
      egress       = { aws_endpoints = { description = "AWS interface endpoints", cidr_ipv4 = "10.40.0.0/16", protocol = "tcp", port = 443 } }
    }
  }
  public_routes = {
    commerce_api = { service = "commerce", priority = 10, path_patterns = ["/api/v1/*"] }
  }
}

run "keep_worker_private_and_deny_internal_paths" {
  command = apply

  override_resource {
    target = aws_lb.public
    values = {
      arn      = "arn:aws:elasticloadbalancing:eu-west-1:123456789012:loadbalancer/app/nigson-stage-public/0123456789abcdef"
      dns_name = "nigson-stage-public.example.invalid"
    }
  }
  override_resource {
    target = aws_iam_role.execution
    values = { arn = "arn:aws:iam::123456789012:role/mock-execution" }
  }
  override_resource {
    target = aws_iam_role.task
    values = { arn = "arn:aws:iam::123456789012:role/mock-task" }
  }
  override_resource {
    target = aws_lb_target_group.service
    values = { arn = "arn:aws:elasticloadbalancing:eu-west-1:123456789012:targetgroup/nigson-stage/0123456789abcdef" }
  }
  override_resource {
    target = aws_lb_listener.https
    values = { arn = "arn:aws:elasticloadbalancing:eu-west-1:123456789012:listener/app/nigson-stage-public/0123456789abcdef/abcdef0123456789" }
  }
  override_resource {
    target = aws_ecs_task_definition.service
    values = { arn = "arn:aws:ecs:eu-west-1:123456789012:task-definition/nigson-stage:1" }
  }
  override_resource {
    target = aws_service_discovery_service.service
    values = { arn = "arn:aws:servicediscovery:eu-west-1:123456789012:service/srv-abcdef" }
  }

  assert {
    condition     = aws_ecs_service.service["integration"].network_configuration[0].assign_public_ip == false
    error_message = "Integration worker tasks must not receive public IPs."
  }
  assert {
    condition     = length(aws_security_group.service["integration"].ingress) == 0
    error_message = "Integration has no public or ALB ingress rules."
  }
  assert {
    condition     = aws_lb_listener_rule.deny_internal.action[0].fixed_response[0].status_code == "404"
    error_message = "The public listener must explicitly deny /internal paths."
  }
  assert {
    condition     = aws_lb_listener.https.protocol == "HTTPS" && aws_lb_listener.https.ssl_policy == "ELBSecurityPolicy-TLS13-1-2-2021-06" && aws_lb_listener.https.certificate_arn == var.public_certificate_arn && aws_lb_listener.http.protocol == "HTTP" && aws_lb_listener.http.default_action[0].redirect[0].protocol == "HTTPS"
    error_message = "Public ingress must use the configured certificate and TLS policy, with HTTP redirected to HTTPS."
  }
  assert {
    condition     = jsondecode(aws_ecs_task_definition.service["commerce"].container_definitions)[0].readonlyRootFilesystem == true
    error_message = "Application containers must run with a read-only root filesystem."
  }
  assert {
    condition     = aws_ecr_repository.service["commerce"].image_tag_mutability == "IMMUTABLE" && aws_ecr_repository.service["commerce"].image_scanning_configuration[0].scan_on_push
    error_message = "Published service images must retain immutable tags and scan on push."
  }
}

run "reject_public_worker_route" {
  command = plan
  variables {
    public_routes = {
      integration_api = { service = "integration", priority = 10, path_patterns = ["/internal/*"] }
    }
  }
  expect_failures = [var.public_routes]
}
