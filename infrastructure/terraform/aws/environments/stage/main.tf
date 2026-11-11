terraform {
  required_version = "= 1.12.2"
  required_providers {
    aws = { source = "hashicorp/aws", version = "6.15.0" }
  }
  backend "s3" {}
}

provider "aws" {
  region              = var.region
  allowed_account_ids = [var.expected_account_id]
  default_tags {
    tags = {
      Application = "nigson"
      Environment = "stage"
      ManagedBy   = "terraform"
      Policy      = var.policy_revision
    }
  }
}

data "aws_caller_identity" "current" {}
locals {
  services_with_secret_refs = { for name, service in var.services : name => merge(service, { secret_arns = lookup(module.secrets.secret_arns, name, {}) }) }
  task_role_names           = { for name, arn in module.compute.task_role_arns : name => split("/", arn)[length(split("/", arn)) - 1] }
}

check "stage_account" {
  assert {
    condition     = data.aws_caller_identity.current.account_id == var.expected_account_id
    error_message = "Stage credentials resolved to an unexpected AWS account."
  }
}

module "network" {
  source              = "../../modules/network"
  environment         = "stage"
  region              = var.region
  policy_revision     = var.policy_revision
  vpc_cidr            = var.vpc_cidr
  availability_zones  = var.availability_zones
  subnet_cidrs        = var.subnet_cidrs
  interface_endpoints = var.interface_endpoints
}

module "secrets" {
  source                      = "../../modules/secrets"
  environment                 = "stage"
  policy_revision             = var.policy_revision
  key_administrator_role_arns = var.key_administrator_role_arns
  secret_recovery_window_days = var.secret_recovery_window_days
  product_phase               = var.product_phase
  secret_names                = var.secret_names
}

module "identity" {
  source                         = "../../modules/identity"
  environment                    = "stage"
  vpc_id                         = module.network.vpc_id
  vpc_cidr                       = var.vpc_cidr
  ecs_cluster_arn                = module.compute.cluster_arn
  public_subnet_ids              = module.network.edge_subnet_ids
  private_subnet_ids             = module.network.application_subnet_ids
  database_subnet_ids            = module.network.data_subnet_ids
  admin_cidrs                    = var.identity_admin_cidrs
  trusted_proxy_cidrs            = concat([for cidrs in values(var.subnet_cidrs) : cidrs.edge], [for cidrs in values(var.subnet_cidrs) : cidrs.application])
  aws_endpoint_security_group_id = module.network.endpoint_security_group_id
  metrics_security_group_ids     = []
  public_hostname                = var.identity_public_hostname
  admin_hostname                 = var.identity_admin_hostname
  realm                          = var.identity_realm
  public_certificate_arn         = var.identity_public_certificate_arn
  admin_certificate_arn          = var.identity_admin_certificate_arn
  kms_key_arn                    = module.secrets.kms_key_arn
  identity_image                 = var.identity_image
  cpu_architecture               = var.identity_cpu_architecture
  secret_refs = {
    database_password = module.secrets.secret_arns.identity["database_password"]
    tls_certificate   = module.secrets.secret_arns.identity["tls_certificate"]
    tls_key           = module.secrets.secret_arns.identity["tls_key"]
    database_ca       = module.secrets.secret_arns.identity["database_ca"]
  }
  postgres_version        = var.database_engine_version
  database_instance_class = var.identity_database_instance_class
  backup_retention_days   = var.database_backup_retention_days
  log_retention_days      = var.log_retention_days
  bootstrap_secret_refs = {
    username = module.secrets.secret_arns.identity["bootstrap_username"]
    password = module.secrets.secret_arns.identity["bootstrap_password"]
  }
  policy_revision        = var.policy_revision
  named_operator         = var.named_operator
  alarm_destination_arns = var.alert_destination_arns
  service_enabled        = var.identity_service_enabled
}

module "compute" {
  source                     = "../../modules/compute"
  environment                = "stage"
  region                     = var.region
  product_phase              = var.product_phase
  policy_revision            = var.policy_revision
  vpc_id                     = module.network.vpc_id
  edge_subnet_ids            = module.network.edge_subnet_ids
  application_subnet_ids     = module.network.application_subnet_ids
  private_namespace          = var.private_namespace
  kms_key_arn                = module.secrets.kms_key_arn
  waf_web_acl_arn            = var.waf_web_acl_arn
  public_certificate_arn     = var.public_certificate_arn
  alb_deletion_protection    = var.alb_deletion_protection
  log_retention_days         = var.log_retention_days
  database_connection_budget = var.database_connection_budget
  cpu_target_percent         = var.cpu_target_percent
  services                   = local.services_with_secret_refs
  public_routes              = var.public_routes
}

