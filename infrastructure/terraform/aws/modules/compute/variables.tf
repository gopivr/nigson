variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "qa", "stage", "prod"], var.environment)
    error_message = "Explicit environment required."
  }
}
variable "region" { type = string }
variable "product_phase" {
  type = number
  validation {
    condition     = contains([1, 2, 3, 4, 5], var.product_phase)
    error_message = "Explicit product phase required."
  }
}
variable "policy_revision" {
  type = string
  validation {
    condition     = length(trimspace(var.policy_revision)) > 0
    error_message = "Approved compute/security policy revision required."
  }
}
variable "vpc_id" { type = string }
variable "edge_subnet_ids" {
  type = list(string)
  validation {
    condition     = length(var.edge_subnet_ids) >= 2
    error_message = "Public ALB requires edge subnets in at least two AZs."
  }
}
variable "application_subnet_ids" {
  type = list(string)
  validation {
    condition     = length(var.application_subnet_ids) >= 2
    error_message = "ECS tasks require private application subnets in at least two AZs."
  }
}
variable "private_namespace" {
  type = string
  validation {
    condition     = can(regex("^[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?$", var.private_namespace))
    error_message = "Explicit private DNS namespace required."
  }
}
variable "kms_key_arn" {
  type = string
  validation {
    condition     = can(regex("^arn:aws[a-z-]*:kms:[a-z0-9-]+:[0-9]{12}:key/[A-Za-z0-9-]+$", var.kms_key_arn))
    error_message = "Approved KMS key ARN required for images, logs and task secrets."
  }
}
variable "waf_web_acl_arn" {
  type = string
  validation {
    condition     = can(regex("^arn:aws[a-z-]*:wafv2:[a-z0-9-]+:[0-9]{12}:regional/webacl/", var.waf_web_acl_arn))
    error_message = "A configured regional WAF web ACL is required before public ALB creation."
  }
}
variable "public_certificate_arn" {
  type = string
  validation {
    condition     = can(regex("^arn:aws[a-z-]*:acm:[a-z0-9-]+:[0-9]{12}:certificate/", var.public_certificate_arn))
    error_message = "An explicit public TLS certificate is required."
  }
}
variable "alb_deletion_protection" { type = bool }
variable "log_retention_days" {
  type = number
  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653], var.log_retention_days)
    error_message = "An approved log retention duration is required."
  }
}
variable "database_connection_budget" {
  type = number
  validation {
    condition     = var.database_connection_budget >= 1
    error_message = "Reserved database connection budget must be explicit and positive."
  }
}
variable "cpu_target_percent" {
  type = number
  validation {
    condition     = var.cpu_target_percent >= 1 && var.cpu_target_percent <= 100
    error_message = "Measured ECS service CPU target must be between 1 and 100 percent."
  }
}
variable "services" {
  type = map(object({
    enabled                 = bool
    public                  = bool
    port                    = number
    cpu                     = number
    memory                  = number
    architecture            = string
    min_count               = number
    desired_count           = number
    max_count               = number
    db_connections_per_task = number
    image_digest            = string
    health_path             = string
    probe_command           = list(string)
    environment             = map(string)
    secret_arns             = map(string)
    egress = map(object({
      description = string
      cidr_ipv4   = string
      protocol    = string
      port        = number
    }))
  }))
  validation {
    condition = length(var.services) >= 3 && (var.product_phase == 5 ? contains(keys(var.services), "ai") : !contains(keys(var.services), "ai")) && alltrue([
      for name, service in var.services : contains(["commerce", "experience", "integration", "ai"], name) &&
      (name != "ai" || var.product_phase == 5) &&
      (!service.public || contains(["commerce", "experience"], name)) &&
      service.port >= 1 && service.port <= 65535 &&
      contains([256, 512, 1024, 2048, 4096], service.cpu) &&
      ((service.cpu == 256 && contains([512, 1024, 2048], service.memory)) ||
        (service.cpu == 512 && service.memory >= 1024 && service.memory <= 4096 && service.memory % 1024 == 0) ||
        (service.cpu == 1024 && service.memory >= 2048 && service.memory <= 8192 && service.memory % 1024 == 0) ||
        (service.cpu == 2048 && service.memory >= 4096 && service.memory <= 16384 && service.memory % 1024 == 0) ||
      (service.cpu == 4096 && service.memory >= 8192 && service.memory <= 30720 && service.memory % 1024 == 0)) &&
      contains(["X86_64", "ARM64"], service.architecture) &&
      can(regex("^sha256:[a-f0-9]{64}$", service.image_digest)) &&
      startswith(service.health_path, "/") &&
      length(service.probe_command) >= 2 &&
      service.min_count >= 0 && service.desired_count >= 0 && service.max_count >= 1 &&
      service.min_count <= service.desired_count && service.desired_count <= service.max_count &&
      service.max_count <= (contains(["commerce", "experience"], name) ? 8 : 4) &&
      (var.environment != "prod" || !service.public || service.min_count >= 2) &&
      service.db_connections_per_task >= 1 &&
      length(service.egress) > 0 && length(service.egress) <= 20 &&
      alltrue([for rule in values(service.egress) : can(cidrnetmask(rule.cidr_ipv4)) && rule.cidr_ipv4 != "0.0.0.0/0" && rule.port >= 1 && rule.port <= 65535 && contains(["tcp", "udp"], lower(rule.protocol))]) &&
      alltrue([for arn in values(service.secret_arns) : can(regex("^arn:aws[a-z-]*:secretsmanager:[a-z0-9-]+:[0-9]{12}:secret:[A-Za-z0-9/_+=.@-]+$", arn))])
    ]) && sum([for service in values(var.services) : service.max_count * service.db_connections_per_task]) <= var.database_connection_budget
    error_message = "Services need valid Fargate sizes, phase-gated immutable images, bounded scaling and DB pools, health probes, named secrets and explicit destination egress; do not use wildcard egress."
  }
}
variable "public_routes" {
  type = map(object({ service = string, priority = number, path_patterns = list(string) }))
  validation {
    condition = length(distinct(flatten([for route in values(var.public_routes) : [for index in range(length(route.path_patterns)) : route.priority + index]]))) == length(flatten([for route in values(var.public_routes) : [for index in range(length(route.path_patterns)) : route.priority + index]])) && alltrue([
      for route in values(var.public_routes) : route.priority >= 10 &&
      contains(keys(var.services), route.service) &&
      var.services[route.service].enabled && var.services[route.service].public &&
      length(route.path_patterns) > 0 &&
      alltrue([for path in route.path_patterns : startswith(path, "/") && !strcontains(path, "?")])
    ])
    error_message = "Public ALB routes need unique priorities, enabled public services and explicit path-only patterns."
  }
}
