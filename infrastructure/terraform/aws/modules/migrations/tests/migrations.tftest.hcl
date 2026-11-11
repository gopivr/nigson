mock_provider "aws" {
  mock_data "aws_region" { defaults = { region = "eu-west-1" } }
  mock_resource "aws_iam_role" { defaults = { arn = "arn:aws:iam::123456789012:role/synthetic-migration" } }
  mock_resource "aws_cloudwatch_log_group" { defaults = { arn = "arn:aws:logs:eu-west-1:123456789012:log-group:synthetic-migration" } }
  mock_resource "aws_ecs_task_definition" { defaults = { arn = "arn:aws:ecs:eu-west-1:123456789012:task-definition/synthetic-migration:1" } }
}

variables {
  environment                 = "stage"
  region                      = "eu-west-1"
  product_phase               = 1
  migration_release           = "BP1"
  image_digest                = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  architecture                = "ARM64"
  database_address            = "nigson-stage-commerce.abc123.eu-west-1.rds.amazonaws.com"
  migration_security_group_id = "sg-migrations"
  kms_key_arn                 = "arn:aws:kms:eu-west-1:123456789012:key/01234567-89ab-cdef-0123-456789abcdef"
  log_retention_days          = 30
  commerce_repository_arn     = "arn:aws:ecr:eu-west-1:123456789012:repository/nigson/stage/commerce"
  commerce_repository_url     = "123456789012.dkr.ecr.eu-west-1.amazonaws.com/nigson/stage/commerce"
  migration_password_secret_arns = {
    commerce    = "arn:aws:secretsmanager:eu-west-1:123456789012:secret:nigson/stage/commerce/migration-password-AbCdEf"
    experience  = "arn:aws:secretsmanager:eu-west-1:123456789012:secret:nigson/stage/experience/migration-password-AbCdEf"
    integration = "arn:aws:secretsmanager:eu-west-1:123456789012:secret:nigson/stage/integration/migration-password-AbCdEf"
  }
}

run "define_private_owner_migration_tasks" {
  command = apply
  assert {
    condition     = length(aws_ecs_task_definition.migration) == 3 && aws_ecs_task_definition.migration["commerce"].network_mode == "awsvpc" && toset(aws_ecs_task_definition.migration["commerce"].requires_compatibilities) == toset(["FARGATE"])
    error_message = "One Fargate one-shot task per active owner is required."
  }
  assert {
    condition     = jsondecode(aws_ecs_task_definition.migration["commerce"].container_definitions)[0].image == "123456789012.dkr.ecr.eu-west-1.amazonaws.com/nigson/stage/commerce@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" && jsondecode(aws_ecs_task_definition.migration["commerce"].container_definitions)[0].readonlyRootFilesystem
    error_message = "Migration tasks must use the immutable migration artifact and a read-only root filesystem."
  }
  assert {
    condition     = one([for item in jsondecode(aws_ecs_task_definition.migration["commerce"].container_definitions)[0].environment : item.value if item.name == "MIGRATION_JDBC_URL"]) == "jdbc:postgresql://nigson-stage-commerce.abc123.eu-west-1.rds.amazonaws.com:5432/nigson?sslmode=verify-full" && one(jsondecode(aws_ecs_task_definition.migration["commerce"].container_definitions)[0].secrets).valueFrom == var.migration_password_secret_arns.commerce
    error_message = "Migration target must be exact private RDS with verified TLS and one owner credential."
  }
  assert {
    condition     = jsondecode(aws_iam_role_policy.execution["commerce"].policy).Statement[3].Resource == [var.migration_password_secret_arns.commerce] && jsondecode(aws_iam_role_policy.execution["commerce"].policy).Statement[4].Resource == var.kms_key_arn
    error_message = "Each migration execution role may read only its owner's migration secret."
  }
}

run "include_ai_only_at_bp5" {
  command = plan
  variables {
    product_phase     = 5
    migration_release = "BP5"
    migration_password_secret_arns = {
      commerce    = "arn:aws:secretsmanager:eu-west-1:123456789012:secret:nigson/stage/commerce/migration-password-AbCdEf"
      experience  = "arn:aws:secretsmanager:eu-west-1:123456789012:secret:nigson/stage/experience/migration-password-AbCdEf"
      integration = "arn:aws:secretsmanager:eu-west-1:123456789012:secret:nigson/stage/integration/migration-password-AbCdEf"
      ai          = "arn:aws:secretsmanager:eu-west-1:123456789012:secret:nigson/stage/ai/migration-password-AbCdEf"
    }
  }
  assert {
    condition     = contains(keys(aws_ecs_task_definition.migration), "ai")
    error_message = "The AI migration task is available only at BP5."
  }
}

run "reject_future_schema_release" {
  command = plan
  variables { migration_release = "BP2" }
  expect_failures = [var.migration_release]
}
