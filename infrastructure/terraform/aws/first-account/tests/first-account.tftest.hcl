mock_provider "aws" {
  mock_data "aws_caller_identity" { defaults = { account_id = "123456789012", arn = "arn:aws:iam::123456789012:user/local-deployer" } }
  mock_data "aws_region" { defaults = { region = "eu-west-1", name = "eu-west-1" } }
  mock_data "aws_availability_zones" { defaults = { names = ["eu-west-1a", "eu-west-1b", "eu-west-1c"] } }
  mock_data "aws_rds_engine_version" { defaults = { version = "18.1" } }
  mock_resource "aws_kms_key" { defaults = { arn = "arn:aws:kms:eu-west-1:123456789012:key/01234567-89ab-cdef-0123-456789abcdef" } }
  mock_resource "aws_iam_role" { defaults = { arn = "arn:aws:iam::123456789012:role/nigson-stage-test" } }
  mock_resource "aws_security_group" { defaults = { id = "sg-0123456789abcdef0" } }
  mock_resource "aws_secretsmanager_secret" { defaults = { arn = "arn:aws:secretsmanager:eu-west-1:123456789012:secret:nigson/stage/identity/test-abc123" } }
  mock_resource "aws_db_instance" { defaults = { master_user_secret = [{ secret_arn = "arn:aws:secretsmanager:eu-west-1:123456789012:secret:rds-admin-test" }] } }
  mock_resource "aws_acm_certificate" { defaults = { arn = "arn:aws:acm:eu-west-1:123456789012:certificate/01234567-89ab-cdef-0123-456789abcdef", domain_validation_options = [for name in ["api", "auth", "identity-admin"] : { domain_name = "${name}.stage.nigson.com", resource_record_name = "_token.${name}.stage.nigson.com", resource_record_type = "CNAME", resource_record_value = "_${name}.acm-validations.aws" }] } }
  mock_resource "aws_wafv2_web_acl" { defaults = { arn = "arn:aws:wafv2:eu-west-1:123456789012:regional/webacl/nigson-stage/test" } }
  mock_resource "aws_sns_topic" { defaults = { arn = "arn:aws:sns:eu-west-1:123456789012:nigson-stage-alerts" } }
  mock_resource "aws_cloudfront_distribution" { defaults = { arn = "arn:aws:cloudfront::123456789012:distribution/E123ABC456", domain_name = "test.cloudfront.net", hosted_zone_id = "Z2FDTNDATAQYW2" } }
}
mock_provider "aws" {
  alias = "global"
  mock_resource "aws_acm_certificate" { defaults = { arn = "arn:aws:acm:us-east-1:123456789012:certificate/01234567-89ab-cdef-0123-456789abcdef", domain_validation_options = [{ domain_name = "stage.nigson.com", resource_record_name = "_frontend.stage.nigson.com", resource_record_type = "CNAME", resource_record_value = "_frontend.acm-validations.aws" }] } }
  mock_resource "aws_wafv2_web_acl" { defaults = { arn = "arn:aws:wafv2:us-east-1:123456789012:global/webacl/nigson-stage/test" } }
}
variables {
  expected_account_id = "123456789012"
  zone_name           = "stage.nigson.com"
  zone_id             = "Z123456789ABC"
}
run "create_foundation_without_images_or_running_apps" {
  command = plan
  assert {
    condition     = length(module.compute.repository_urls) == 3
    error_message = "First infrastructure apply must create repositories for real subsequent image publication."
  }
  assert {
    condition     = !output.infrastructure.applications_activated && !output.infrastructure.identity_activated && length(module.migrations) == 0 && module.identity.keycloak_task_definition_arn == null
    error_message = "No unpublished image, migration or identity task may be fabricated during bootstrap."
  }
  assert {
    condition     = module.network.vpc_cidr == var.vpc_cidr && length(module.compute.service_security_group_ids) == 3
    error_message = "Network, private database and load balancer must be provisioned by Terraform."
  }
  assert {
    condition     = aws_acm_certificate.regional.validation_method == "DNS" && aws_acm_certificate.frontend.validation_method == "DNS" && aws_wafv2_web_acl.global.scope == "CLOUDFRONT"
    error_message = "Certificates and both WAF scopes must be created rather than pre-supplied ARNs."
  }
}
run "activation_requires_published_artifacts" {
  command = plan
  variables {
    publish_task_definitions = true
    migration_image_digest   = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  }
  expect_failures = [var.image_digests]
}

