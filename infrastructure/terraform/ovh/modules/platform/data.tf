resource "ovh_cloud_project_database" "postgres" {
  for_each     = toset(["domain", "identity"])
  service_name = var.project_id
  description  = "${local.name}-${each.key}"
  engine       = "postgresql"
  version      = var.database.version
  plan         = var.database.plan
  flavor       = var.database.flavor
  dynamic "nodes" {
    for_each = range(var.database.node_count)
    content {
      region     = var.database.region
      network_id = openstack_networking_subnet_v2.application.network_id
      subnet_id  = openstack_networking_subnet_v2.application.id
    }
  }
  ip_restrictions {
    description = "Private application network"
    ip          = var.subnet_cidr
  }
  lifecycle {
    prevent_destroy = true
    precondition {
      condition     = var.environment != "prod" || var.database.node_count >= 2 && var.database.plan != "essential"
      error_message = "Production requires a multi-node managed PostgreSQL plan."
    }
  }
}
resource "ovh_cloud_project_database_database" "application" {
  for_each     = { domain = "nigson", identity = "keycloak" }
  service_name = var.project_id
  engine       = "postgresql"
  cluster_id   = ovh_cloud_project_database.postgres[each.key].id
  name         = each.value
  lifecycle { prevent_destroy = true }
}
resource "ovh_cloud_project_storage" "private" {
  for_each     = var.bucket_names
  service_name = var.project_id
  region_name  = var.storage_region
  name         = each.key
  hide_objects = true
  versioning   = { status = "enabled" }
  encryption   = { sse_algorithm = "AES256" }
  lifecycle { prevent_destroy = true }
}
resource "ovh_cloud_project_containerregistry" "images" {
  service_name = var.project_id
  name         = local.name
  region       = var.registry.region
  plan_id      = var.registry.plan_id
  lifecycle { prevent_destroy = true }
}
module "owner_bootstrap" {
  source = "../../../aws/database"
}
