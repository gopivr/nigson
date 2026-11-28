variable "expected_account_id" {

  type = string
  validation {

    condition     = can(regex("^[0-9]{12}$", var.expected_account_id))
    error_message = "Supply your actual AWS account ID. Credentials must use AWS_PROFILE, not Terraform variables."

  }
}
variable "region" {
  type    = string
  default = "eu-west-1"
}
variable "environment" {
  type    = string
  default = "stage"
  validation {

    condition     = contains(["stage", "prod"], var.environment)
    error_message = "Choose stage or prod."

  }
}
variable "domain_name" {
  type    = string
  default = "nigson.com"
}
variable "zone_name" {
  type = string
}
variable "zone_id" {
  type = string
}
variable "vpc_cidr" {
  type    = string
  default = "10.40.0.0/16"
  validation {
    condition     = can(cidrnetmask(var.vpc_cidr)) && try(split("/", var.vpc_cidr)[1] == "16", false)
    error_message = "This starter subdivides an RFC1918 /16; use a /16 or customize the subnet design."
  }
}
variable "product_phase" {
  type    = number
  default = 1
}
variable "policy_revision" {
  type    = string
  default = "first-account-v1"
}
variable "frontend_release_id" {
  type    = string
  default = "initial"
}
variable "log_retention_days" {
  type    = number
  default = 30
}
variable "database_instance_class" {
  type    = string
  default = "db.t4g.medium"
}
variable "publish_task_definitions" {

  type        = bool
  default     = false
  description = "Set only after publishing real image digests; infrastructure bootstrap needs no images."
}
variable "activate_applications" {

  type    = bool
  default = false
  validation {

    condition     = !var.activate_applications || var.publish_task_definitions
    error_message = "Publish real task definitions before activating applications."

  }
}
variable "activate_identity" {

  type    = bool
  default = false
  validation {

    condition     = !var.activate_identity || var.publish_task_definitions
    error_message = "Publish identity task definitions and configure DB/TLS/realm secrets first."

  }
}
variable "image_digests" {

  type = map(string)
  default = {

  }
  validation {

    condition     = !var.publish_task_definitions || alltrue([for service in concat(["commerce", "experience", "integration", "identity"], var.product_phase == 5 ? ["ai"] : []) : can(regex("^sha256:[a-f0-9]{64}$", lookup(var.image_digests, service, "")))])
    error_message = "Real immutable application and identity image digests are required before publishing tasks."

  }
}
variable "application_environment" {

  type = map(map(string))
  default = {

  }
  validation {

    condition     = !var.activate_applications || alltrue([for service in concat(["commerce", "experience", "integration"], var.product_phase == 5 ? ["ai"] : []) : try(var.application_environment[service].NIGSON_SECURITY_ENABLED == "true" && var.application_environment[service].APP_ENV == var.environment, false)])
    error_message = "Explicit secure per-service runtime configuration is required before activation."

  }
}
variable "application_secret_names" {

  type = map(set(string))
  default = {

  }
  description = "Additional runtime environment variable names stored in Secrets Manager; secret values never enter Terraform."
}
variable "provider_egress" {

  type = map(map(object({
    description = string, cidr_ipv4 = string, protocol = string, port = number
  })))
  default = {

  }
  description = "Provider destinations must be explicitly configured for activation; no public wildcard egress."
}
variable "alert_email" {

  type        = string
  default     = null
  description = "Optional operator email; SNS sends its normal subscription confirmation."
}

variable "migration_image_digest" {
  type        = string
  default     = null
  description = "The separately published commerce migration-target image, not the commerce application image."
  validation {
    condition     = !var.publish_task_definitions || can(regex("^sha256:[a-f0-9]{64}$", var.migration_image_digest))
    error_message = "Publish the separate migration image before provisioning migration task definitions."
  }
}

variable "cpu_architecture" {
  type    = string
  default = "X86_64"
  validation {
    condition     = contains(["X86_64", "ARM64"], var.cpu_architecture)
    error_message = "Use the architecture of the selected immutable release."
  }
}

variable "key_administrator_principal_arn" {
  type        = string
  default     = null
  description = "Preserve the foundation provisioner's principal when runtime setup uses its separate OIDC role."
}
