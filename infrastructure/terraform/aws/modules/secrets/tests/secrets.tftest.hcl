mock_provider "aws" {
  mock_data "aws_caller_identity" { defaults = { account_id = "123456789012" } }
  mock_data "aws_region" { defaults = { region = "us-east-2", name = "us-east-2" } }
}

variables {
  environment                 = "stage"
  policy_revision             = "secrets-v1"
  key_administrator_role_arns = ["arn:aws:iam::123456789012:role/nigson-kms-admin"]
  secret_recovery_window_days = 14
  product_phase               = 1
  secret_names = {
    commerce = {
      paystack_test      = "nigson/stage/commerce/paystack-test"
      migration_password = "nigson/stage/commerce/migration-password"
    }
    experience = { migration_password = "nigson/stage/experience/migration-password" }
    integration = {
      twilio_auth        = "nigson/stage/integration/twilio-auth"
      migration_password = "nigson/stage/integration/migration-password"
    }
    identity = {
      database_password  = "nigson/stage/identity/database-password"
      tls_certificate    = "nigson/stage/identity/tls-certificate"
      tls_key            = "nigson/stage/identity/tls-key"
      database_ca        = "nigson/stage/identity/database-ca"
      bootstrap_username = "nigson/stage/identity/bootstrap-username"
      bootstrap_password = "nigson/stage/identity/bootstrap-password"
    }
  }
}

run "create_secret_metadata_without_values" {
  command = plan

  assert {
    condition     = length(aws_secretsmanager_secret.owned) == 11
    error_message = "Only named secret metadata should be provisioned."
  }
  assert {
    condition     = aws_kms_key.application.enable_key_rotation == true
    error_message = "The application data key must rotate."
  }
}

run "reject_ai_secrets_before_bp5" {
  command = plan
  variables {
    secret_names = {
      commerce    = { migration_password = "nigson/stage/commerce/migration-password" }
      experience  = { migration_password = "nigson/stage/experience/migration-password" }
      integration = { migration_password = "nigson/stage/integration/migration-password" }
      identity = {
        database_password  = "nigson/stage/identity/database-password"
        tls_certificate    = "nigson/stage/identity/tls-certificate"
        tls_key            = "nigson/stage/identity/tls-key"
        database_ca        = "nigson/stage/identity/database-ca"
        bootstrap_username = "nigson/stage/identity/bootstrap-username"
        bootstrap_password = "nigson/stage/identity/bootstrap-password"
      }
      ai = {
        provider           = "nigson/stage/ai/provider"
        migration_password = "nigson/stage/ai/migration-password"
      }
    }
  }
  expect_failures = [var.secret_names]
}

run "logs_key_allows_only_environment_groups_and_exact_identity_rds_export" {
  command = plan
  assert {
    condition = toset(one([for statement in jsondecode(aws_kms_key.application.policy).Statement : statement if statement.Sid == "CloudWatchLogGroupEncryption"]).Condition.ArnLike["kms:EncryptionContext:aws:logs:arn"]) == toset([
      "arn:aws:logs:us-east-2:123456789012:log-group:/nigson/stage/*",
      "arn:aws:logs:us-east-2:123456789012:log-group:/aws/rds/instance/nigson-stage-identity/postgresql"
    ])
    error_message = "KMS must authorize identity RDS logs without opening unrelated groups/accounts/environments."
  }
  assert {
    condition     = one([for statement in jsondecode(aws_kms_key.application.policy).Statement : statement if statement.Sid == "CloudWatchLogGroupEncryption"]).Principal.Service == "logs.us-east-2.amazonaws.com"
    error_message = "Only the configured regional CloudWatch Logs service may encrypt these groups."
  }
}
