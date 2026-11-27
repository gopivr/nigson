terraform {
  required_version = "= 1.12.2"
  required_providers {
    aws = {
      source = "hashicorp/aws", version = "6.15.0"
    }
  }
  backend "s3" {
  }
}
provider "aws" {
  region              = var.region
  allowed_account_ids = [var.expected_account_id]
  default_tags {
    tags = {
      Application = "nigson", Environment = var.environment, ManagedBy = "terraform", Policy = var.policy_revision
    }
  }
}
provider "aws" {
  alias               = "global"
  region              = "us-east-1"
  allowed_account_ids = [var.expected_account_id]
  default_tags {
    tags = {
      Application = "nigson", Environment = var.environment, ManagedBy = "terraform"
    }
  }
}
data "aws_caller_identity" "current" {
}
data "aws_iam_session_context" "operator" {
  count = startswith(data.aws_caller_identity.current.arn, "arn:aws:sts:") ? 1 : 0
  arn   = data.aws_caller_identity.current.arn
}
data "aws_availability_zones" "available" {
  state = "available"
  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]

  }
}
data "aws_rds_engine_version" "postgres" {
  engine                 = "postgres"
  parameter_group_family = "postgres18"
  latest                 = true
}
locals {
  names    = concat(["commerce", "experience", "integration"], var.product_phase == 5 ? ["ai"] : [])
  azs      = slice(sort(data.aws_availability_zones.available.names), 0, 2)
  prefix   = "nigson-${var.environment}"
  registry = "${var.expected_account_id}.dkr.ecr.${var.region}.amazonaws.com"
  operator = try(data.aws_iam_session_context.operator[0].issuer_arn, data.aws_caller_identity.current.arn)
  secret_names = merge({
    for service in local.names : service => {
      for name in setunion(toset(["migration_password", "SPRING_DATASOURCE_PASSWORD"]), lookup(var.application_secret_names, service, toset([]))) : name => "nigson/${var.environment}/${service}/${name}"
    }
    }, {
    identity = {
      for name in ["database_password", "tls_certificate", "tls_key", "database_ca", "bootstrap_username", "bootstrap_password"] : name => "nigson/${var.environment}/identity/${name}"
    }

  })
  services = {
    for name in local.names : name => {
      enabled                 = var.activate_applications
      public                  = contains(["commerce", "experience"], name)
      port                    = name == "commerce" ? 8081 : name == "experience" ? 8082 : name == "integration" ? 8083 : 8084
      cpu                     = 1024
      memory                  = 2048
      architecture            = "X86_64"
      min_count               = var.environment == "prod" && contains(["commerce", "experience"], name) ? 2 : 1
      desired_count           = var.environment == "prod" && contains(["commerce", "experience"], name) ? 2 : 1
      max_count               = contains(["commerce", "experience"], name) ? 4 : 2
      db_connections_per_task = 10
      image_digest            = lookup(var.image_digests, name, null)
      health_path             = name == "ai" ? "/health" : "/actuator/health/readiness"
      probe_command           = name == "ai" ? ["CMD", "python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8084/health')"] : ["CMD", "java", "-cp", "/app/probe", "HealthProbe"]
      environment = merge({
        APP_ENV = var.environment, NIGSON_PRODUCT_PHASE = tostring(var.product_phase)
        }, lookup(var.application_environment, name, {
      }))
      secret_arns = {
        for alias, arn in module.secrets.secret_arns[name] : alias => arn if alias != "migration_password"
      }
      egress = merge({
        aws_endpoints = {
          description = "Private AWS endpoints", cidr_ipv4 = var.vpc_cidr, protocol = "tcp", port = 443
        }
        }, lookup(var.provider_egress, name, {
      }))

    }
  }
}
resource "aws_iam_role" "key_administrator" {
  name = "${local.prefix}-key-administrator"
  assume_role_policy = jsonencode({
    Version = "2012-10-17", Statement = [{
      Effect = "Allow", Principal = {
        AWS = "arn:aws:iam::${var.expected_account_id}:root"
        }, Action = "sts:AssumeRole", Condition = {
        ArnEquals = {
          "aws:PrincipalArn" = local.operator
        }
      }
    }]
  })
}
module "network" {
  source             = "../modules/network"
  environment        = var.environment
  region             = var.region
  policy_revision    = var.policy_revision
  vpc_cidr           = var.vpc_cidr
  availability_zones = local.azs
  subnet_cidrs = {
    for index, az in local.azs : az => {
      edge = cidrsubnet(var.vpc_cidr, 8, index), application = cidrsubnet(var.vpc_cidr, 8, 16 + index), data = cidrsubnet(var.vpc_cidr, 8, 32 + index)
    }
  }
}
module "secrets" {
  source                      = "../modules/secrets"
  environment                 = var.environment
  policy_revision             = var.policy_revision
  key_administrator_role_arns = [aws_iam_role.key_administrator.arn]
  secret_recovery_window_days = 30
  product_phase               = var.product_phase
  secret_names                = local.secret_names
}
resource "aws_acm_certificate" "regional" {
  domain_name               = "api.${var.zone_name}"
  subject_alternative_names = ["auth.${var.zone_name}", "identity-admin.${var.zone_name}"]
  validation_method         = "DNS"
  lifecycle {
    create_before_destroy = true
  }
}
resource "aws_acm_certificate" "frontend" {
  provider          = aws.global
  domain_name       = var.zone_name
  validation_method = "DNS"
  lifecycle {
    create_before_destroy = true
  }
}
resource "aws_route53_record" "regional_validation" {
  for_each = toset(["api.${var.zone_name}", "auth.${var.zone_name}", "identity-admin.${var.zone_name}"])
  zone_id  = var.zone_id
  name     = one([for option in aws_acm_certificate.regional.domain_validation_options : option if option.domain_name == each.key]).resource_record_name
  type     = one([for option in aws_acm_certificate.regional.domain_validation_options : option if option.domain_name == each.key]).resource_record_type
  records  = [one([for option in aws_acm_certificate.regional.domain_validation_options : option if option.domain_name == each.key]).resource_record_value]
  ttl      = 60
}
resource "aws_route53_record" "frontend_validation" {
  zone_id = var.zone_id
  name    = one(aws_acm_certificate.frontend.domain_validation_options).resource_record_name
  type    = one(aws_acm_certificate.frontend.domain_validation_options).resource_record_type
  records = [one(aws_acm_certificate.frontend.domain_validation_options).resource_record_value]
  ttl     = 60
}
resource "aws_acm_certificate_validation" "regional" {
  certificate_arn         = aws_acm_certificate.regional.arn
  validation_record_fqdns = [for record in aws_route53_record.regional_validation : record.fqdn]
}
resource "aws_acm_certificate_validation" "frontend" {
  provider                = aws.global
  certificate_arn         = aws_acm_certificate.frontend.arn
  validation_record_fqdns = [aws_route53_record.frontend_validation.fqdn]
}
resource "aws_wafv2_web_acl" "regional" {
  name  = "${local.prefix}-public"
  scope = "REGIONAL"
  default_action {
    allow {
    }
  }
  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${local.prefix}-public"
    sampled_requests_enabled   = false

  }
  rule {
    name     = "AWSCommonRules"
    priority = 1
    override_action {
      none {
      }
    }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"

      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.prefix}-common"
      sampled_requests_enabled   = false

    }

  }
}
resource "aws_wafv2_web_acl" "global" {
  provider = aws.global
  name     = "${local.prefix}-frontend"
  scope    = "CLOUDFRONT"
  default_action {
    allow {
    }
  }
  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${local.prefix}-frontend"
    sampled_requests_enabled   = false

  }
  rule {
    name     = "AWSCommonRules"
    priority = 1
    override_action {
      none {
      }
    }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"

      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.prefix}-frontend-common"
      sampled_requests_enabled   = false

    }

  }
}
module "compute" {
  source                     = "../modules/compute"
  environment                = var.environment
  region                     = var.region
  product_phase              = var.product_phase
  policy_revision            = var.policy_revision
  vpc_id                     = module.network.vpc_id
  edge_subnet_ids            = module.network.edge_subnet_ids
  application_subnet_ids     = module.network.application_subnet_ids
  private_namespace          = "svc.${var.environment}.nigson.internal"
  kms_key_arn                = module.secrets.kms_key_arn
  waf_web_acl_arn            = aws_wafv2_web_acl.regional.arn
  public_certificate_arn     = aws_acm_certificate_validation.regional.certificate_arn
  alb_deletion_protection    = var.environment == "prod"
  log_retention_days         = var.log_retention_days
  database_connection_budget = 160
  cpu_target_percent         = 65
  services                   = local.services
  provision_tasks            = var.publish_task_definitions
  public_routes = { for name, route in {
    commerce     = { service = "commerce", priority = 100, path_patterns = ["/bff/*", "/api/v1/*", "/wh/*"] }
    experience   = { service = "experience", priority = 20, path_patterns = ["/api/v1/content", "/api/v1/content/*", "/api/v1/enquiries", "/api/v1/newsletter-subscriptions", "/api/v1/unsubscribe", "/api/v1/career-opportunities", "/api/v1/career-opportunities/*", "/api/v1/career-upload-sessions", "/api/v1/career-upload-sessions/*", "/api/v1/channels", "/api/v1/channels/*", "/api/v1/campaigns", "/api/v1/campaigns/*", "/api/v1/seo", "/api/v1/seo/*"] }
    review_reads = { service = "experience", priority = 60, path_patterns = ["/api/v1/reviews", "/api/v1/reviews/*"], methods = ["GET", "HEAD"] }
  } : name => route if var.activate_applications }
}
module "database" {
  source                     = "../modules/database"
  environment                = var.environment
  policy_revision            = var.policy_revision
  product_phase              = var.product_phase
  vpc_id                     = module.network.vpc_id
  endpoint_security_group_id = module.network.endpoint_security_group_id
  data_subnet_ids            = module.network.data_subnet_ids
  owner_task_security_groups = module.compute.service_security_group_ids
  kms_key_arn                = module.secrets.kms_key_arn
  engine_version             = data.aws_rds_engine_version.postgres.version
  instance_class             = var.database_instance_class
  allocated_storage_gib      = 20
  max_allocated_storage_gib  = 100
  backup_retention_days      = 7
  log_retention_days         = var.log_retention_days
  multi_az                   = var.environment == "prod"
  deletion_protection        = var.environment == "prod"
}
resource "aws_sns_topic" "alerts" {
  name              = "${local.prefix}-operator-alerts"
  kms_master_key_id = aws_kms_key.alerts.arn
}
resource "aws_sns_topic_subscription" "email" {
  count     = var.alert_email == null ? 0 : 1
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}
module "identity" {
  source                         = "../modules/identity"
  environment                    = var.environment
  vpc_id                         = module.network.vpc_id
  vpc_cidr                       = var.vpc_cidr
  ecs_cluster_arn                = module.compute.cluster_arn
  public_subnet_ids              = module.network.edge_subnet_ids
  private_subnet_ids             = module.network.application_subnet_ids
  database_subnet_ids            = module.network.data_subnet_ids
  admin_cidrs                    = [var.vpc_cidr]
  trusted_proxy_cidrs            = [for index, az in local.azs : cidrsubnet(var.vpc_cidr, 8, index)]
  aws_endpoint_security_group_id = module.network.endpoint_security_group_id
  public_hostname                = "auth.${var.zone_name}"
  admin_hostname                 = "identity-admin.${var.zone_name}"
  realm                          = "nigson-${var.environment}"
  public_certificate_arn         = aws_acm_certificate_validation.regional.certificate_arn
  admin_certificate_arn          = aws_acm_certificate_validation.regional.certificate_arn
  kms_key_arn                    = module.secrets.kms_key_arn
  identity_image                 = var.publish_task_definitions ? "${local.registry}/nigson/${var.environment}/identity@${lookup(var.image_digests, "identity", "")}" : null
  provision_tasks                = var.publish_task_definitions
  cpu_architecture               = "X86_64"
  secret_refs = {
    for name in ["database_password", "tls_certificate", "tls_key", "database_ca"] : name => module.secrets.secret_arns.identity[name]
  }
  postgres_version        = data.aws_rds_engine_version.postgres.version
  database_instance_class = var.database_instance_class
  backup_retention_days   = 7
  log_retention_days      = var.log_retention_days
  bootstrap_secret_refs = {
    username = module.secrets.secret_arns.identity.bootstrap_username, password = module.secrets.secret_arns.identity.bootstrap_password
  }
  policy_revision        = var.policy_revision
  named_operator         = "first-account-operator"
  alarm_destination_arns = [aws_sns_topic.alerts.arn]
  service_enabled        = var.activate_identity
}
module "frontend" {
  source                  = "../modules/frontend"
  environment             = var.environment
  bucket_name             = "nigson-${var.environment}-${var.expected_account_id}-frontend"
  domain_name             = var.zone_name
  certificate_arn         = aws_acm_certificate_validation.frontend.certificate_arn
  backend_origin_hostname = "api.${var.zone_name}"
  release_id              = var.frontend_release_id
  zone_id                 = var.zone_id
  web_acl_arn             = aws_wafv2_web_acl.global.arn
}
resource "aws_route53_record" "api" {
  zone_id = var.zone_id
  name    = "api.${var.zone_name}"
  type    = "A"
  alias {
    name                   = module.compute.load_balancer_dns_name
    zone_id                = module.compute.load_balancer_zone_id
    evaluate_target_health = false

  }
}
resource "aws_route53_record" "identity" {
  zone_id = var.zone_id
  name    = "auth.${var.zone_name}"
  type    = "A"
  alias {
    name                   = module.identity.public_alb_dns_name
    zone_id                = module.identity.public_alb_zone_id
    evaluate_target_health = false

  }
}
module "storage" {
  source                      = "../modules/storage"
  environment                 = var.environment
  region                      = var.region
  policy_revision             = var.policy_revision
  kms_key_arn                 = module.secrets.kms_key_arn
  cloudfront_distribution_arn = module.frontend.distribution_arn
  enable_object_expiration    = false
  bucket_names = {
    for purpose in ["public_media", "quarantine", "private_assets", "carrier_labels"] : purpose => "nigson-${var.environment}-${var.expected_account_id}-${replace(purpose, "_", "-")}"
  }
  lifecycle_days = {
    for purpose in ["public_media", "quarantine", "private_assets", "carrier_labels"] : purpose => {
      current_version_days = 365, noncurrent_version_days = 30, incomplete_upload_days = 7
    }
  }
  bucket_access = {
    for purpose in ["public_media", "quarantine", "private_assets", "carrier_labels"] : purpose => {
      readers = {
        }, writers = {
      }
    }
  }
}
module "messaging" {
  source        = "../modules/messaging"
  environment   = var.environment
  product_phase = var.product_phase
  kms_key_arn   = module.secrets.kms_key_arn
  publisher_roles = {
    for name in ["commerce", "experience"] : name => split("/", module.compute.task_role_arns[name])[1]
  }
  consumer_roles = merge({
    integration-work = split("/", module.compute.task_role_arns.integration)[1], experience-projections = split("/", module.compute.task_role_arns.experience)[1], commerce-domain = split("/", module.compute.task_role_arns.commerce)[1]
    }, var.product_phase == 5 ? {
    ai-ingestion = split("/", module.compute.task_role_arns.ai)[1]
    } : {
  })
}
module "migrations" {
  count                       = var.publish_task_definitions ? 1 : 0
  source                      = "../modules/migrations"
  environment                 = var.environment
  region                      = var.region
  product_phase               = var.product_phase
  migration_release           = "BP${var.product_phase}"
  image_digest                = var.migration_image_digest
  architecture                = "X86_64"
  database_address            = module.database.database_address
  migration_security_group_id = module.database.migration_task_security_group_id
  kms_key_arn                 = module.secrets.kms_key_arn
  log_retention_days          = var.log_retention_days
  commerce_repository_arn     = module.compute.repository_arns.commerce
  commerce_repository_url     = module.compute.repository_urls.commerce
  migration_password_secret_arns = {
    for name in local.names : name => module.secrets.secret_arns[name].migration_password
  }
}
output "infrastructure" {
  value = {
    account_id         = var.expected_account_id
    region             = var.region
    frontend_url       = module.frontend.url
    frontend_bucket    = module.frontend.bucket_name
    frontend_release   = var.frontend_release_id
    api_url            = "https://api.${var.zone_name}"
    identity_issuer    = module.identity.issuer
    vpc_id             = module.network.vpc_id
    private_subnet_ids = module.network.application_subnet_ids
    cluster_arn        = module.compute.cluster_arn
    repository_urls = merge(module.compute.repository_urls, {
      identity = module.identity.identity_repository_url
    })
    database_address           = module.database.database_address
    database_master_secret_arn = module.database.master_secret_arn
    secret_arns                = module.secrets.secret_arns
    migration_task_definitions = try(module.migrations[0].task_definition_arns, {
    })
    applications_activated = var.activate_applications
    identity_activated     = var.activate_identity

  }
}

