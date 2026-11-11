mock_provider "openstack" {
  mock_resource "openstack_networking_port_v2" {
    defaults = { all_fixed_ips = ["10.70.10.150"] }
  }
}
variables {
  environment             = "stage"
  region                  = "GRA9"
  network_id              = "00000000-0000-0000-0000-000000000001"
  subnet_id               = "00000000-0000-0000-0000-000000000002"
  subnet_cidr             = "10.70.10.0/24"
  external_network_id     = "00000000-0000-0000-0000-000000000003"
  image_id                = "00000000-0000-0000-0000-000000000004"
  flavor_id               = "synthetic-flavor"
  ssh_key_pair            = "synthetic-public-key"
  admin_cidrs             = ["192.0.2.10/32"]
  replicas                = 1
  frontend_image          = "synthetic.invalid/frontend@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  domain_name             = "shop.synthetic.test"
  backend_origin_hostname = "api.synthetic.test"
  dns_servers             = ["192.0.2.53"]
  ntp_servers             = ["192.0.2.123"]
}
run "private_frontend" {
  command = plan
  assert {
    condition     = openstack_networking_secgroup_v2.frontend.delete_default_rules && openstack_networking_secgroup_rule_v2.https.remote_ip_prefix == var.subnet_cidr && openstack_lb_listener_v2.https.protocol_port == 443
    error_message = "Only the load balancer may expose the private HTTPS frontend."
  }
  assert {
    condition     = length(openstack_compute_instance_v2.frontend) == 1 && length(openstack_lb_member_v2.frontend) == 1
    error_message = "Every frontend replica must have a matching load-balancer member."
  }
}
run "production_replicas" {
  command = plan
  variables {
    environment = "prod"
    replicas    = 2
  }
  assert {
    condition     = length(openstack_lb_member_v2.frontend) == 2
    error_message = "Production needs two frontend targets."
  }
}
run "reject_single_production_node" {
  command = plan
  variables { environment = "prod" }
  expect_failures = [var.replicas]
}
run "reject_mutable_image" {
  command = plan
  variables { frontend_image = "synthetic.invalid/frontend:latest" }
  expect_failures = [var.frontend_image]
}
run "reject_public_ssh" {
  command = plan
  variables { admin_cidrs = ["0.0.0.0/0"] }
  expect_failures = [var.admin_cidrs]
}
