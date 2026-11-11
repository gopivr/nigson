variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "qa", "stage", "prod"], var.environment)
    error_message = "Explicit storage environment required."
  }
}
variable "region" {
  type = string
  validation {
    condition     = can(regex("^[a-z]{2}(-gov)?-[a-z]+-[0-9]+$", var.region))
    error_message = "Explicit approved AWS region required."
  }
}
variable "policy_revision" {
  type = string
  validation {
    condition     = length(trimspace(var.policy_revision)) > 0
    error_message = "Approved storage, classification and retention policy revision required."
  }
}
variable "kms_key_arn" {
  type = string
  validation {
    condition     = can(regex("^arn:aws[a-z-]*:kms:[a-z0-9-]+:[0-9]{12}:key/[A-Za-z0-9-]+$", var.kms_key_arn))
    error_message = "Approved KMS key ARN required for private and quarantine objects."
  }
}
variable "cloudfront_distribution_arn" {
  type = string
  validation {
    condition     = can(regex("^arn:aws[a-z-]*:cloudfront::[0-9]{12}:distribution/[A-Z0-9]+$", var.cloudfront_distribution_arn))
    error_message = "An explicit CloudFront distribution ARN is required for public media origin access."
  }
}
variable "bucket_names" {
  type = map(string)
  validation {
    condition = (toset(keys(var.bucket_names)) == toset(["public_media", "quarantine", "private_assets", "carrier_labels"]) &&
      length(distinct(values(var.bucket_names))) == 4 &&
    alltrue([for name in values(var.bucket_names) : can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", name)) && !strcontains(name, "..")]))
    error_message = "Supply four distinct globally unique DNS-compatible names for public media, quarantine, private assets and carrier labels."
  }
}
variable "lifecycle_days" {
  type = map(object({ current_version_days = number, noncurrent_version_days = number, incomplete_upload_days = number }))
  validation {
    condition = toset(keys(var.lifecycle_days)) == toset(["public_media", "quarantine", "private_assets", "carrier_labels"]) && alltrue([
      for rule in values(var.lifecycle_days) : alltrue([for days in [rule.current_version_days, rule.noncurrent_version_days, rule.incomplete_upload_days] : floor(days) == days && days >= 1 && days <= 3653])
    ])
    error_message = "Explicit approved current, noncurrent and incomplete-upload retention periods are required for every bucket."
  }
}
variable "bucket_access" {
  type = map(object({
    readers = map(object({ role_arn = string, prefixes = set(string) }))
    writers = map(object({ role_arn = string, prefixes = set(string) }))
  }))
  validation {
    condition = toset(keys(var.bucket_access)) == toset(["public_media", "quarantine", "private_assets", "carrier_labels"]) && alltrue(flatten([
      for access in values(var.bucket_access) : concat(
        [for grant in values(access.readers) : can(regex("^arn:aws(-us-gov|-cn)?:iam::[0-9]{12}:role/[A-Za-z0-9+=,.@_/-]+$", grant.role_arn)) && length(grant.prefixes) > 0 && alltrue([for prefix in grant.prefixes : !startswith(prefix, "/") && !strcontains(prefix, "..") && !strcontains(prefix, "*")])],
        [for grant in values(access.writers) : can(regex("^arn:aws(-us-gov|-cn)?:iam::[0-9]{12}:role/[A-Za-z0-9+=,.@_/-]+$", grant.role_arn)) && length(grant.prefixes) > 0 && alltrue([for prefix in grant.prefixes : !startswith(prefix, "/") && !strcontains(prefix, "..") && !strcontains(prefix, "*")])]
      )
    ]))
    error_message = "Bucket access must use exact task roles and non-wildcard purpose prefixes; deletion permissions are never granted."
  }
}