# Owner tasks reach the database by security-group reference, never a public CIDR.
resource "aws_vpc_security_group_egress_rule" "owner_database" {
  for_each                     = toset(local.names)
  security_group_id            = module.compute.service_security_group_ids[each.key]
  referenced_security_group_id = module.database.database_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}
resource "aws_vpc_security_group_egress_rule" "owner_dns" {
  for_each          = { for entry in setproduct(local.names, ["tcp", "udp"]) : "${entry[0]}-${entry[1]}" => { service = entry[0], protocol = entry[1] } }
  security_group_id = module.compute.service_security_group_ids[each.value.service]
  cidr_ipv4         = var.vpc_cidr
  ip_protocol       = each.value.protocol
  from_port         = 53
  to_port           = 53
}
locals {
  private_routes = merge({
    commerce-integration = { source = "commerce", target = "integration", port = 8083 }
    commerce-experience  = { source = "commerce", target = "experience", port = 8082 }
    experience-commerce  = { source = "experience", target = "commerce", port = 8081 }
    integration-commerce = { source = "integration", target = "commerce", port = 8081 }
    }, var.product_phase == 5 ? {
    commerce-ai   = { source = "commerce", target = "ai", port = 8084 }
    ai-commerce   = { source = "ai", target = "commerce", port = 8081 }
    ai-experience = { source = "ai", target = "experience", port = 8082 }
  } : {})
}
resource "aws_vpc_security_group_egress_rule" "private_workload" {
  for_each                     = local.private_routes
  security_group_id            = module.compute.service_security_group_ids[each.value.source]
  referenced_security_group_id = module.compute.service_security_group_ids[each.value.target]
  ip_protocol                  = "tcp"
  from_port                    = each.value.port
  to_port                      = each.value.port
}
resource "aws_vpc_security_group_ingress_rule" "private_workload" {
  for_each                     = local.private_routes
  security_group_id            = module.compute.service_security_group_ids[each.value.target]
  referenced_security_group_id = module.compute.service_security_group_ids[each.value.source]
  ip_protocol                  = "tcp"
  from_port                    = each.value.port
  to_port                      = each.value.port
}
resource "aws_route53_zone" "identity_admin" {
  name = "identity-admin.${var.zone_name}"
  vpc { vpc_id = module.network.vpc_id }
}
resource "aws_route53_record" "identity_admin" {
  zone_id = aws_route53_zone.identity_admin.zone_id
  name    = "identity-admin.${var.zone_name}"
  type    = "A"
  alias {
    name                   = module.identity.private_admin_alb_dns_name
    zone_id                = module.identity.private_admin_alb_zone_id
    evaluate_target_health = false
  }
}
module "observability" {
  source                 = "../modules/observability"
  environment            = var.environment
  product_phase          = var.product_phase
  kms_key_arn            = module.secrets.kms_key_arn
  log_retention_days     = var.log_retention_days
  policy_revision        = var.policy_revision
  application_log_groups = module.compute.log_groups
  named_operator         = "first-account-operator"
  alert_destination_arns = [aws_sns_topic.alerts.arn]
  queue_names            = module.messaging.queue_names
}

