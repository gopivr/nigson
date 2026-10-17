variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "qa", "stage", "prod"], var.environment)
    error_message = "Explicit database environment required."
  }
}
variable "policy_revision" {
  type = string
  validation {
    condition     = length(trimspace(var.policy_revision)) > 0
    error_message = "Approved database/security policy revision required."
  }
}
variable "product_phase" {
  type = number
  validation {
    condition     = contains([1, 2, 3, 4, 5], var.product_phase)
    error_message = "Explicit product phase required."
  }
}
variable "vpc_id" { type = string }
variable "endpoint_security_group_id" {
  type = string
  validation {
    condition     = can(regex("^sg-[A-Za-z0-9-]+$", var.endpoint_security_group_id))
    error_message = "Private AWS endpoint security group required for migrations."
  }
}
variable "data_subnet_ids" {
  type = list(string)
  validation {
    condition     = length(var.data_subnet_ids) >= 2
    error_message = "Database requires isolated data subnets in at least two AZs."
  }
}
variable "owner_task_security_groups" {
  type = map(string)
  validation {
    condition     = length(var.owner_task_security_groups) >= 3 && alltrue([for name, id in var.owner_task_security_groups : (contains(["commerce", "experience", "integration"], name) || (name == "ai" && var.product_phase == 5)) && can(regex("^sg-[A-Za-z0-9-]+$", id))])
    error_message = "Only explicit owned task groups may connect; AI is permitted only at BP5."
  }
}
variable "kms_key_arn" {
  type = string
  validation {
    condition     = can(regex("^arn:aws[a-z-]*:kms:[a-z0-9-]+:[0-9]{12}:key/[A-Za-z0-9-]+$", var.kms_key_arn))
    error_message = "Approved KMS key ARN required for database, logs and managed master credentials."
  }
}
variable "engine_version" {
  type = string
  validation {
    condition     = can(regex("^18\\.[0-9]+$", var.engine_version))
    error_message = "Explicit supported PostgreSQL 18 minor version required."
  }
}
variable "instance_class" {
  type = string
  validation {
    condition     = can(regex("^db\\.[a-z0-9-]+\\.[a-z0-9]+$", var.instance_class))
    error_message = "Region and load qualified RDS instance class required."
  }
}
variable "allocated_storage_gib" {
  type = number
  validation {
    condition     = var.allocated_storage_gib >= 20 && var.allocated_storage_gib <= 65536
    error_message = "Explicit initial allocated storage from 20 GiB through 64 TiB required."
  }
}
variable "max_allocated_storage_gib" {
  type = number
  validation {
    condition     = var.max_allocated_storage_gib >= var.allocated_storage_gib && var.max_allocated_storage_gib <= 65536
    error_message = "Storage autoscaling ceiling must cover initial storage and remain within RDS limits."
  }
}
variable "backup_retention_days" {
  type = number
  validation {
    condition     = var.backup_retention_days >= 1 && var.backup_retention_days <= 35
    error_message = "Approved point-in-time backup retention from 1 to 35 days required."
  }
}
variable "log_retention_days" {
  type = number
  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653], var.log_retention_days)
    error_message = "Approved database log retention required."
  }
}
variable "multi_az" { type = bool }
variable "deletion_protection" { type = bool }
