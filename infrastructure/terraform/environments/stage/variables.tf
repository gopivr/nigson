variable "region" { type = string }
variable "expected_account_id" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.expected_account_id))
    error_message = "Stage AWS account ID must be supplied explicitly."
  }
}
variable "policy_revision" {
  type = string
  validation {
    condition     = length(trimspace(var.policy_revision)) > 0
    error_message = "Approved network policy revision required."
  }
}
variable "vpc_cidr" { type = string }
variable "availability_zones" { type = list(string) }
variable "subnet_cidrs" {
  type = map(object({ edge = string, application = string, data = string }))
}
variable "interface_endpoints" {
  type    = set(string)
  default = ["ecr.api", "ecr.dkr", "kms", "logs", "secretsmanager", "sns", "sqs", "ssm"]
}
variable "product_phase" {
  type = number
  validation {
    condition     = contains([1, 2, 3, 4, 5], var.product_phase)
    error_message = "Explicit product phase required."
  }
}
variable "private_namespace" { type = string }
variable "key_administrator_role_arns" { type = set(string) }
variable "secret_recovery_window_days" { type = number }
variable "secret_names" {
  type = map(map(string))
  validation {
    condition     = contains(keys(var.secret_names), "identity") && toset(["database_password", "tls_certificate", "tls_key", "database_ca", "bootstrap_username", "bootstrap_password"]) == toset(keys(var.secret_names.identity))
    error_message = "Explicit Keycloak DB, TLS, CA and isolated bootstrap secret names are required; values are provisioned outside Terraform."
  }
}
variable "waf_web_acl_arn" { type = string }
variable "public_certificate_arn" { type = string }
variable "alb_deletion_protection" { type = bool }
variable "log_retention_days" { type = number }
variable "database_connection_budget" { type = number }
variable "cpu_target_percent" { type = number }
variable "services" {
  type = map(object({
    enabled                 = bool
    public                  = bool
    port                    = number
    cpu                     = number
    memory                  = number
    architecture            = string
    min_count               = number
    desired_count           = number
    max_count               = number
    db_connections_per_task = number
    image_digest            = string
    health_path             = string
    probe_command           = list(string)
    environment             = map(string)
    egress = map(object({
      description = string
      cidr_ipv4   = string
      protocol    = string
      port        = number
    }))
  }))
}
variable "public_routes" {
  type = map(object({ service = string, priority = number, path_patterns = list(string) }))
}
variable "database_engine_version" { type = string }
variable "database_instance_class" { type = string }
variable "database_allocated_storage_gib" { type = number }
variable "database_max_allocated_storage_gib" { type = number }
variable "database_backup_retention_days" { type = number }
variable "database_multi_az" { type = bool }
variable "database_deletion_protection" { type = bool }
variable "cloudfront_distribution_arn" { type = string }
variable "bucket_names" { type = map(string) }
variable "bucket_lifecycle_days" {
  type = map(object({ current_version_days = number, noncurrent_version_days = number, incomplete_upload_days = number }))
}
variable "bucket_access" {
  type = map(object({
    readers = map(object({ role_arn = string, prefixes = set(string) }))
    writers = map(object({ role_arn = string, prefixes = set(string) }))
  }))
}
variable "named_operator" { type = string }
variable "alert_destination_arns" { type = list(string) }
variable "identity_admin_cidrs" { type = list(string) }
variable "identity_public_hostname" { type = string }
variable "identity_admin_hostname" { type = string }
variable "identity_realm" { type = string }
variable "identity_public_certificate_arn" { type = string }
variable "identity_admin_certificate_arn" { type = string }
variable "identity_image" { type = string }
variable "identity_database_instance_class" { type = string }
variable "identity_service_enabled" { type = bool }
