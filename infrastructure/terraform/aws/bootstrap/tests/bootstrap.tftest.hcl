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

run "github_publisher_is_environment_scoped" {
  command = plan
  override_resource {
    target          = aws_iam_openid_connect_provider.github[0]
    override_during = plan
    values          = { arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com" }
  }
  variables {
    github_repository_id = "12345"
    github_owner_id      = "67890"
  }
  assert {
    condition     = aws_iam_role.github_publish.name == "nigson-stage-github-publish"
    error_message = "Workflow role discovery must match the bootstrap-managed name."
  }
  assert {
    condition     = aws_iam_openid_connect_provider.github[0].url == "https://token.actions.githubusercontent.com" && aws_iam_openid_connect_provider.github[0].client_id_list == toset(["sts.amazonaws.com"])
    error_message = "Trust only the GitHub issuer and STS audience."
  }
  assert {
    condition     = jsondecode(aws_iam_role.github_publish.assume_role_policy).Statement[0].Action == "sts:AssumeRoleWithWebIdentity" && jsondecode(aws_iam_role.github_publish.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == ["repo:gopivr/nigson:environment:stage", "repo:gopivr@67890/nigson@12345:environment:stage"]
    error_message = "Trust must bind both supported GitHub subject formats to this repository and stage environment."
  }
  assert {
    condition     = jsondecode(aws_iam_role.github_publish.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:aud"] == "sts.amazonaws.com"
    error_message = "Audience must be validated."
  }
  assert {
    condition     = jsondecode(aws_iam_role_policy.github_publish.policy).Statement[0].Action == ["ecr:GetAuthorizationToken"] && jsondecode(aws_iam_role_policy.github_publish.policy).Statement[0].Resource == "*"
    error_message = "Only registry authentication may use wildcard resources."
  }
  assert {
    condition     = toset(jsondecode(aws_iam_role_policy.github_publish.policy).Statement[1].Resource) == toset([for service in ["commerce", "experience", "integration", "identity", "ai"] : "arn:aws:ecr:eu-west-1:123456789012:repository/nigson/stage/${service}"])
    error_message = "Publishing access must not cross repository, account, region or environment boundaries."
  }
  assert {
    condition     = toset(jsondecode(aws_iam_role_policy.github_publish.policy).Statement[1].Action) == toset(["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart", "ecr:CompleteLayerUpload", "ecr:PutImage", "ecr:DescribeImages", "ecr:DescribeImageScanFindings"])
    error_message = "Publisher must have only push/pull and scan-read permissions, never IAM, ECS, secrets, Terraform or ECR deletion."
  }
}
run "shared_oidc_provider_is_reused_without_ownership" {
  command = plan
  variables {
    github_oidc_provider_arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
  }
  assert {
    condition     = length(aws_iam_openid_connect_provider.github) == 0 && jsondecode(aws_iam_role.github_publish.assume_role_policy).Statement[0].Principal.Federated == var.github_oidc_provider_arn
    error_message = "A shared issuer must be reused, without creating or taking ownership of it."
  }
}
run "cross_account_oidc_is_refused" {
  command = plan
  variables {
    github_oidc_provider_arn = "arn:aws:iam::999999999999:oidc-provider/token.actions.githubusercontent.com"
  }
  expect_failures = [var.github_oidc_provider_arn]
}
run "wildcard_repository_is_refused" {
  command = plan
  variables { github_repository = "gopivr/*" }
  expect_failures = [var.github_repository]
}
run "partial_immutable_subject_ids_are_refused" {
  command = plan
  variables { github_repository_id = "12345" }
  expect_failures = [var.github_repository_id]
}

run "github_deployer_is_scoped_to_existing_environment_services" {
  command = plan
  variables {
    github_oidc_provider_arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
  }
  assert {
    condition     = aws_iam_role.github_deploy.name == "nigson-stage-github-deploy" && aws_iam_role.github_deploy.assume_role_policy == aws_iam_role.github_publish.assume_role_policy
    error_message = "Deploy role must have the same environment-scoped OIDC trust as publishing, with a separate identity."
  }
  assert {
    condition     = jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[0].Action == ["ecs:DescribeTasks"] && jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[0].Resource == "arn:aws:ecs:eu-west-1:123456789012:task/nigson-stage/*" && jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[0].Condition.ArnEquals["ecs:cluster"] == "arn:aws:ecs:eu-west-1:123456789012:cluster/nigson-stage"
    error_message = "Migration receipt inspection must stay within this environment's cluster."
  }
  assert {
    condition     = toset(jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[1].Resource) == toset([for service in ["commerce", "experience", "integration", "ai"] : "arn:aws:ecs:eu-west-1:123456789012:service/nigson-stage/nigson-stage-${service}"])
    error_message = "Service inspection must not extend to identity or another environment."
  }
  assert {
    condition     = jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[2].Action == ["ecs:DescribeTaskDefinition"] && jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[2].Resource == "*" && jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[2].Condition.StringEquals["aws:RequestedRegion"] == "eu-west-1"
    error_message = "The ECS task-definition read API requires wildcard resources but must remain region-scoped and read-only."
  }
  assert {
    condition     = jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[3].Action == ["ecs:UpdateService"] && jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[3].Resource == jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[1].Resource && jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[3].Condition.ArnEquals["ecs:cluster"] == "arn:aws:ecs:eu-west-1:123456789012:cluster/nigson-stage" && toset(jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[3].Condition.ArnLike["ecs:task-definition"]) == toset([for service in ["commerce", "experience", "integration", "ai"] : "arn:aws:ecs:eu-west-1:123456789012:task-definition/nigson-stage-${service}:*"])
    error_message = "Deploy and rollback must stay within existing environment services and their task-definition families."
  }
  assert {
    condition     = jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[4].Action == ["iam:PassRole"] && toset(jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[4].Resource) == toset(flatten([for service in ["commerce", "experience", "integration", "ai"] : ["arn:aws:iam::123456789012:role/nigson-stage-${service}-task", "arn:aws:iam::123456789012:role/nigson-stage-${service}-execution"]])) && jsondecode(aws_iam_role_policy.github_deploy.policy).Statement[4].Condition.StringEquals["iam:PassedToService"] == "ecs-tasks.amazonaws.com"
    error_message = "Only this environment's app execution/task roles may be passed to ECS tasks."
  }
  assert {
    condition     = toset(flatten([for statement in jsondecode(aws_iam_role_policy.github_deploy.policy).Statement : statement.Action])) == toset(["ecs:DescribeTasks", "ecs:DescribeServices", "ecs:DescribeTaskDefinition", "ecs:UpdateService", "iam:PassRole"])
    error_message = "Deployer must not publish images, create services/task definitions, run migrations, provision infrastructure or read secrets."
  }
}
run "production_deployer_does_not_share_staging_scope" {
  command = plan
  variables {
    environment              = "prod"
    github_oidc_provider_arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
  }
  assert {
    condition     = aws_iam_role.github_deploy.name == "nigson-prod-github-deploy" && jsondecode(aws_iam_role.github_deploy.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == ["repo:gopivr/nigson:environment:prod"] && !strcontains(aws_iam_role_policy.github_deploy.policy, "nigson-stage")
    error_message = "Production must use its own role, trust subject and resource scope."
  }
}
