mock_provider "aws" {
  mock_resource "aws_kms_key" { defaults = { arn = "arn:aws:kms:eu-west-1:000000000000:key/12345678-1234-1234-1234-123456789012" } }
}
variables {
  environment                 = "stage"
  product_phase               = 1
  policy_revision             = "synthetic-test"
  key_administrator_arns      = ["arn:aws:iam::000000000000:role/test-security"]
  commerce_task_role_arn      = "arn:aws:iam::000000000000:role/test-commerce"
  commerce_task_role_name     = "test-commerce"
  session_key_provisioner_arn = "arn:aws:iam::000000000000:role/test-provisioner"
  key_deletion_window_days    = 30
  private_hops = {
    commerce-integration = { source_security_group_id = "sg-test-commerce", target_security_group_id = "sg-test-integration", target_port = 8083 }
  }
}
run "separate_keys_and_least_privilege" {
  command = plan
  override_resource {
    override_during = plan
    target          = aws_kms_key.actor
    values          = { arn = "arn:aws:kms:eu-west-1:000000000000:key/12345678-1234-1234-1234-123456789012" }
  }
  override_resource {
    override_during = plan
    target          = aws_kms_key.session
    values          = { arn = "arn:aws:kms:eu-west-1:000000000000:key/22345678-1234-1234-1234-123456789012" }
  }
  assert {
    condition     = aws_kms_key.actor.key_usage == "SIGN_VERIFY" && aws_kms_key.actor.customer_master_key_spec == "ECC_NIST_P256" && aws_kms_key.session.enable_key_rotation
    error_message = "Actor signing and symmetric session encryption must use separate keys."
  }
  assert {
    condition     = jsondecode(aws_iam_role_policy.commerce.policy).Statement[0].Action == ["kms:Sign"] && jsondecode(aws_iam_role_policy.commerce.policy).Statement[1].Action == ["kms:Decrypt"]
    error_message = "Runtime must not encrypt/provision keys or manage KMS grants."
  }
  assert {
    condition     = aws_vpc_security_group_ingress_rule.private_hops["commerce-integration"].referenced_security_group_id == "sg-test-commerce" && aws_vpc_security_group_ingress_rule.private_hops["commerce-integration"].from_port == 8083
    error_message = "Private ingress must reference explicit source security groups."
  }
}
run "runtime_cannot_administer_keys" {
  command = plan
  variables { key_administrator_arns = ["arn:aws:iam::000000000000:role/test-commerce"] }
  expect_failures = [var.commerce_task_role_arn]
}
run "runtime_cannot_provision_session_keys" {
  command = plan
  variables { session_key_provisioner_arn = "arn:aws:iam::000000000000:role/test-commerce" }
  expect_failures = [var.session_key_provisioner_arn]
}
run "no_public_ingress_or_unregistered_hops" {
  command = plan
  variables {
    private_hops = { public-commerce = { source_security_group_id = "0.0.0.0/0", target_security_group_id = "sg-test-commerce", target_port = 8081 } }
  }
  expect_failures = [var.private_hops]
}
run "no_wildcard_key_administrator" {
  command = plan
  variables { key_administrator_arns = ["*"] }
  expect_failures = [var.key_administrator_arns]
}

run "no_ai_before_bp5" {
  command = plan
  variables {
    private_hops = { commerce-ai = { source_security_group_id = "sg-test-commerce", target_security_group_id = "sg-test-ai", target_port = 8084 } }
  }
  expect_failures = [var.private_hops]
}
