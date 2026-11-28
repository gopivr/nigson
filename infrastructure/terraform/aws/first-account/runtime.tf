# State-owned security and private one-shot bootstrap tasks. No secret values enter state.
locals {
  runtime_client_secrets = {
    bff         = module.secrets.secret_arns.commerce.NIGSON_BFF_CLIENT_SECRET
    commerce    = module.secrets.secret_arns.commerce.NIGSON_COMMERCE_WORKLOAD_SECRET
    experience  = module.secrets.secret_arns.experience.NIGSON_EXPERIENCE_WORKLOAD_SECRET
    integration = module.secrets.secret_arns.integration.NIGSON_INTEGRATION_WORKLOAD_SECRET
  }
  setup_database_secrets = merge({
    SETUP_DATABASE_ADMIN_USER     = "${module.database.master_secret_arn}:username::"
    SETUP_DATABASE_ADMIN_PASSWORD = "${module.database.master_secret_arn}:password::"
    SETUP_IDENTITY_ADMIN_USER     = "${module.identity.database_admin_secret_arn}:username::"
    SETUP_IDENTITY_ADMIN_PASSWORD = "${module.identity.database_admin_secret_arn}:password::"
    SETUP_IDENTITY_OWNER_PASSWORD = module.secrets.secret_arns.identity.database_password
    SETUP_DB_CA_PEM               = module.secrets.secret_arns.identity.database_ca
    }, merge([for owner in local.names : {
      "SETUP_${upper(owner)}_MIGRATOR_PASSWORD" = module.secrets.secret_arns[owner].migration_password
      "SETUP_${upper(owner)}_APP_PASSWORD"      = module.secrets.secret_arns[owner].SPRING_DATASOURCE_PASSWORD
  }]...))
  setup_realm_secrets = merge({
    SETUP_BOOTSTRAP_USER     = module.secrets.secret_arns.identity.bootstrap_username
    SETUP_BOOTSTRAP_PASSWORD = module.secrets.secret_arns.identity.bootstrap_password
  }, { for client, arn in local.runtime_client_secrets : "SETUP_CLIENT_${upper(client)}" => arn })
  setup_modes = var.publish_task_definitions && contains(keys(var.image_digests), "operations") ? toset(["database", "realm"]) : toset([])
}
resource "aws_kms_key" "actor" {
  description              = "Nigson ${var.environment} actor delegation signing"
  key_usage                = "SIGN_VERIFY"
  customer_master_key_spec = "ECC_NIST_P256"
  deletion_window_in_days  = 30
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Principal = { AWS = "arn:aws:iam::${var.expected_account_id}:root" }, Action = "kms:*", Resource = "*" }
  ] })
  lifecycle { prevent_destroy = true }
}
resource "aws_iam_role_policy" "runtime_security" {
  name = "nigson-${var.environment}-runtime-security"
  role = split("/", module.compute.task_role_arns.commerce)[1]
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["kms:Sign"], Resource = aws_kms_key.actor.arn, Condition = { StringEquals = { "kms:SigningAlgorithm" = "ECDSA_SHA_256", "kms:MessageType" = "DIGEST" } } },
    { Effect = "Allow", Action = ["kms:Decrypt"], Resource = module.secrets.kms_key_arn, Condition = { StringEquals = { "kms:EncryptionContext:purpose" = "nigson-commerce-session" }, Null = { "kms:EncryptionContext:revision" = "false" } } }
  ] })
}
resource "aws_iam_role" "setup_execution" {
  for_each             = local.setup_modes
  name                 = "${local.prefix}-${each.key}-setup-execution"
  permissions_boundary = "arn:aws:iam::${var.expected_account_id}:policy/nigson-${var.environment}-runtime-task-boundary"
  assume_role_policy   = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole" }] })
}
resource "aws_iam_role" "setup_task" {
  for_each             = local.setup_modes
  name                 = "${local.prefix}-${each.key}-setup-task"
  permissions_boundary = "arn:aws:iam::${var.expected_account_id}:policy/nigson-${var.environment}-runtime-task-boundary"
  assume_role_policy   = aws_iam_role.setup_execution[each.key].assume_role_policy
}
resource "aws_iam_role_policy" "setup_execution" {
  for_each = local.setup_modes
  role     = aws_iam_role.setup_execution[each.key].id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"], Resource = "arn:aws:ecr:${var.region}:${var.expected_account_id}:repository/nigson/${var.environment}/identity" },
    { Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = "arn:aws:logs:${var.region}:${var.expected_account_id}:log-group:/nigson/${var.environment}/commerce:*" },
    { Effect = "Allow", Action = ["secretsmanager:GetSecretValue"], Resource = each.key == "database" ? concat([module.database.master_secret_arn, module.identity.database_admin_secret_arn], [for arn in values(local.setup_database_secrets) : arn if !endswith(arn, "::")]) : values(local.setup_realm_secrets) },
    { Effect = "Allow", Action = ["kms:Decrypt"], Resource = module.secrets.kms_key_arn, Condition = { StringEquals = { "kms:ViaService" = "secretsmanager.${var.region}.amazonaws.com" } } }
  ] })
}
resource "aws_ecs_task_definition" "setup" {
  for_each                 = local.setup_modes
  family                   = "${local.prefix}-${each.key}-setup"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.setup_execution[each.key].arn
  task_role_arn            = aws_iam_role.setup_task[each.key].arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }
  volume { name = "tmp" }
  container_definitions = jsonencode([{
    name = "runtime-setup", image = "${local.registry}/nigson/${var.environment}/identity@${var.image_digests.operations}", essential = true,
    user = "10001:10001", readonlyRootFilesystem = true, command = ["bootstrap_runtime.py"],
    environment = [for name, value in merge({ APP_ENV = var.environment, SETUP_MODE = each.key }, each.key == "database" ? {
      SETUP_DATABASE_HOST = module.database.database_address, SETUP_IDENTITY_DATABASE_HOST = module.identity.dedicated_database_address, SETUP_OWNERS = jsonencode(local.names)
    } : { SETUP_ADMIN_ORIGIN = "https://identity-admin.${var.zone_name}" }) : { name = name, value = value }],
    secrets          = [for name, arn in(each.key == "database" ? local.setup_database_secrets : local.setup_realm_secrets) : { name = name, valueFrom = arn }],
    mountPoints      = [{ sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }],
    linuxParameters  = { capabilities = { drop = ["ALL"] }, initProcessEnabled = true },
    logConfiguration = { logDriver = "awslogs", options = { awslogs-group = "/nigson/${var.environment}/commerce", awslogs-region = var.region, awslogs-stream-prefix = "${each.key}-setup" } }
  }])
}
# Split-horizon auth DNS keeps backend token/JWKS calls on the internal HTTPS ALB.
resource "aws_route53_zone" "identity_backchannel" {
  name = "auth.${var.zone_name}"
  vpc { vpc_id = module.network.vpc_id }
}
resource "aws_route53_record" "identity_backchannel" {
  zone_id = aws_route53_zone.identity_backchannel.zone_id
  name    = "auth.${var.zone_name}"
  type    = "A"
  alias {
    name                   = module.identity.private_admin_alb_dns_name
    zone_id                = module.identity.private_admin_alb_zone_id
    evaluate_target_health = false
  }
}
resource "aws_vpc_security_group_egress_rule" "owner_identity" {
  for_each                     = module.compute.service_security_group_ids
  security_group_id            = each.value
  referenced_security_group_id = module.identity.admin_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
}
resource "aws_vpc_security_group_egress_rule" "setup_identity_database" {
  security_group_id            = module.database.migration_task_security_group_id
  referenced_security_group_id = module.identity.database_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}
resource "aws_vpc_security_group_ingress_rule" "setup_identity_database" {
  security_group_id            = module.identity.database_security_group_id
  referenced_security_group_id = module.database.migration_task_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}
resource "aws_vpc_security_group_egress_rule" "setup_identity_admin" {
  security_group_id            = module.database.migration_task_security_group_id
  referenced_security_group_id = module.identity.admin_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
}
