resource "openstack_compute_keypair_v2" "deployment" {
  name       = local.name
  public_key = var.ssh_public_key
}
resource "openstack_compute_instance_v2" "node" {
  for_each     = var.nodes
  name         = "${local.name}-${each.key}"
  image_id     = var.image_id
  flavor_id    = each.value.flavor_id
  key_pair     = openstack_compute_keypair_v2.deployment.name
  config_drive = true
  user_data    = templatefile("${path.module}/host.yaml.tftpl", { role = each.value.role, ntp_servers = var.ntp_servers })
  metadata     = { application = "nigson", environment = var.environment, role = each.value.role }
  network {
    port = openstack_networking_port_v2.node[each.key].id
  }
  depends_on = [openstack_networking_router_interface_v2.application]
  lifecycle {
    precondition {
      condition     = contains(keys(var.workloads), each.value.role)
      error_message = "Every VM role requires a configured immutable workload image and health check."
    }
    precondition {
      condition     = var.environment != "prod" || alltrue([for role in ["edge", "commerce", "experience", "integration", "identity"] : length([for node in values(var.nodes) : node if node.role == role]) >= 2])
      error_message = "Production requires at least two VMs for each mandatory role."
    }
  }
}
resource "openstack_lb_loadbalancer_v2" "public" {
  name          = local.name
  vip_subnet_id = openstack_networking_subnet_v2.application.id
  depends_on    = [openstack_networking_router_interface_v2.application]
}
resource "openstack_networking_floatingip_v2" "public" {
  pool    = var.external_network_id
  port_id = openstack_lb_loadbalancer_v2.public.vip_port_id
}
resource "openstack_lb_listener_v2" "https" {
  name            = "${local.name}-https"
  protocol        = "TCP"
  protocol_port   = 443
  loadbalancer_id = openstack_lb_loadbalancer_v2.public.id
}
resource "openstack_lb_pool_v2" "edge" {
  name        = "${local.name}-edge"
  protocol    = "TCP"
  lb_method   = "ROUND_ROBIN"
  listener_id = openstack_lb_listener_v2.https.id
}
resource "openstack_lb_member_v2" "edge" {
  for_each      = local.edge_nodes
  pool_id       = openstack_lb_pool_v2.edge.id
  subnet_id     = openstack_networking_subnet_v2.application.id
  address       = local.node_ips[each.key]
  protocol_port = 443
}
resource "openstack_lb_monitor_v2" "edge" {
  pool_id     = openstack_lb_pool_v2.edge.id
  type        = "TCP"
  delay       = 10
  timeout     = 5
  max_retries = 3
}
