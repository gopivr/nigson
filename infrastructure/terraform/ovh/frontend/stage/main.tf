terraform {
  required_version = ">= 1.12.2, < 2.0.0"
  required_providers { openstack = { source = "terraform-provider-openstack/openstack", version = "3.4.0" } }
  backend "s3" {}
}
provider "openstack" {
  region    = var.region
  tenant_id = var.openstack_project_id
  auth_url  = var.openstack_auth_url
}
module "frontend" {
  source                  = "../../modules/frontend"
  environment             = "stage"
  region                  = var.region
  network_id              = var.network_id
  subnet_id               = var.subnet_id
  subnet_cidr             = var.subnet_cidr
  external_network_id     = var.external_network_id
  image_id                = var.image_id
  flavor_id               = var.flavor_id
  ssh_key_pair            = var.ssh_key_pair
  admin_cidrs             = var.admin_cidrs
  replicas                = var.replicas
  frontend_image          = var.frontend_image
  domain_name             = var.domain_name
  backend_origin_hostname = var.backend_origin_hostname
  dns_servers             = var.dns_servers
  ntp_servers             = var.ntp_servers
}
output "frontend" { value = { url = module.frontend.url, public_ip = module.frontend.public_ip, private_ips = module.frontend.private_ips } }
