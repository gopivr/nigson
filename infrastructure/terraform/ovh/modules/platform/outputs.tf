output "public_ip" { value = openstack_networking_floatingip_v2.public.address }
output "private_hosts" {
  value = { for name, node in var.nodes : name => { address = local.node_ips[name], role = node.role, port = local.ports[node.role] } }
}
output "database_endpoints" {
  value = { for name, cluster in ovh_cloud_project_database.postgres : name => cluster.endpoints }
}
output "database_ids" { value = { for name, cluster in ovh_cloud_project_database.postgres : name => cluster.id } }
output "registry_url" { value = ovh_cloud_project_containerregistry.images.url }
output "bucket_names" { value = var.bucket_names }
output "bootstrap_sql" { value = module.owner_bootstrap.bootstrap_sql }
output "activation_requirements" {
  value = [
    "Private VPN/routed administrator access is required; no VM has a public IP.",
    "Upload role-specific compose.yaml with immutable images and root-only runtime.env/secrets to /etc/nigson; then start nigson.service.",
    "Edge containers must terminate TLS, serve the frontend and route approved public API/Keycloak paths; block /internal and Keycloak admin paths.",
    "Bootstrap owner roles and pgvector; run owner migrations with separate migrator credentials before starting application containers.",
    "Configure genuine AWS SNS/SQS, KMS, storage/workload credentials and telemetry until those application integrations are ported; no emulation is provisioned.",
    "Production approval requires backups/restore verification, TLS, monitoring and genuine provider acceptance."
  ]
}

output "compose_files" {
  description = "Non-secret Docker Compose documents, one per VM. Upload after bootstrap and inject runtime.env/secrets separately."
  value = { for name, node in var.nodes : name => jsonencode({
    services = { (node.role) = {
      image        = var.workloads[node.role].image
      restart      = "unless-stopped"
      init         = true
      read_only    = true
      cap_drop     = ["ALL"]
      security_opt = ["no-new-privileges:true"]
      pids_limit   = 256
      mem_limit    = "${var.workloads[node.role].memory_mb}m"
      env_file     = ["/etc/nigson/runtime.env"]
      ports        = ["${local.node_ips[name]}:${local.ports[node.role]}:${local.ports[node.role]}"]
      volumes      = ["/etc/nigson/secrets:/run/secrets:ro", "/etc/nigson/config:/etc/nigson/config:ro"]
      tmpfs        = ["/tmp:rw,noexec,nosuid,size=256m"]
      healthcheck  = { test = var.workloads[node.role].health_command, interval = "30s", timeout = "5s", retries = 3, start_period = "60s" }
      logging      = { driver = "json-file", options = { max-size = "10m", max-file = "3" } }
    } }
  }) }
}

output "frontend_network" {
  value = {
    network_id          = openstack_networking_subnet_v2.application.network_id
    subnet_id           = openstack_networking_subnet_v2.application.id
    subnet_cidr         = var.subnet_cidr
    external_network_id = var.external_network_id
    ssh_key_pair        = openstack_compute_keypair_v2.deployment.name
  }
}
