variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "qa", "stage", "prod"], var.environment)
    error_message = "Explicit environment required."
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
    condition     = can(regex("^[a-zA-Z0-9_-]{1,40}$", var.policy_revision))
    error_message = "Approved network policy revision required."
  }
}
variable "vpc_cidr" {
  type = string
  validation {
    condition = can(cidrnetmask(var.vpc_cidr)) && contains(["10", "172", "192"], split(".", cidrhost(var.vpc_cidr, 0))[0]) && (
      split(".", cidrhost(var.vpc_cidr, 0))[0] == "10" ||
      (split(".", cidrhost(var.vpc_cidr, 0))[0] == "172" && try(tonumber(split(".", cidrhost(var.vpc_cidr, 0))[1]) >= 16 && tonumber(split(".", cidrhost(var.vpc_cidr, 0))[1]) <= 31, false)) ||
      (split(".", cidrhost(var.vpc_cidr, 0))[0] == "192" && split(".", cidrhost(var.vpc_cidr, 0))[1] == "168")
    )
    error_message = "VPC must use a valid RFC1918 IPv4 CIDR."
  }
}
variable "availability_zones" {
  type = list(string)
  validation {
    condition     = length(var.availability_zones) >= 2 && length(distinct(var.availability_zones)) == length(var.availability_zones)
    error_message = "At least two distinct explicitly approved availability zones are required."
  }
}
variable "subnet_cidrs" {
  type = map(object({ edge = string, application = string, data = string }))
  validation {
    condition = length(var.subnet_cidrs) >= 2 && alltrue([
      for cidrs in values(var.subnet_cidrs) : alltrue([for cidr in [cidrs.edge, cidrs.application, cidrs.data] : can(cidrnetmask(cidr))]) &&
      length(distinct([cidrs.edge, cidrs.application, cidrs.data])) == 3
    ]) && length(distinct(flatten([for cidrs in values(var.subnet_cidrs) : [cidrs.edge, cidrs.application, cidrs.data]]))) == length(flatten([for cidrs in values(var.subnet_cidrs) : [cidrs.edge, cidrs.application, cidrs.data]]))
    error_message = "Every AZ needs distinct valid CIDRs for edge, application and isolated data subnets."
  }
}
variable "interface_endpoints" {
  type = set(string)
  default = [
    "ecr.api", "ecr.dkr", "logs", "kms", "secretsmanager", "sns", "sqs", "ssm"
  ]
  validation {
    condition     = length(var.interface_endpoints) > 0 && alltrue([for endpoint in var.interface_endpoints : can(regex("^[a-z0-9.-]+$", endpoint))])
    error_message = "Only named AWS service interface endpoints are accepted."
  }
}
