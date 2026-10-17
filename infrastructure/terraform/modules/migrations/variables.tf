variable "environment" {
  type = string
  validation {
    condition     = contains(["stage", "prod"], var.environment)
    error_message = "Production-style migration tasks may run only in protected stage or production environments."
  }
}
variable "region" { type = string }
variable "product_phase" {
  type = number
  validation {
    condition     = contains([1, 2, 3, 4, 5], var.product_phase)
    error_message = "Explicit product phase required."
  }
}
variable "migration_release" {
  type = string
  validation {
    condition     = can(regex("^BP[1-5]$", var.migration_release)) && tonumber(substr(var.migration_release, 2, 1)) <= var.product_phase
    error_message = "Migration release must be an explicitly enabled BP no later than the environment's product phase."
  }
}
variable "image_digest" {
  type = string
  validation {
    condition     = can(regex("^sha256:[a-f0-9]{64}$", var.image_digest))
    error_message = "Immutable commerce migration image digest required."
  }
}
variable "architecture" {
  type = string
  validation {
    condition     = contains(["ARM64", "X86_64"], var.architecture)
    error_message = "Explicit migration image architecture required."
  }
}
variable "database_address" {
  type = string
  validation {
    condition     = can(regex("^(?:[a-z0-9-]+\\.)+[a-z0-9-]+\\.rds\\.amazonaws\\.com$", var.database_address))
    error_message = "Private RDS endpoint required for owner migrations."
  }
}
variable "migration_security_group_id" {
  type = string
  validation {
    condition     = can(regex("^sg-[A-Za-z0-9-]+$", var.migration_security_group_id))
    error_message = "Private one-shot migration task security group required."
  }
}
variable "kms_key_arn" { type = string }
variable "log_retention_days" {
  type = number
  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653], var.log_retention_days)
    error_message = "Explicit approved migration-log retention is required."
  }
}
variable "commerce_repository_arn" { type = string }
variable "commerce_repository_url" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/nigson/(stage|prod)/commerce$", var.commerce_repository_url))
    error_message = "Explicit stage/prod commerce ECR repository URL required."
  }
}
variable "migration_password_secret_arns" {
  type = map(string)
  validation {
    condition = toset(keys(var.migration_password_secret_arns)) == (var.product_phase == 5
      ? toset(["commerce", "experience", "integration", "ai"])
      : toset(["commerce", "experience", "integration"])) && alltrue([
        for arn in values(var.migration_password_secret_arns) : can(regex("^arn:aws[a-z-]*:secretsmanager:[a-z0-9-]+:[0-9]{12}:secret:[A-Za-z0-9/_+=.@-]+$", arn))
    ])
    error_message = "One migration credential secret per active owner is required; AI is BP5-only."
  }
}
