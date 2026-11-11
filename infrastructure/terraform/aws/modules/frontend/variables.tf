variable "environment" {
  type = string
  validation {
    condition     = contains(["stage", "prod"], var.environment)
    error_message = "Use stage or prod."
  }
}
variable "bucket_name" { type = string }
variable "domain_name" {
  type = string
  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]+\\.[a-z]{2,}$", var.domain_name))
    error_message = "A public frontend hostname is required."
  }
}
variable "certificate_arn" {
  type = string
  validation {
    condition     = can(regex("^arn:aws:acm:us-east-1:[0-9]{12}:certificate/", var.certificate_arn))
    error_message = "CloudFront requires an ACM certificate in us-east-1."
  }
}
variable "backend_origin_hostname" {
  description = "HTTPS backend ALB/reverse-proxy DNS name with a valid certificate, routing public API and BFF paths."
  type        = string
  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]+\\.[a-z]{2,}$", var.backend_origin_hostname)) && var.backend_origin_hostname != var.domain_name
    error_message = "Use a distinct HTTPS backend hostname without path or port."
  }
}
variable "release_id" {
  type = string
  validation {
    condition     = can(regex("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}$", var.release_id))
    error_message = "Use an immutable release identifier."
  }
}
variable "zone_id" {
  description = "Existing Route53 zone ID; null leaves DNS to your DNS operator."
  type        = string
  default     = null
}
variable "web_acl_arn" {
  type = string
  validation {
    condition     = can(regex("^arn:aws:wafv2:us-east-1:[0-9]{12}:global/webacl/", var.web_acl_arn))
    error_message = "Supply an approved global WAF ACL for CloudFront."
  }
}
