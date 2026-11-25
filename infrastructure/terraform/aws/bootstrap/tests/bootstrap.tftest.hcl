mock_provider "aws" {}
variables {
  expected_account_id = "123456789012"
  region              = "eu-west-1"
  domain_name         = "nigson.com"
}
run "stage_bootstrap_has_no_resource_prerequisites" {
  command = plan
  assert {
    condition     = aws_route53_zone.environment.name == "stage.nigson.com"
    error_message = "Stage must use a delegated subdomain, preserving existing apex/email DNS."
  }
  assert {
    condition     = aws_s3_bucket_public_access_block.state.block_public_acls && aws_s3_bucket_public_access_block.state.block_public_policy && aws_s3_bucket_public_access_block.state.ignore_public_acls && aws_s3_bucket_public_access_block.state.restrict_public_buckets
    error_message = "Terraform state must not be public."
  }
  assert {
    condition     = aws_s3_bucket_versioning.state.versioning_configuration[0].status == "Enabled" && alltrue([for rule in aws_s3_bucket_server_side_encryption_configuration.state.rule : alltrue([for encryption in rule.apply_server_side_encryption_by_default : encryption.sse_algorithm == "AES256"])])
    error_message = "State requires encryption and version recovery."
  }
}
run "invalid_account_is_refused" {
  command = plan
  variables { expected_account_id = "not-an-account" }
  expect_failures = [var.expected_account_id]
}
