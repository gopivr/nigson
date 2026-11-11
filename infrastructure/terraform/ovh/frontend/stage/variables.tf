variable "region" { type = string }
variable "network_id" { type = string }
variable "subnet_id" { type = string }
variable "subnet_cidr" {
  type = string
  validation {
    condition     = can(cidrnetmask(var.subnet_cidr)) && try(tonumber(split("/", var.subnet_cidr)[1]) >= 16 && tonumber(split("/", var.subnet_cidr)[1]) <= 28 && can(regex("^(10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.)", cidrhost(var.subnet_cidr, 0))), false)
    error_message = "Frontend VMs require an existing private RFC1918 IPv4 subnet."
  }
}
variable "external_network_id" { type = string }
variable "image_id" { type = string }
variable "flavor_id" { type = string }
variable "ssh_key_pair" { type = string }
variable "admin_cidrs" {
  type = set(string)
  validation {
    condition     = length(var.admin_cidrs) > 0 && alltrue([for cidr in var.admin_cidrs : can(cidrnetmask(cidr)) && try(tonumber(split("/", cidr)[1]) >= 24, false)])
    error_message = "Supply narrow administrator/VPN IPv4 CIDRs."
  }
}
variable "replicas" {
  type    = number
  default = 2
  validation {
    condition     = var.replicas >= ("stage" == "prod" ? 2 : 1) && var.replicas <= 6 && floor(var.replicas) == var.replicas
    error_message = "Use 1..6 nodes, with at least two in production."
  }
}
variable "frontend_image" {
  type = string
  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9./:_-]*@sha256:[a-f0-9]{64}$", var.frontend_image))
    error_message = "Use the digest-pinned frontend image containing shell and remotes."
  }
}
variable "domain_name" {
  type = string
  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]+\\.[a-z]{2,}$", var.domain_name))
    error_message = "Supply the frontend hostname."
  }
}
variable "backend_origin_hostname" {
  type = string
  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]+\\.[a-z]{2,}$", var.backend_origin_hostname)) && var.backend_origin_hostname != var.domain_name
    error_message = "Use a distinct HTTPS backend hostname without path or port."
  }
}
variable "dns_servers" {
  type = set(string)
  validation {
    condition     = length(var.dns_servers) > 0 && alltrue([for ip in var.dns_servers : can(cidrhost("${ip}/32", 0))])
    error_message = "Approved IPv4 servers are required."
  }
}
variable "ntp_servers" {
  type = set(string)
  validation {
    condition     = length(var.ntp_servers) > 0 && alltrue([for ip in var.ntp_servers : can(cidrhost("${ip}/32", 0))])
    error_message = "Approved IPv4 servers are required."
  }
}

variable "openstack_project_id" { type = string }
variable "openstack_auth_url" {
  type    = string
  default = "https://auth.cloud.ovh.net/v3"
}
