terraform {
  required_version = ">= 1.12.2, < 2.0.0"
}

variable "database_name" {
  description = "Target domain database; Keycloak vendor storage is separate."
  type        = string
  default     = "nigson"
  validation {
    condition     = can(regex("^[a-z][a-z0-9_]{0,62}$", var.database_name))
    error_message = "Database name must be a lower-case PostgreSQL identifier."
  }
}

output "bootstrap_sql" {
  description = "Execute once with the privileged database provisioning identity before owner migrations. Passwords are psql variables, never Terraform state."
  value = templatefile("${path.module}/bootstrap.sql.tftpl", {
    database_name = var.database_name
    owners        = ["commerce", "experience", "integration", "ai"]
  })
}
