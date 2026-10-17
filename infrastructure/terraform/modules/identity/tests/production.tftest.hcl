mock_provider "aws" {
  mock_resource "aws_lb" { defaults = { arn = "arn:aws:elasticloadbalancing:us-east-1:000000000000:loadbalancer/app/test/aaaaaaaa", arn_suffix = "app/test/aaaaaaaa" } }
  mock_resource "aws_lb_target_group" { defaults = { arn = "arn:aws:elasticloadbalancing:us-east-1:000000000000:targetgroup/test/aaaaaaaa", arn_suffix = "targetgroup/test/aaaaaaaa" } }
  mock_resource "aws_lb_listener" { defaults = { arn = "arn:aws:elasticloadbalancing:us-east-1:000000000000:listener/app/test/aaaaaaaa/bbbbbbbb" } }
  mock_resource "aws_iam_role" { defaults = { arn = "arn:aws:iam::000000000000:role/test" } }
  mock_resource "aws_wafv2_web_acl" { defaults = { arn = "arn:aws:wafv2:us-east-1:000000000000:regional/webacl/test/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" } }
  mock_resource "aws_ecs_task_definition" { defaults = { arn = "arn:aws:ecs:us-east-1:000000000000:task-definition/test:1" } }
  mock_resource "aws_db_instance" { defaults = { address = "identity.synthetic.test", master_user_secret = [{ secret_arn = "arn:aws:secretsmanager:us-east-1:000000000000:secret:test-master", secret_status = "active", kms_key_id = "arn:aws:kms:us-east-1:000000000000:key/test" }] } }

  mock_data "aws_region" { defaults = { region = "us-east-1" } }
}
variables {
  environment                    = "stage"
  vpc_id                         = "vpc-synthetic-test"
  vpc_cidr                       = "10.93.0.0/16"
  ecs_cluster_arn                = "arn:aws:ecs:us-east-1:000000000000:cluster/test"
  public_subnet_ids              = ["subnet-test-public-a", "subnet-test-public-b"]
  private_subnet_ids             = ["subnet-test-private-a", "subnet-test-private-b"]
  database_subnet_ids            = ["subnet-test-data-a", "subnet-test-data-b"]
  product_phase                  = 1
  owner_task_security_groups     = { commerce = "sg-test-commerce", experience = "sg-test-experience", integration = "sg-test-integration" }
  admin_cidrs                    = ["10.94.0.0/24"]
  trusted_proxy_cidrs            = ["10.93.1.0/24", "10.93.2.0/24"]
  aws_endpoint_security_group_id = "sg-test-endpoints"
  metrics_security_group_ids     = []
  public_hostname                = "identity.synthetic.test"
  admin_hostname                 = "identity-admin.synthetic.test"
  realm                          = "synthetic-test"
  public_certificate_arn         = "arn:aws:acm:us-east-1:000000000000:certificate/test-public"
  admin_certificate_arn          = "arn:aws:acm:us-east-1:000000000000:certificate/test-private"
  kms_key_arn                    = "arn:aws:kms:us-east-1:000000000000:key/synthetic-test"
  identity_image                 = "000000000000.dkr.ecr.us-east-1.amazonaws.com/nigson/stage/identity@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  cpu_architecture               = "ARM64"
  secret_refs = {
    database_password = "arn:aws:secretsmanager:us-east-1:000000000000:secret:test-db"
    tls_certificate   = "arn:aws:secretsmanager:us-east-1:000000000000:secret:test-cert"
    tls_key           = "arn:aws:secretsmanager:us-east-1:000000000000:secret:test-key"
    database_ca       = "arn:aws:secretsmanager:us-east-1:000000000000:secret:test-ca"
  }
  bootstrap_secret_refs = {
    username = "arn:aws:secretsmanager:us-east-1:000000000000:secret:test-bootstrap-user"
    password = "arn:aws:secretsmanager:us-east-1:000000000000:secret:test-bootstrap-password"
  }
  postgres_version        = "18.6"
  database_instance_class = "db.t4g.medium"
  backup_retention_days   = 7
  log_retention_days      = 7
  policy_revision         = "synthetic-test-policy"
  named_operator          = "synthetic-test-operator"
  alarm_destination_arns  = ["arn:aws:sns:us-east-1:000000000000:test-operator"]
}
run "bootstrap_closed" {
  command = apply
  assert {
    condition     = aws_ecs_service.identity.desired_count == 0 && !aws_ecs_service.identity.network_configuration[0].assign_public_ip
    error_message = "Identity must remain closed until explicit provisioning/activation."
  }
  assert {
    condition     = !aws_db_instance.identity.publicly_accessible && aws_db_instance.identity.multi_az && aws_db_instance.identity.storage_encrypted && aws_db_instance.identity.deletion_protection && !aws_db_instance.identity.auto_minor_version_upgrade
    error_message = "Dedicated identity database must be private, encrypted, protected and upgrade-controlled."
  }
  assert {
    condition     = aws_lb.admin.internal && aws_lb_listener.public.default_action[0].type == "fixed-response" && aws_lb_listener.public.default_action[0].fixed_response[0].status_code == "403" && length(aws_wafv2_web_acl.identity.default_action[0].block) == 1
    error_message = "Public identity entry must default deny; admin ingress is private."
  }
  assert {
    condition     = jsondecode(aws_ecs_task_definition.identity.container_definitions)[0].command == ["start", "--optimized"] && jsondecode(aws_ecs_task_definition.identity.container_definitions)[0].readonlyRootFilesystem && jsondecode(aws_ecs_task_definition.identity.container_definitions)[0].user == "1000:0" && length(jsondecode(aws_ecs_task_definition.identity.container_definitions)[0].secrets) == 4
    error_message = "Permanent tasks must be optimized, non-root and have no bootstrap credentials."
  }
  assert {
    condition     = length(jsondecode(aws_ecs_task_definition.bootstrap.container_definitions)[0].secrets) == 6 && jsondecode(aws_ecs_task_definition.bootstrap.container_definitions)[0].command == ["bootstrap-admin", "user"]
    error_message = "Bootstrap must be an isolated one-off task, never a permanent service mode."
  }
  assert {
    condition     = alltrue([for rule in aws_vpc_security_group_egress_rule.server : rule.referenced_security_group_id != null]) && aws_vpc_security_group_ingress_rule.cluster["7800"].from_port == 7800 && aws_vpc_security_group_ingress_rule.cluster["57800"].from_port == 57800
    error_message = "Cluster and external connections must be private and explicitly scoped."
  }
  assert {
    condition     = length(aws_vpc_security_group_ingress_rule.database) == 4 && aws_vpc_security_group_ingress_rule.database["commerce"].referenced_security_group_id == "sg-test-commerce"
    error_message = "Dedicated identity PostgreSQL must allow Keycloak and each explicitly named business owner."
  }
  assert {
    condition     = aws_ecs_task_definition.identity.runtime_platform[0].cpu_architecture == "ARM64" && aws_ecs_task_definition.bootstrap.runtime_platform[0].cpu_architecture == "ARM64"
    error_message = "Identity and bootstrap task architectures must match the explicitly selected artifact platform."
  }
}
run "two_replicas" {
  command = plan
  variables { service_enabled = true }
  assert {
    condition     = aws_ecs_service.identity.desired_count == 2 && aws_ecs_service.identity.deployment_minimum_healthy_percent == 100 && aws_ecs_service.identity.deployment_circuit_breaker[0].rollback
    error_message = "Enabled identity requires two replicas and safe replacement."
  }
}
run "select_x86_artifact_platform" {
  command = plan
  variables { cpu_architecture = "X86_64" }
  assert {
    condition     = aws_ecs_task_definition.identity.runtime_platform[0].cpu_architecture == "X86_64" && aws_ecs_task_definition.bootstrap.runtime_platform[0].cpu_architecture == "X86_64"
    error_message = "The selected artifact architecture must be used consistently by identity and bootstrap tasks."
  }
}
run "deny_public_admin_cidr" {
  command = plan
  variables { admin_cidrs = ["0.0.0.0/0"] }
  expect_failures = [var.admin_cidrs]
}
run "deny_mock_image" {
  command = plan
  variables { identity_image = "quay.io/keycloak/keycloak:latest" }
  expect_failures = [var.identity_image]
}
run "require_retention_policy" {
  command = plan
  variables { backup_retention_days = 0 }
  expect_failures = [var.backup_retention_days]
}
run "require_private_admin_hostname" {
  command = plan
  variables { admin_hostname = "identity.synthetic.test" }
  expect_failures = [var.admin_hostname]
}
run "require_proxy_sources" {
  command = plan
  variables { trusted_proxy_cidrs = ["0.0.0.0/0"] }
  expect_failures = [var.trusted_proxy_cidrs]
}
