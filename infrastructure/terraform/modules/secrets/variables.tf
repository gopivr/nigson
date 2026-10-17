variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "qa", "stage", "prod"], var.environment)
    error_message = "Explicit secret environment required."
  }
}
variable "policy_revision" {
  type = string
  validation {
    condition     = length(trimspace(var.policy_revision)) > 0
    error_message = "Approved encryption and secret-retention policy revision required."
  }
}
variable "key_administrator_role_arns" {
  type = set(string)
  validation {
    condition     = length(var.key_administrator_role_arns) > 0 && length(var.key_administrator_role_arns) <= 5 && alltrue([for arn in var.key_administrator_role_arns : can(regex("^arn:aws(-us-gov|-cn)?:iam::[0-9]{12}:role/[A-Za-z0-9+=,.@_/-]+$", arn))])
    error_message = "One to five named KMS administrator roles must be supplied."
  }
}
variable "secret_recovery_window_days" {
  type = number
  validation {
    condition     = floor(var.secret_recovery_window_days) == var.secret_recovery_window_days && var.secret_recovery_window_days >= 7 && var.secret_recovery_window_days <= 30
    error_message = "An approved Secrets Manager recovery window from 7 to 30 days is required."
  }
}
variable "secret_names" {
  type = map(map(string))
  validation {
    condition = contains(keys(var.secret_names), "identity") && toset(["database_password", "tls_certificate", "tls_key", "database_ca", "bootstrap_username", "bootstrap_password"]) == toset(keys(var.secret_names.identity)) && alltrue([for service in ["commerce", "experience", "integration"] : contains(keys(var.secret_names), service) && contains(keys(var.secret_names[service]), "migration_password")]) && (var.product_phase == 5 ? contains(keys(var.secret_names), "ai") && contains(keys(var.secret_names.ai), "migration_password") : !contains(keys(var.secret_names), "ai")) && alltrue([for service, secrets in var.secret_names : contains(["commerce", "experience", "integration", "identity", "ai"], service) && (service != "ai" || var.product_phase == 5) && alltrue([
      for alias, name in secrets : can(regex("^[A-Za-z0-9/_+=.@-]{1,512}$", alias)) && startswith(name, "nigson/${var.environment}/${service}/") && !strcontains(name, "..")
    ])])
    error_message = "Secret metadata must use exact approved service paths; AI entries are BP5-only. Secret values are never Terraform inputs."
  }
}
variable "product_phase" {
  type = number
  validation {
    condition     = contains([1, 2, 3, 4, 5], var.product_phase)
    error_message = "Explicit product phase required."
  }
}