module "database" {
  source                     = "../../modules/database"
  environment                = "stage"
  policy_revision            = var.policy_revision
  product_phase              = var.product_phase
  vpc_id                     = module.network.vpc_id
  endpoint_security_group_id = module.network.endpoint_security_group_id
  data_subnet_ids            = module.network.data_subnet_ids
  owner_task_security_groups = merge(
    { for name in ["commerce", "experience", "integration"] : name => module.compute.service_security_group_ids[name] },
    contains(keys(var.services), "ai") ? { ai = module.compute.service_security_group_ids["ai"] } : {}
  )
  kms_key_arn               = module.secrets.kms_key_arn
  engine_version            = var.database_engine_version
  instance_class            = var.database_instance_class
  allocated_storage_gib     = var.database_allocated_storage_gib
  max_allocated_storage_gib = var.database_max_allocated_storage_gib
  backup_retention_days     = var.database_backup_retention_days
  log_retention_days        = var.log_retention_days
  multi_az                  = var.database_multi_az
  deletion_protection       = var.database_deletion_protection
}

module "migrations" {
  source                      = "../../modules/migrations"
  environment                 = "stage"
  region                      = var.region
  product_phase               = var.product_phase
  migration_release           = var.migration_release
  image_digest                = var.migration_image_digest
  architecture                = var.migration_architecture
  database_address            = module.database.database_address
  migration_security_group_id = module.database.migration_task_security_group_id
  kms_key_arn                 = module.secrets.kms_key_arn
  log_retention_days          = var.log_retention_days
  commerce_repository_arn     = module.compute.repository_arns.commerce
  commerce_repository_url     = module.compute.repository_urls.commerce
  migration_password_secret_arns = merge(
    { for name in ["commerce", "experience", "integration"] : name => module.secrets.secret_arns[name]["migration_password"] },
    var.product_phase == 5 ? { ai = module.secrets.secret_arns.ai["migration_password"] } : {}
  )
}

module "storage" {
  source                      = "../../modules/storage"
  environment                 = "stage"
  region                      = var.region
  policy_revision             = var.policy_revision
  kms_key_arn                 = module.secrets.kms_key_arn
  cloudfront_distribution_arn = var.cloudfront_distribution_arn
  bucket_names                = var.bucket_names
  lifecycle_days              = var.bucket_lifecycle_days
  bucket_access               = var.bucket_access
}

module "messaging" {
  source          = "../../modules/messaging"
  environment     = "stage"
  product_phase   = var.product_phase
  kms_key_arn     = module.secrets.kms_key_arn
  publisher_roles = { for name in ["commerce", "experience"] : name => local.task_role_names[name] }
  consumer_roles = merge(
    {
      integration-work       = local.task_role_names.integration
      experience-projections = local.task_role_names.experience
      commerce-domain        = local.task_role_names.commerce
    },
    var.product_phase == 5 ? { ai-ingestion = local.task_role_names.ai } : {}
  )
}

module "observability" {
  source                 = "../../modules/observability"
  environment            = "stage"
  product_phase          = var.product_phase
  kms_key_arn            = module.secrets.kms_key_arn
  log_retention_days     = var.log_retention_days
  policy_revision        = var.policy_revision
  named_operator         = var.named_operator
  alert_destination_arns = var.alert_destination_arns
  queue_names            = module.messaging.queue_names
}

output "network" {
  value = {
    vpc_id                  = module.network.vpc_id
    edge_subnet_ids         = module.network.edge_subnet_ids
    application_subnet_ids  = module.network.application_subnet_ids
    data_subnet_ids         = module.network.data_subnet_ids
    endpoint_security_group = module.network.endpoint_security_group_id
  }
}

output "compute" {
  value = {
    cluster_arn             = module.compute.cluster_arn
    load_balancer_dns_name  = module.compute.load_balancer_dns_name
    service_security_groups = module.compute.service_security_group_ids
    task_role_arns          = module.compute.task_role_arns
    repository_urls         = module.compute.repository_urls
    private_service_names   = module.compute.private_service_names
  }
}

output "database" {
  value = {
    address                     = module.database.database_address
    port                        = module.database.database_port
    security_group_id           = module.database.database_security_group_id
    master_secret_arn           = module.database.master_secret_arn
    migration_security_group_id = module.database.migration_task_security_group_id
  }
}

output "migrations" {
  value = {
    environment          = "stage"
    region               = var.region
    task_definition_arns = module.migrations.task_definition_arns
    execution_role_arns  = module.migrations.execution_role_arns
    task_role_arns       = module.migrations.task_role_arns
    cluster_arn          = module.compute.cluster_arn
    private_subnet_ids   = module.network.application_subnet_ids
    security_group_id    = module.database.migration_task_security_group_id
  }
}

output "storage" {
  value = {
    bucket_names = module.storage.bucket_names
    bucket_arns  = module.storage.bucket_arns
  }
}

output "messaging" {
  value = {
    topic_arn = module.messaging.topic_arn
    queues    = module.messaging.queues
  }
}

output "identity" {
  value = {
    issuer                = module.identity.issuer
    private_admin_url     = module.identity.private_admin_url
    public_alb_dns_name   = module.identity.public_alb_dns_name
    private_admin_alb_dns = module.identity.private_admin_alb_dns_name
    service_name          = module.identity.identity_service_name
    bootstrap_task_arn    = module.identity.bootstrap_task_definition_arn
    ecr_repository_url    = module.identity.identity_repository_url
  }
}