output "migrations" {
  value = {
    environment          = var.environment
    region               = var.region
    task_definition_arns = try(module.migrations[0].task_definition_arns, {})
    cluster_arn          = module.compute.cluster_arn
    private_subnet_ids   = module.network.application_subnet_ids
    security_group_id    = module.database.migration_task_security_group_id
  }
}

resource "aws_kms_key" "alerts" {
  description         = "Nigson ${var.environment} operator alert encryption"
  enable_key_rotation = true
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Sid = "AccountAdministration", Effect = "Allow", Principal = { AWS = "arn:aws:iam::${var.expected_account_id}:root" }, Action = "kms:*", Resource = "*" },
    { Sid = "AlarmDelivery", Effect = "Allow", Principal = { Service = ["sns.amazonaws.com", "cloudwatch.amazonaws.com"] }, Action = ["kms:Decrypt", "kms:GenerateDataKey*"], Resource = "*", Condition = { StringEquals = { "aws:SourceAccount" = var.expected_account_id, "kms:EncryptionContext:aws:sns:topicArn" = "arn:aws:sns:${var.region}:${var.expected_account_id}:${local.prefix}-operator-alerts" } } }
  ] })
  lifecycle { prevent_destroy = true }
}
resource "aws_sns_topic_policy" "alerts" {
  arn = aws_sns_topic.alerts.arn
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Sid = "AccountAdministration", Effect = "Allow", Principal = { AWS = "arn:aws:iam::${var.expected_account_id}:root" }, Action = ["sns:GetTopicAttributes", "sns:SetTopicAttributes", "sns:AddPermission", "sns:RemovePermission", "sns:DeleteTopic", "sns:Subscribe", "sns:ListSubscriptionsByTopic", "sns:Publish"], Resource = aws_sns_topic.alerts.arn },
    { Sid = "EnvironmentAlarms", Effect = "Allow", Principal = { Service = "cloudwatch.amazonaws.com" }, Action = "sns:Publish", Resource = aws_sns_topic.alerts.arn, Condition = { StringEquals = { "aws:SourceAccount" = var.expected_account_id }, ArnLike = { "aws:SourceArn" = "arn:aws:cloudwatch:${var.region}:${var.expected_account_id}:alarm:${local.prefix}-*" } } }
  ] })
}

