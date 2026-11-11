variable "environment" {
  type = string
  validation {
    condition     = contains(["stage", "prod"], var.environment)
    error_message = "Use stage or prod."
  }
}
variable "project_id" {
  type = string
  validation {
    condition     = can(regex("^[a-f0-9]{32}$", var.project_id))
    error_message = "An OVH Public Cloud project ID is required."
  }
}
variable "region" { type = string }
variable "external_network_id" { type = string }
variable "image_id" {
  description = "Explicit Ubuntu 24.04 image UUID available in the selected region."
  type        = string
}
variable "ssh_public_key" {
  type = string
  validation {
    condition     = can(regex("^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp256) [A-Za-z0-9+/=]+", var.ssh_public_key))
    error_message = "Supply only an SSH public key."
  }
}
variable "admin_cidrs" {
  type = set(string)
  validation {
    condition     = length(var.admin_cidrs) > 0 && alltrue([for cidr in var.admin_cidrs : can(cidrnetmask(cidr)) && try(tonumber(split("/", cidr)[1]) >= 24, false)])
    error_message = "SSH requires explicit IPv4 administrator ranges /24 or narrower."
  }
}
variable "subnet_cidr" {
  type    = string
  default = "10.70.10.0/24"
  validation {
    condition     = can(cidrnetmask(var.subnet_cidr)) && try(tonumber(split("/", var.subnet_cidr)[1]) >= 16 && tonumber(split("/", var.subnet_cidr)[1]) <= 24 && can(regex("^(10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.)", cidrhost(var.subnet_cidr, 0))), false)
    error_message = "Use an RFC1918 IPv4 subnet between /16 and /24."
  }
}
variable "dns_servers" {
  type = list(string)
  validation {
    condition     = length(var.dns_servers) > 0 && alltrue([for ip in var.dns_servers : can(cidrhost("${ip}/32", 0))])
    error_message = "Supply approved IPv4 DNS resolvers."
  }
}
variable "nodes" {
  description = "VM name to role/flavor; explicit replicas, no inferred autoscaling."
  type        = map(object({ role = string, flavor_id = string }))
  validation {
    condition = length(var.nodes) >= 5 && length(var.nodes) <= 40 && alltrue([
      for name, node in var.nodes : can(regex("^[a-z][a-z0-9-]{0,30}$", name)) && contains(["edge", "commerce", "experience", "integration", "identity", "ai"], node.role) && length(node.flavor_id) > 0
    ]) && alltrue([for role in ["edge", "commerce", "experience", "integration", "identity"] : contains([for node in values(var.nodes) : node.role], role)])
    error_message = "Require edge, commerce, experience, integration and identity VM roles, with explicit flavor IDs."
  }
}
variable "egress_tcp_ports" {
  description = "Approved outbound TCP ports, including registry/provider HTTPS and database TLS."
  type        = set(number)
  default     = [80, 443]
  validation {
    condition     = alltrue([for port in var.egress_tcp_ports : port >= 1 && port <= 65535 && floor(port) == port])
    error_message = "Outbound TCP ports must be integers 1..65535."
  }
}
variable "database" {
  type = object({ region = string, version = string, plan = string, flavor = string, node_count = number, capabilities_verified = bool })
  validation {
    condition     = var.database.version == "18" && contains(["essential", "business", "enterprise"], var.database.plan) && var.database.node_count >= 1 && var.database.node_count <= 3 && floor(var.database.node_count) == var.database.node_count && var.database.capabilities_verified
    error_message = "Verify PostgreSQL 18, pgvector 0.8.2 and owner-role bootstrap support in this OVH region/plan before applying."
  }
}
variable "storage_region" { type = string }
variable "bucket_names" {
  type = set(string)
  validation {
    condition     = length(var.bucket_names) > 0 && alltrue([for name in var.bucket_names : can(regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$", name))])
    error_message = "Supply globally unique private bucket names."
  }
}
variable "registry" {
  type = object({ region = string, plan_id = string })
}

variable "workloads" {
  description = "Immutable deployment images and real health checks per VM role. Secrets are supplied outside Terraform in runtime.env."
  type        = map(object({ image = string, health_command = list(string), memory_mb = number }))
  validation {
    condition     = alltrue([for role, workload in var.workloads : contains(["edge", "commerce", "experience", "integration", "identity", "ai"], role) && can(regex("^[^[:space:]]+@sha256:[a-f0-9]{64}$", workload.image)) && length(workload.health_command) >= 2 && contains(["CMD", "CMD-SHELL"], workload.health_command[0]) && workload.memory_mb >= 256])
    error_message = "Workloads require digest-pinned images, explicit health commands and bounded memory."
  }
}

variable "ntp_servers" {
  description = "Approved IPv4 time servers for private VMs."
  type        = list(string)
  validation {
    condition     = length(var.ntp_servers) > 0 && alltrue([for ip in var.ntp_servers : can(cidrhost("${ip}/32", 0))])
    error_message = "Supply approved IPv4 time servers."
  }
}
