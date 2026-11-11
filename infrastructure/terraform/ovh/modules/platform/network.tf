locals {
  name       = "nigson-${var.environment}"
  ports      = { edge = 443, commerce = 8081, experience = 8082, integration = 8083, identity = 8080, ai = 8084 }
  node_ips   = { for index, name in sort(keys(var.nodes)) : name => cidrhost(var.subnet_cidr, 10 + index) }
  edge_nodes = { for name, node in var.nodes : name => node if node.role == "edge" }
}
resource "ovh_cloud_project_network_private" "application" {
  service_name = var.project_id
  name         = local.name
  regions      = [var.region]
}
resource "openstack_networking_subnet_v2" "application" {
  name            = local.name
  network_id      = ovh_cloud_project_network_private.application.regions_openstack_ids[var.region]
  cidr            = var.subnet_cidr
  ip_version      = 4
  enable_dhcp     = true
  dns_nameservers = var.dns_servers
  allocation_pool {
    start = cidrhost(var.subnet_cidr, 100)
    end   = cidrhost(var.subnet_cidr, 200)
  }
}
resource "openstack_networking_router_v2" "egress" {
  name                = local.name
  external_network_id = var.external_network_id
  enable_snat         = true
}
resource "openstack_networking_router_interface_v2" "application" {
  router_id = openstack_networking_router_v2.egress.id
  subnet_id = openstack_networking_subnet_v2.application.id
}
resource "openstack_networking_secgroup_v2" "node" {
  for_each             = var.nodes
  name                 = "${local.name}-${each.key}"
  description          = "Private ${each.value.role}; no default internet ingress or egress"
  delete_default_rules = true
}
resource "openstack_networking_secgroup_rule_v2" "application" {
  for_each          = var.nodes
  security_group_id = openstack_networking_secgroup_v2.node[each.key].id
  direction         = "ingress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  port_range_min    = local.ports[each.value.role]
  port_range_max    = local.ports[each.value.role]
  remote_ip_prefix  = var.subnet_cidr
}
resource "openstack_networking_secgroup_rule_v2" "ssh" {
  for_each          = { for pair in setproduct(keys(var.nodes), var.admin_cidrs) : "${pair[0]}:${pair[1]}" => { node = pair[0], cidr = pair[1] } }
  security_group_id = openstack_networking_secgroup_v2.node[each.value.node].id
  direction         = "ingress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  port_range_min    = 22
  port_range_max    = 22
  remote_ip_prefix  = each.value.cidr
}
resource "openstack_networking_secgroup_rule_v2" "egress" {
  for_each          = { for pair in setproduct(keys(var.nodes), var.egress_tcp_ports) : "${pair[0]}:${pair[1]}" => { node = pair[0], port = pair[1] } }
  security_group_id = openstack_networking_secgroup_v2.node[each.value.node].id
  direction         = "egress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  port_range_min    = each.value.port
  port_range_max    = each.value.port
  remote_ip_prefix  = "0.0.0.0/0"
}
resource "openstack_networking_secgroup_rule_v2" "private_egress" {
  for_each          = var.nodes
  security_group_id = openstack_networking_secgroup_v2.node[each.key].id
  direction         = "egress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  remote_ip_prefix  = var.subnet_cidr
}
resource "openstack_networking_secgroup_rule_v2" "dns" {
  for_each          = { for pair in setproduct(keys(var.nodes), var.dns_servers, ["tcp", "udp"]) : join(":", pair) => { node = pair[0], ip = pair[1], protocol = pair[2] } }
  security_group_id = openstack_networking_secgroup_v2.node[each.value.node].id
  direction         = "egress"
  ethertype         = "IPv4"
  protocol          = each.value.protocol
  port_range_min    = 53
  port_range_max    = 53
  remote_ip_prefix  = "${each.value.ip}/32"
}
resource "openstack_networking_port_v2" "node" {
  for_each           = var.nodes
  name               = "${local.name}-${each.key}"
  network_id         = openstack_networking_subnet_v2.application.network_id
  security_group_ids = [openstack_networking_secgroup_v2.node[each.key].id]
  fixed_ip {
    subnet_id  = openstack_networking_subnet_v2.application.id
    ip_address = local.node_ips[each.key]
  }
}

resource "openstack_networking_secgroup_rule_v2" "ntp" {
  for_each          = { for pair in setproduct(keys(var.nodes), var.ntp_servers) : join(":", pair) => { node = pair[0], ip = pair[1] } }
  security_group_id = openstack_networking_secgroup_v2.node[each.value.node].id
  direction         = "egress"
  ethertype         = "IPv4"
  protocol          = "udp"
  port_range_min    = 123
  port_range_max    = 123
  remote_ip_prefix  = "${each.value.ip}/32"
}
