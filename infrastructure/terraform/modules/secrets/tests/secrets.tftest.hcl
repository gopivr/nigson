mock_provider "aws" {}

variables {
  environment                 = "stage"
  policy_revision             = "secrets-v1"
  key_administrator_role_arns = ["arn:aws:iam::123456789012:role/nigson-kms-admin"]
  secret_recovery_window_days = 14
  product_phase               = 1
  secret_names = {
    commerce    = { paystack_test = "nigson/stage/commerce/paystack-test" }
    experience  = {}
    integration = { twilio_auth = "nigson/stage/integration/twilio-auth" }
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
    condition     = length(aws_secretsmanager_secret.owned) == 8
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
      commerce    = {}
      experience  = {}
      integration = {}
      identity = {
        database_password  = "nigson/stage/identity/database-password"
        tls_certificate    = "nigson/stage/identity/tls-certificate"
        tls_key            = "nigson/stage/identity/tls-key"
        database_ca        = "nigson/stage/identity/database-ca"
        bootstrap_username = "nigson/stage/identity/bootstrap-username"
        bootstrap_password = "nigson/stage/identity/bootstrap-password"
      }
      ai = { provider = "nigson/stage/ai/provider" }
    }
  }
  expect_failures = [var.secret_names]
}
