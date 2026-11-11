terraform {
  required_version = ">= 1.12.2, < 2.0.0"
  required_providers {
    ovh       = { source = "ovh/ovh", version = "2.22.0" }
    openstack = { source = "terraform-provider-openstack/openstack", version = "3.4.0" }
  }
}
