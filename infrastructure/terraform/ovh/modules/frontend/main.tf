terraform {
  required_version = ">= 1.12.2, < 2.0.0"
  required_providers { openstack = { source = "terraform-provider-openstack/openstack", version = "3.4.0" } }
}
locals { name = "nigson-${var.environment}-frontend" }
resource "openstack_networking_secgroup_v2" "frontend" {
  name                 = local.name
  delete_default_rules = true
}
resource "openstack_networking_secgroup_rule_v2" "https" {
  security_group_id = openstack_networking_secgroup_v2.frontend.id
  direction         = "ingress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  port_range_min    = 443
  port_range_max    = 443
  remote_ip_prefix  = var.subnet_cidr
}
resource "openstack_networking_secgroup_rule_v2" "ssh" {
  for_each          = var.admin_cidrs
  security_group_id = openstack_networking_secgroup_v2.frontend.id
  direction         = "ingress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  port_range_min    = 22
  port_range_max    = 22
  remote_ip_prefix  = each.key
}
resource "openstack_networking_secgroup_rule_v2" "outbound" {
  for_each          = toset(["80", "443"])
  security_group_id = openstack_networking_secgroup_v2.frontend.id
  direction         = "egress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  port_range_min    = tonumber(each.key)
  port_range_max    = tonumber(each.key)
  remote_ip_prefix  = "0.0.0.0/0"
}
resource "openstack_networking_secgroup_rule_v2" "dns" {
  for_each          = { for pair in setproduct(var.dns_servers, ["tcp", "udp"]) : join(":", pair) => { ip = pair[0], protocol = pair[1] } }
  security_group_id = openstack_networking_secgroup_v2.frontend.id
  direction         = "egress"
  ethertype         = "IPv4"
  protocol          = each.value.protocol
  port_range_min    = 53
  port_range_max    = 53
  remote_ip_prefix  = "${each.value.ip}/32"
}
resource "openstack_networking_secgroup_rule_v2" "ntp" {
  for_each          = var.ntp_servers
  security_group_id = openstack_networking_secgroup_v2.frontend.id
  direction         = "egress"
  ethertype         = "IPv4"
  protocol          = "udp"
  port_range_min    = 123
  port_range_max    = 123
  remote_ip_prefix  = "${each.key}/32"
}
resource "openstack_networking_port_v2" "frontend" {
  count              = var.replicas
  name               = "${local.name}-${count.index + 1}"
  network_id         = var.network_id
  security_group_ids = [openstack_networking_secgroup_v2.frontend.id]
  fixed_ip { subnet_id = var.subnet_id }
}
resource "openstack_compute_instance_v2" "frontend" {
  count        = var.replicas
  name         = "${local.name}-${count.index + 1}"
  image_id     = var.image_id
  flavor_id    = var.flavor_id
  key_pair     = var.ssh_key_pair
  config_drive = true
  user_data = templatefile("${path.module}/host.yaml.tftpl", {
    image        = var.frontend_image
    private_ip   = openstack_networking_port_v2.frontend[count.index].all_fixed_ips[0]
    ntp_servers  = var.ntp_servers
    nginx_config = indent(6, templatefile("${path.module}/../../../shared/frontend/nginx.conf.tftpl", { backend = var.backend_origin_hostname, domain = var.domain_name }))
  })
  network { port = openstack_networking_port_v2.frontend[count.index].id }
  metadata = { application = "nigson", environment = var.environment, role = "frontend" }
}
resource "openstack_lb_loadbalancer_v2" "frontend" {
  name          = local.name
  vip_subnet_id = var.subnet_id
}
resource "openstack_networking_floatingip_v2" "frontend" {
  pool    = var.external_network_id
  port_id = openstack_lb_loadbalancer_v2.frontend.vip_port_id
}
resource "openstack_lb_listener_v2" "https" {
  name            = "${local.name}-https"
  protocol        = "TCP"
  protocol_port   = 443
  loadbalancer_id = openstack_lb_loadbalancer_v2.frontend.id
}
resource "openstack_lb_pool_v2" "frontend" {
  protocol    = "TCP"
  lb_method   = "ROUND_ROBIN"
  listener_id = openstack_lb_listener_v2.https.id
}
resource "openstack_lb_member_v2" "frontend" {
  count         = var.replicas
  pool_id       = openstack_lb_pool_v2.frontend.id
  subnet_id     = var.subnet_id
  address       = openstack_networking_port_v2.frontend[count.index].all_fixed_ips[0]
  protocol_port = 443
}
resource "openstack_lb_monitor_v2" "frontend" {
  pool_id     = openstack_lb_pool_v2.frontend.id
  type        = "TCP"
  delay       = 10
  timeout     = 5
  max_retries = 3
}
output "url" { value = "https://${var.domain_name}" }
output "public_ip" { value = openstack_networking_floatingip_v2.frontend.address }
output "private_ips" { value = [for port in openstack_networking_port_v2.frontend : port.all_fixed_ips[0]] }
