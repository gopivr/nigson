mock_provider "ovh" {
  mock_resource "ovh_cloud_project_network_private" {
    defaults = { regions_openstack_ids = { GRA9 = "00000000-0000-0000-0000-000000000001" } }
  }
}
mock_provider "openstack" {}
variables {
  environment         = "stage"
  project_id          = "00000000000000000000000000000001"
  region              = "GRA9"
  external_network_id = "00000000-0000-0000-0000-000000000002"
  image_id            = "00000000-0000-0000-0000-000000000003"
  ssh_public_key      = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIFixtureOnlyNotForRealAuthentication"
  admin_cidrs         = ["192.0.2.10/32"]
  ntp_servers         = ["192.0.2.123"]
  dns_servers         = ["192.0.2.53"]
  nodes = {
    edge-1        = { role = "edge", flavor_id = "synthetic-flavor" }
    commerce-1    = { role = "commerce", flavor_id = "synthetic-flavor" }
    experience-1  = { role = "experience", flavor_id = "synthetic-flavor" }
    integration-1 = { role = "integration", flavor_id = "synthetic-flavor" }
    identity-1    = { role = "identity", flavor_id = "synthetic-flavor" }
  }
  workloads = {
    edge        = { image = "synthetic.invalid/edge@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", health_command = ["CMD", "health-probe"], memory_mb = 512 }
    commerce    = { image = "synthetic.invalid/commerce@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", health_command = ["CMD", "health-probe"], memory_mb = 2048 }
    experience  = { image = "synthetic.invalid/experience@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", health_command = ["CMD", "health-probe"], memory_mb = 2048 }
    integration = { image = "synthetic.invalid/integration@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", health_command = ["CMD", "health-probe"], memory_mb = 2048 }
    identity    = { image = "synthetic.invalid/identity@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", health_command = ["CMD", "health-probe"], memory_mb = 2048 }
  }
  database       = { region = "GRA9", version = "18", plan = "essential", flavor = "synthetic-db", node_count = 1, capabilities_verified = true }
  storage_region = "GRA"
  bucket_names   = ["synthetic-nigson-private"]
  registry       = { region = "GRA", plan_id = "00000000-0000-0000-0000-000000000004" }
}
run "private_platform" {
  command = plan
  assert {
    condition     = length(openstack_compute_instance_v2.node) == 5 && length(openstack_networking_floatingip_v2.public) > 0 && length(openstack_lb_member_v2.edge) == 1
    error_message = "Private workload VMs must use one public load balancer with edge targets only."
  }
  assert {
    condition     = alltrue([for rule in openstack_networking_secgroup_rule_v2.application : rule.remote_ip_prefix == var.subnet_cidr]) && alltrue([for sg in openstack_networking_secgroup_v2.node : sg.delete_default_rules])
    error_message = "Workload ports must remain private and default security-group rules removed."
  }
  assert {
    condition     = length(ovh_cloud_project_database.postgres) == 2 && ovh_cloud_project_database_database.application["identity"].name == "keycloak"
    error_message = "Keycloak requires its own managed database cluster."
  }
  assert {
    condition     = alltrue([for bucket in ovh_cloud_project_storage.private : bucket.encryption.sse_algorithm == "AES256" && bucket.versioning.status == "enabled"])
    error_message = "Private data buckets need encryption and versioning."
  }
  assert {
    condition     = alltrue([for name, manifest in output.compose_files : jsondecode(manifest).services[var.nodes[name].role].read_only && jsondecode(manifest).services[var.nodes[name].role].env_file == ["/etc/nigson/runtime.env"]])
    error_message = "Container files must be read-only and load runtime secrets outside Terraform."
  }
}
run "reject_public_admin_ssh" {
  command = plan
  variables { admin_cidrs = ["0.0.0.0/0"] }
  expect_failures = [var.admin_cidrs]
}
run "reject_unverified_database" {
  command = plan
  variables { database = { region = "GRA9", version = "18", plan = "essential", flavor = "synthetic-db", node_count = 1, capabilities_verified = false } }
  expect_failures = [var.database]
}
run "reject_single_replica_production" {
  command = plan
  variables {
    environment = "prod"
    database    = { region = "GRA9", version = "18", plan = "business", flavor = "synthetic-db", node_count = 2, capabilities_verified = true }
  }
  expect_failures = [openstack_compute_instance_v2.node]
}
run "reject_public_application_network" {
  command = plan
  variables { subnet_cidr = "8.8.8.0/24" }
  expect_failures = [var.subnet_cidr]
}

run "reject_mutable_images" {
  command = plan
  variables {
    workloads = { for role in ["edge", "commerce", "experience", "integration", "identity"] : role => {
      image = "synthetic.invalid/${role}:latest", health_command = ["CMD", "health-probe"], memory_mb = 2048
    } }
  }
  expect_failures = [var.workloads]
}
run "production_replicas" {
  command = plan
  variables {
    environment = "prod"
    database    = { region = "GRA9", version = "18", plan = "business", flavor = "synthetic-db", node_count = 2, capabilities_verified = true }
    nodes = {
      edge-1        = { role = "edge", flavor_id = "synthetic-flavor" }
      edge-2        = { role = "edge", flavor_id = "synthetic-flavor" }
      commerce-1    = { role = "commerce", flavor_id = "synthetic-flavor" }
      commerce-2    = { role = "commerce", flavor_id = "synthetic-flavor" }
      experience-1  = { role = "experience", flavor_id = "synthetic-flavor" }
      experience-2  = { role = "experience", flavor_id = "synthetic-flavor" }
      integration-1 = { role = "integration", flavor_id = "synthetic-flavor" }
      integration-2 = { role = "integration", flavor_id = "synthetic-flavor" }
      identity-1    = { role = "identity", flavor_id = "synthetic-flavor" }
      identity-2    = { role = "identity", flavor_id = "synthetic-flavor" }
    }
  }
  assert {
    condition     = length(openstack_lb_member_v2.edge) == 2 && length(openstack_compute_instance_v2.node) == 10
    error_message = "Production requires multiple edge targets and per-role replicas."
  }
}
