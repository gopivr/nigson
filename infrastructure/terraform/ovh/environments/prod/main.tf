terraform {
  required_version = ">= 1.12.2, < 2.0.0"
  required_providers {
    ovh       = { source = "ovh/ovh", version = "2.22.0" }
    openstack = { source = "terraform-provider-openstack/openstack", version = "3.4.0" }
  }
  backend "s3" {}
}
provider "ovh" { endpoint = var.ovh_endpoint }
provider "openstack" {
  auth_url  = var.openstack_auth_url
  tenant_id = var.openstack_project_id
  region    = var.region
}
module "platform" {
  ntp_servers         = var.ntp_servers
  workloads           = var.workloads
  source              = "../../modules/platform"
  environment         = "prod"
  project_id          = var.project_id
  region              = var.region
  external_network_id = var.external_network_id
  image_id            = var.image_id
  ssh_public_key      = var.ssh_public_key
  admin_cidrs         = var.admin_cidrs
  subnet_cidr         = var.subnet_cidr
  dns_servers         = var.dns_servers
  nodes               = var.nodes
  egress_tcp_ports    = var.egress_tcp_ports
  database            = var.database
  storage_region      = var.storage_region
  bucket_names        = var.bucket_names
  registry            = var.registry
}
output "deployment" {
  value = {
    public_ip               = module.platform.public_ip
    private_hosts           = module.platform.private_hosts
    database_endpoints      = module.platform.database_endpoints
    database_ids            = module.platform.database_ids
    registry_url            = module.platform.registry_url
    bucket_names            = module.platform.bucket_names
    activation_requirements = module.platform.activation_requirements
  }
}
output "bootstrap_sql" { value = module.platform.bootstrap_sql }

output "compose_files" { value = module.platform.compose_files }