# Preserve any group created by Observability before ownership was consolidated.
moved {
  from = module.observability.aws_cloudwatch_log_group.owner["commerce"]
  to   = module.compute.aws_cloudwatch_log_group.service["commerce"]
}

# Preserve any group created by Observability before ownership was consolidated.
moved {
  from = module.observability.aws_cloudwatch_log_group.owner["experience"]
  to   = module.compute.aws_cloudwatch_log_group.service["experience"]
}

# Preserve any group created by Observability before ownership was consolidated.
moved {
  from = module.observability.aws_cloudwatch_log_group.owner["integration"]
  to   = module.compute.aws_cloudwatch_log_group.service["integration"]
}

# Preserve any group created by Observability before ownership was consolidated.
moved {
  from = module.observability.aws_cloudwatch_log_group.owner["ai"]
  to   = module.compute.aws_cloudwatch_log_group.service["ai"]
}

output "deployment_configuration" {
  description = "Applied inputs for guarded frontend-only releases; never publish this state output as an artifact."
  sensitive   = true
  value = {
    schema_version = 1
    variables = {
      expected_account_id      = var.expected_account_id
      region                   = var.region
      environment              = var.environment
      domain_name              = var.domain_name
      zone_name                = var.zone_name
      zone_id                  = var.zone_id
      vpc_cidr                 = var.vpc_cidr
      product_phase            = var.product_phase
      policy_revision          = var.policy_revision
      frontend_release_id      = var.frontend_release_id
      log_retention_days       = var.log_retention_days
      database_instance_class  = var.database_instance_class
      publish_task_definitions = var.publish_task_definitions
      activate_applications    = var.activate_applications
      activate_identity        = var.activate_identity
      image_digests            = var.image_digests
      application_environment  = var.application_environment
      application_secret_names = var.application_secret_names
      provider_egress          = var.provider_egress
      alert_email              = var.alert_email
      migration_image_digest   = var.migration_image_digest
    }
  }
}
