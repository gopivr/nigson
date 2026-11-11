terraform {
  required_version = "= 1.12.2"
  required_providers {
    aws = { source = "hashicorp/aws", version = "6.15.0" }
  }
}

data "aws_region" "current" {}

locals {
  tags = {
    Application = "nigson"
    Environment = var.environment
    ManagedBy   = "terraform"
    Workload    = "one-shot-owner-migration"
    Release     = var.migration_release
  }
}

resource "aws_cloudwatch_log_group" "migration" {
  name              = "/nigson/${var.environment}/owner-migrations"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
  tags              = local.tags
}

resource "aws_iam_role" "execution" {
  for_each           = var.migration_password_secret_arns
  name               = "nigson-${var.environment}-${each.key}-migration-execution"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole" }] })
  tags               = merge(local.tags, { Owner = each.key, Role = "migration-execution" })
}

resource "aws_iam_role_policy" "execution" {
  for_each = var.migration_password_secret_arns
  name     = "nigson-${var.environment}-${each.key}-migration-execution"
  role     = aws_iam_role.execution[each.key].id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage"], Resource = var.commerce_repository_arn },
    { Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = "${aws_cloudwatch_log_group.migration.arn}:*" },
    { Effect = "Allow", Action = ["secretsmanager:GetSecretValue"], Resource = [each.value] },
    { Effect = "Allow", Action = ["kms:Decrypt"], Resource = var.kms_key_arn, Condition = { StringEquals = { "kms:ViaService" = "secretsmanager.${data.aws_region.current.region}.amazonaws.com" }, "ForAnyValue:StringEquals" = { "kms:EncryptionContext:SecretARN" = [each.value] } } }
  ] })
}

resource "aws_iam_role" "task" {
  for_each           = var.migration_password_secret_arns
  name               = "nigson-${var.environment}-${each.key}-migration-task"
  assume_role_policy = aws_iam_role.execution[each.key].assume_role_policy
  tags               = merge(local.tags, { Owner = each.key, Role = "migration-task" })
}

resource "aws_ecs_task_definition" "migration" {
  for_each                 = var.migration_password_secret_arns
  family                   = "nigson-${var.environment}-${each.key}-migration-${var.migration_release}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "1024"
  memory                   = "2048"
  execution_role_arn       = aws_iam_role.execution[each.key].arn
  task_role_arn            = aws_iam_role.task[each.key].arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.architecture
  }
  volume { name = "tmp" }
  container_definitions = jsonencode([{
    name                   = "owner-migration"
    image                  = "${var.commerce_repository_url}@${var.image_digest}"
    essential              = true
    user                   = "10001:10001"
    readonlyRootFilesystem = true
    stopTimeout            = 120
    environment = [for name, value in {
      APP_ENV                 = var.environment, MIGRATION_OWNER = each.key, MIGRATION_RELEASE = var.migration_release,
      MIGRATION_EXPECTED_HOST = var.database_address,
      MIGRATION_JDBC_URL      = "jdbc:postgresql://${var.database_address}:5432/nigson?sslmode=verify-full",
      MIGRATION_ROOT          = "/app/source"
    } : { name = name, value = value }]
    secrets          = [{ name = "MIGRATION_PASSWORD", valueFrom = each.value }]
    mountPoints      = [{ sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }]
    linuxParameters  = { capabilities = { drop = ["ALL"] }, initProcessEnabled = true }
    logConfiguration = { logDriver = "awslogs", options = { awslogs-group = aws_cloudwatch_log_group.migration.name, awslogs-region = data.aws_region.current.region, awslogs-stream-prefix = each.key } }
  }])
  tags = merge(local.tags, { Owner = each.key })
}

output "task_definition_arns" { value = { for owner, task in aws_ecs_task_definition.migration : owner => task.arn } }
output "execution_role_arns" { value = { for owner, role in aws_iam_role.execution : owner => role.arn } }
output "task_role_arns" { value = { for owner, role in aws_iam_role.task : owner => role.arn } }
