variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "qa", "stage", "prod"], var.environment)
    error_message = "Explicit deployment environment required."
  }
}
variable "policy_revision" {
  type = string
  validation {
    condition     = can(regex("^[a-zA-Z0-9_-]{1,40}$", var.policy_revision))
    error_message = "Approved revision required."
  }
}
variable "key_administrator_arns" {
  type = set(string)
  validation {
    condition     = length(var.key_administrator_arns) > 0 && length(var.key_administrator_arns) <= 5 && alltrue([for arn in var.key_administrator_arns : can(regex("^arn:aws(-us-gov|-cn)?:iam::[0-9]{12}:role/[a-zA-Z0-9+=,.@_/-]+$", arn))])
    error_message = "Named key administrator role ARNs required."
  }
}
variable "commerce_task_role_arn" {
  type = string
  validation {
    condition     = can(regex("^arn:aws(-us-gov|-cn)?:iam::[0-9]{12}:role/[a-zA-Z0-9+=,.@_/-]+$", var.commerce_task_role_arn)) && !contains(var.key_administrator_arns, var.commerce_task_role_arn)
    error_message = "Commerce task role must be explicit and separate from key administrators."
  }
}
variable "commerce_task_role_name" {
  type = string
  validation {
    condition     = can(regex("^[a-zA-Z0-9+=,.@_-]{1,64}$", var.commerce_task_role_name))
    error_message = "Existing commerce task role name required."
  }
}
variable "session_key_provisioner_arn" {
  type = string
  validation {
    condition     = can(regex("^arn:aws(-us-gov|-cn)?:iam::[0-9]{12}:role/[a-zA-Z0-9+=,.@_/-]+$", var.session_key_provisioner_arn)) && var.session_key_provisioner_arn != var.commerce_task_role_arn
    error_message = "Session key provisioning must be separate from the runtime role."
  }
}
variable "key_deletion_window_days" {
  type = number
  validation {
    condition     = floor(var.key_deletion_window_days) == var.key_deletion_window_days && var.key_deletion_window_days >= 7 && var.key_deletion_window_days <= 30
    error_message = "Explicit KMS maintenance deletion window in 7..30 days required."
  }
}
variable "private_hops" {
  type = map(object({ source_security_group_id = string, target_security_group_id = string, target_port = number }))
  validation {
    condition = length(var.private_hops) <= 16 && (var.product_phase == 5 || alltrue([for name in keys(var.private_hops) : !strcontains(name, "ai")])) && alltrue([for name, hop in var.private_hops :
      contains(["commerce-experience", "commerce-integration", "commerce-ai", "experience-commerce", "integration-commerce", "integration-experience", "ai-commerce", "ai-experience"], name) &&
      can(regex("^sg-[a-zA-Z0-9-]+$", hop.source_security_group_id)) && can(regex("^sg-[a-zA-Z0-9-]+$", hop.target_security_group_id)) && lookup({ commerce = 8081, experience = 8082, integration = 8083, ai = 8084 }, split("-", name)[length(split("-", name)) - 1], 0) == hop.target_port
    ])
    error_message = "Only explicit registered service security groups and application ports are accepted; no public CIDR rules."
  }
}

variable "product_phase" {
  type = number
  validation {
    condition     = contains([1, 2, 3, 4, 5], var.product_phase)
    error_message = "Explicit product phase required."
  }
}
