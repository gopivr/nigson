terraform {
  required_version = ">= 1.12.2, < 2.0.0"
  required_providers { aws = { source = "hashicorp/aws", version = "6.15.0" } }
}
variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "qa", "stage", "prod"], var.environment)
    error_message = "Explicit deployment environment required."
  }
}
variable "vpc_id" { type = string }
variable "vpc_cidr" { type = string }
variable "ecs_cluster_arn" { type = string }
variable "public_subnet_ids" {
  type = list(string)
  validation {
    condition     = length(var.public_subnet_ids) >= 2
    error_message = "At least two public availability-zone subnets required."
  }
}
variable "private_subnet_ids" {
  type = list(string)
  validation {
    condition     = length(var.private_subnet_ids) >= 2
    error_message = "At least two private availability-zone subnets required."
  }
}
variable "database_subnet_ids" {
  type = list(string)
  validation {
    condition     = length(var.database_subnet_ids) >= 2
    error_message = "Dedicated identity database requires isolated data subnets in at least two AZs."
  }
}
variable "admin_cidrs" {
  type = list(string)
  validation {
    condition     = length(var.admin_cidrs) > 0 && alltrue([for cidr in var.admin_cidrs : can(cidrnetmask(cidr)) && !contains(["0.0.0.0/0", "::/0"], cidr)])
    error_message = "Explicit restricted VPN/admin CIDRs required."
  }
}
variable "trusted_proxy_cidrs" {
  type = list(string)
  validation {
    condition     = length(var.trusted_proxy_cidrs) > 0 && alltrue([for cidr in var.trusted_proxy_cidrs : can(cidrnetmask(cidr)) && cidr != "0.0.0.0/0"])
    error_message = "Explicit ALB subnet proxy CIDRs required."
  }
}
variable "aws_endpoint_security_group_id" { type = string }
variable "metrics_security_group_ids" {
  type    = set(string)
  default = []
  validation {
    condition     = alltrue([for id in var.metrics_security_group_ids : can(regex("^sg-[A-Za-z0-9-]+$", id))])
    error_message = "Only explicit private telemetry security groups may access the identity management port."
  }
}
variable "public_hostname" {
  type = string
  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]+[a-z0-9]$", var.public_hostname))
    error_message = "Explicit DNS public hostname required."
  }
}
variable "admin_hostname" {
  type = string
  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]+[a-z0-9]$", var.admin_hostname)) && var.admin_hostname != var.public_hostname
    error_message = "Distinct private admin DNS hostname required."
  }
}
variable "realm" {
  type = string
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,62}$", var.realm)) && var.realm != "master"
    error_message = "Explicit business realm; master cannot be public."
  }
}
variable "public_certificate_arn" { type = string }
variable "admin_certificate_arn" { type = string }
variable "kms_key_arn" {
  type = string
  validation {
    condition     = can(regex("^arn:aws[a-z-]*:kms:[a-z0-9-]+:[0-9]{12}:key/[A-Za-z0-9-]+$", var.kms_key_arn))
    error_message = "Approved encryption key ARN required."
  }
}
variable "identity_image" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/nigson/${var.environment}/identity@sha256:[a-f0-9]{64}$", var.identity_image))
    error_message = "Immutable optimized Keycloak ECR artifact digest from this environment's identity repository required."
  }
}
variable "cpu_architecture" {
  type = string
  validation {
    condition     = contains(["ARM64", "X86_64"], var.cpu_architecture)
    error_message = "Explicit supported ECS identity task architecture required."
  }
}
variable "secret_refs" {
  type = object({ database_password = string, tls_certificate = string, tls_key = string, database_ca = string })
  validation {
    condition     = alltrue([for arn in values(var.secret_refs) : can(regex("^arn:aws[a-z-]*:secretsmanager:[a-z0-9-]+:[0-9]{12}:secret:", arn))])
    error_message = "Secrets Manager references required; never pass secret values."
  }
}
variable "postgres_version" {
  type = string
  validation {
    condition     = can(regex("^18\\.[0-9]+$", var.postgres_version))
    error_message = "Region-qualified PostgreSQL18 minor version required."
  }
}
variable "database_instance_class" { type = string }
variable "backup_retention_days" {
  type = number
  validation {
    condition     = var.backup_retention_days >= 1 && var.backup_retention_days <= 35
    error_message = "Approved RDS backup retention 1..35 days required."
  }
}
variable "log_retention_days" {
  type = number
  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653], var.log_retention_days)
    error_message = "Explicit supported CloudWatch retention required."
  }
}
variable "bootstrap_secret_refs" {
  type = object({ username = string, password = string })
  validation {
    condition     = alltrue([for arn in values(var.bootstrap_secret_refs) : can(regex("^arn:aws[a-z-]*:secretsmanager:[a-z0-9-]+:[0-9]{12}:secret:", arn))])
    error_message = "Explicit temporary bootstrap secret references required."
  }
}
variable "policy_revision" {
  type = string
  validation {
    condition     = length(trimspace(var.policy_revision)) > 0
    error_message = "Approved identity/TLS/retention policy revision required."
  }
}
variable "named_operator" {
  type = string
  validation {
    condition     = length(trimspace(var.named_operator)) > 0
    error_message = "Named identity/restore operator required."
  }
}
variable "alarm_destination_arns" {
  type = list(string)
  validation {
    condition     = length(var.alarm_destination_arns) > 0 && alltrue([for arn in var.alarm_destination_arns : can(regex("^arn:aws[a-z-]*:sns:", arn))])
    error_message = "Explicit operator alert destinations required."
  }
}
variable "service_enabled" {
  type        = bool
  default     = false
  description = "Enable only after dedicated DB owner, temporary bootstrap and approved realm/client/MFA provisioning."
}