run "alerts_use_only_sns_topic_policy_actions" {
  command = plan
  override_resource {
    target          = aws_sns_topic.alerts
    override_during = plan
    values          = { arn = "arn:aws:sns:eu-west-1:123456789012:nigson-stage-operator-alerts" }
  }
  assert {
    condition = toset(jsondecode(aws_sns_topic_policy.alerts.policy).Statement[0].Action) == toset([
      "sns:GetTopicAttributes", "sns:SetTopicAttributes", "sns:AddPermission", "sns:RemovePermission", "sns:DeleteTopic", "sns:Subscribe", "sns:ListSubscriptionsByTopic", "sns:Publish"
    ])
    error_message = "SNS topic policies require supported topic actions, not a service-wide wildcard."
  }
  assert {
    condition = (
      jsondecode(aws_sns_topic_policy.alerts.policy).Statement[1].Action == "sns:Publish" &&
      jsondecode(aws_sns_topic_policy.alerts.policy).Statement[1].Principal.Service == "cloudwatch.amazonaws.com" &&
      jsondecode(aws_sns_topic_policy.alerts.policy).Statement[1].Condition.StringEquals["aws:SourceAccount"] == var.expected_account_id &&
      jsondecode(aws_sns_topic_policy.alerts.policy).Statement[1].Condition.ArnLike["aws:SourceArn"] == "arn:aws:cloudwatch:${var.region}:${var.expected_account_id}:alarm:nigson-${var.environment}-*"
    )
    error_message = "CloudWatch publishing must remain restricted to this account/environment alarms."
  }
}
run "compute_and_observability_share_application_log_groups" {
  command = plan
  assert {
    condition     = tomap(module.compute.log_groups) == tomap(module.observability.log_groups) && length(module.compute.log_groups) == 3
    error_message = "Application groups must have one resource owner and shared names."
  }
}

run "partial_apply_has_observability_owned_groups" {
  command   = apply
  state_key = "log-ownership-upgrade"
  module { source = "./tests/fixtures/legacy-logs" }
  plan_options {
    target = [module.observability.aws_cloudwatch_log_group.owner]
  }
}
run "resume_partial_apply_moves_groups_to_compute" {
  command   = plan
  state_key = "log-ownership-upgrade"
  assert {
    condition     = tomap(module.compute.log_groups) == tomap(module.observability.log_groups)
    error_message = "Moved groups remain available to the runtime and observability under identical names."
  }
}

run "record_applied_runtime_configuration_for_ui_releases" {
  command = plan
  variables {
    publish_task_definitions = true
    activate_applications    = true
    activate_identity        = true
    image_digests            = { commerce = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", experience = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", integration = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", identity = "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd" }
    migration_image_digest   = "sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    application_environment  = { commerce = { APP_ENV = "stage", NIGSON_SECURITY_ENABLED = "true" }, experience = { APP_ENV = "stage", NIGSON_SECURITY_ENABLED = "true" }, integration = { APP_ENV = "stage", NIGSON_SECURITY_ENABLED = "true" } }
  }
  assert {
    condition     = output.deployment_configuration.schema_version == 1 && output.deployment_configuration.variables.publish_task_definitions && output.deployment_configuration.variables.activate_applications && output.deployment_configuration.variables.activate_identity && output.deployment_configuration.variables.image_digests == var.image_digests
    error_message = "UI releases must be able to retain applied runtime activation, images and environment inputs."
  }
}
run "frontend_release_existing_distribution" {
  command   = apply
  state_key = "ui-origin-only"
  module { source = "./tests/fixtures/frontend-release-plan" }
  variables { release_id = "initial" }
}
run "frontend_release_plan_updates_static_origin_only" {
  command   = plan
  state_key = "ui-origin-only"
  module { source = "./tests/fixtures/frontend-release-plan" }
  variables { release_id = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-123-1" }
}

run "identity_target_tls_is_automatically_provisioned" {
  command = plan
  variables {
    publish_task_definitions = true
    image_digests = { commerce = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", experience = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", integration = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", identity = "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd" }
    migration_image_digest = "sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
  }
  assert {
    condition = module.identity.target_tls_mode == "generated" && aws_acm_certificate.regional.subject_alternative_names == toset(["auth.stage.nigson.com", "identity-admin.stage.nigson.com"])
    error_message = "First-account identity must generate private target TLS and retain public ACM coverage for both identity hostnames."
  }
}
