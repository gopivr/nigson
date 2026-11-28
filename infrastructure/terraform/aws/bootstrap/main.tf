terraform {
  required_version = "= 1.12.2"
  required_providers {
    aws = {
      source = "hashicorp/aws", version = "6.15.0"
    }
  }
}
provider "aws" {
  region              = var.region
  allowed_account_ids = [var.expected_account_id]
  default_tags {
    tags = {
      Application = "nigson", Environment = var.environment, ManagedBy = "terraform"
    }
  }
}
variable "expected_account_id" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.expected_account_id))
    error_message = "Supply the actual twelve-digit AWS account ID; credentials use AWS_PROFILE."

  }
}
variable "region" {
  type    = string
  default = "eu-west-1"
}
variable "environment" {
  type    = string
  default = "stage"
  validation {
    condition     = contains(["stage", "prod"], var.environment)
    error_message = "Choose stage or prod."

  }
}
variable "domain_name" {
  type    = string
  default = "nigson.com"
  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]+\\.[a-z]{2,}$", var.domain_name))
    error_message = "Supply a domain you control."

  }
}
data "aws_caller_identity" "current" {
}
locals {
  zone_name    = var.environment == "prod" ? var.domain_name : "${var.environment}.${var.domain_name}"
  state_bucket = "nigson-${var.expected_account_id}-${var.region}-terraform-state"
}
resource "aws_s3_bucket" "state" {
  bucket = local.state_bucket
  lifecycle {
    prevent_destroy = true
  }
}
resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}
resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id
  policy = jsonencode({
    Version = "2012-10-17", Statement = [{
      Effect = "Deny", Principal = "*", Action = "s3:*", Resource = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"], Condition = {
        Bool = {
          "aws:SecureTransport" = "false"
        }
      }
    }]
  })
}
resource "aws_route53_zone" "environment" {
  name    = local.zone_name
  comment = "Nigson ${var.environment}; delegate using Squarespace DNS settings"
  lifecycle {
    prevent_destroy = true
  }
}
output "configuration" {
  value = {
    expected_account_id = var.expected_account_id
    region              = var.region
    environment         = var.environment
    domain_name         = var.domain_name
    zone_name           = local.zone_name
    zone_id             = aws_route53_zone.environment.zone_id
    state_bucket        = aws_s3_bucket.state.id
    state_key           = "nigson/first-account/${var.environment}/terraform.tfstate"

  }
}
output "squarespace_nameservers" {
  value = aws_route53_zone.environment.name_servers
}
output "squarespace_record_name" {
  value = var.environment == "prod" ? "CHANGE_DOMAIN_NAMESERVERS" : var.environment
}

# GitHub publishing authenticates independently of the ECS runtime roles.
variable "github_repository" {
  type    = string
  default = "gopivr/nigson"
  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "Use the exact GitHub owner/repository, without wildcards."
  }
}
variable "github_repository_id" {
  type    = string
  default = null
  validation {
    condition     = var.github_repository_id == null ? var.github_owner_id == null : can(regex("^[0-9]+$", var.github_repository_id)) && can(regex("^[0-9]+$", var.github_owner_id))
    error_message = "Supply both numeric GitHub repository and owner IDs, or neither."
  }
}
variable "github_owner_id" {
  type    = string
  default = null
}
variable "github_oidc_provider_arn" {
  description = "Reuse a GitHub OIDC provider owned by another stack; otherwise bootstrap creates it."
  type        = string
  default     = null
  validation {
    condition     = var.github_oidc_provider_arn == null ? true : var.github_oidc_provider_arn == "arn:aws:iam::${var.expected_account_id}:oidc-provider/token.actions.githubusercontent.com"
    error_message = "Reuse only the GitHub OIDC provider in this AWS account."
  }
}
locals {
  github_owner = split("/", var.github_repository)[0]
  github_repo  = split("/", var.github_repository)[1]
  github_subjects = concat(
    ["repo:${var.github_repository}:environment:${var.environment}"],
    var.github_repository_id == null ? [] : ["repo:${local.github_owner}@${var.github_owner_id}/${local.github_repo}@${var.github_repository_id}:environment:${var.environment}"]
  )
  github_oidc_provider_arn = var.github_oidc_provider_arn != null ? var.github_oidc_provider_arn : aws_iam_openid_connect_provider.github[0].arn
}
resource "aws_iam_openid_connect_provider" "github" {
  count          = var.github_oidc_provider_arn == null ? 1 : 0
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}
resource "aws_iam_role" "github_publish" {
  name                 = "nigson-${var.environment}-github-publish"
  max_session_duration = 7200
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = local.github_oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = { StringEquals = {
        "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        "token.actions.githubusercontent.com:sub" = local.github_subjects
      } }
    }]
  })
}
resource "aws_iam_role_policy" "github_publish" {
  name = "publish-${var.environment}-images"
  role = aws_iam_role.github_publish.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Sid = "RegistryLogin", Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
      {
        Sid = "PublishAndScanImages", Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage",
          "ecr:InitiateLayerUpload", "ecr:UploadLayerPart", "ecr:CompleteLayerUpload", "ecr:PutImage",
          "ecr:DescribeImages", "ecr:DescribeImageScanFindings"
        ]
        Resource = [for service in ["commerce", "experience", "integration", "identity", "ai"] :
        "arn:aws:ecr:${var.region}:${var.expected_account_id}:repository/nigson/${var.environment}/${service}"]
      }
    ]
  })
}
output "github_publish_role_arn" {
  description = "Automatically used by Backend — Build and Publish Images."
  value       = aws_iam_role.github_publish.arn
}
output "github_oidc_provider_arn" {
  value = local.github_oidc_provider_arn
}

resource "aws_iam_role" "github_deploy" {
  name                 = "nigson-${var.environment}-github-deploy"
  max_session_duration = 7200
  assume_role_policy   = aws_iam_role.github_publish.assume_role_policy
}
locals {
  deploy_owners    = ["commerce", "experience", "integration", "identity", "ai"]
  migration_owners = ["commerce", "experience", "integration", "ai"]
  deploy_migration_definition_arns = [for owner in local.migration_owners :
  "arn:aws:ecs:${var.region}:${var.expected_account_id}:task-definition/nigson-${var.environment}-${owner}-migration-BP*:*"]
  deploy_cluster_arn = "arn:aws:ecs:${var.region}:${var.expected_account_id}:cluster/nigson-${var.environment}"
  deploy_service_arns = [for service in local.deploy_owners :
  "arn:aws:ecs:${var.region}:${var.expected_account_id}:service/nigson-${var.environment}/nigson-${var.environment}-${service}"]
  deploy_task_definition_arns = [for service in local.deploy_owners :
  "arn:aws:ecs:${var.region}:${var.expected_account_id}:task-definition/nigson-${var.environment}-${service}:*"]
}
resource "aws_iam_role_policy" "github_deploy" {
  name = "deploy-${var.environment}-services"
  role = aws_iam_role.github_deploy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "InspectMigrationReceipts", Effect = "Allow", Action = ["ecs:DescribeTasks"]
        Resource  = "arn:aws:ecs:${var.region}:${var.expected_account_id}:task/nigson-${var.environment}/*"
        Condition = { ArnEquals = { "ecs:cluster" = local.deploy_cluster_arn } }
      },
      {
        Sid       = "InspectServices", Effect = "Allow", Action = ["ecs:DescribeServices"]
        Resource  = local.deploy_service_arns
        Condition = { ArnEquals = { "ecs:cluster" = local.deploy_cluster_arn } }
      },
      {
        # ECS DescribeTaskDefinition does not support resource-level permissions.
        Sid       = "InspectTaskDefinitions", Effect = "Allow", Action = ["ecs:DescribeTaskDefinition"], Resource = "*"
        Condition = { StringEquals = { "aws:RequestedRegion" = var.region } }
      },
      {
        Sid      = "DeployAndRollback", Effect = "Allow", Action = ["ecs:UpdateService"]
        Resource = local.deploy_service_arns
        Condition = {
          ArnEquals = { "ecs:cluster" = local.deploy_cluster_arn }
          ArnLike   = { "ecs:task-definition" = local.deploy_task_definition_arns }
        }
      },
      {
        Sid = "PassApplicationRoles", Effect = "Allow", Action = ["iam:PassRole"]
        Resource = concat(flatten([for service in local.deploy_owners : [
          "arn:aws:iam::${var.expected_account_id}:role/nigson-${var.environment}-${service}-task",
          "arn:aws:iam::${var.expected_account_id}:role/nigson-${var.environment}-${service}-execution"
          ]]), flatten([for owner in local.migration_owners : [
          "arn:aws:iam::${var.expected_account_id}:role/nigson-${var.environment}-${owner}-migration-task",
          "arn:aws:iam::${var.expected_account_id}:role/nigson-${var.environment}-${owner}-migration-execution"
        ]]))
        Condition = { StringEquals = { "iam:PassedToService" = "ecs-tasks.amazonaws.com" } }
      },
      {
        Sid      = "RegisterReleaseRevisions", Effect = "Allow", Action = ["ecs:RegisterTaskDefinition"]
        Resource = concat(local.deploy_task_definition_arns, local.deploy_migration_definition_arns)
        Condition = {
          StringEquals                = { "aws:RequestTag/Application" = "nigson", "aws:RequestTag/Environment" = var.environment }
          BoolIfExists                = { "ecs:privileged" = "false" }
          "ForAllValues:StringEquals" = { "ecs:compute-compatibility" = ["FARGATE"] }
        }
      },
      {
        Sid      = "RunOwnerMigrations", Effect = "Allow", Action = ["ecs:RunTask"]
        Resource = local.deploy_migration_definition_arns
        Condition = {
          ArnEquals    = { "ecs:cluster" = local.deploy_cluster_arn }
          BoolIfExists = { "ecs:enable-execute-command" = "false" }
        }
      },
      {
        Sid       = "TagReleaseResources", Effect = "Allow", Action = ["ecs:TagResource"]
        Resource  = concat(local.deploy_task_definition_arns, local.deploy_migration_definition_arns, ["arn:aws:ecs:${var.region}:${var.expected_account_id}:task/nigson-${var.environment}/*"])
        Condition = { StringEquals = { "ecs:CreateAction" = ["RegisterTaskDefinition", "RunTask"] } }
      },
      {
        Sid      = "VerifyReleaseImages", Effect = "Allow", Action = ["ecr:DescribeImages", "ecr:DescribeImageScanFindings"]
        Resource = [for service in local.deploy_owners : "arn:aws:ecr:${var.region}:${var.expected_account_id}:repository/nigson/${var.environment}/${service}"]
      },
      {
        Sid       = "DiscoverMigrationNetwork", Effect = "Allow", Action = ["ec2:DescribeSubnets", "ec2:DescribeSecurityGroups"], Resource = "*"
        Condition = { StringEquals = { "aws:RequestedRegion" = var.region } }
      }
    ]
  })
}
output "github_deploy_role_arn" {
  description = "Automatically used by Backend — Deploy Release."
  value       = aws_iam_role.github_deploy.arn
}

# Initial deployment stays on short-lived OIDC. Ordinary release permissions are unchanged.
locals {
  setup_roles           = concat(flatten([for owner in ["commerce", "experience", "integration", "ai"] : ["${owner}-migration-task", "${owner}-migration-execution"]]), ["database-setup-task", "database-setup-execution", "realm-setup-task", "realm-setup-execution"])
  setup_role_arns       = [for role in local.setup_roles : "arn:aws:iam::${var.expected_account_id}:role/nigson-${var.environment}-${role}"]
  setup_definition_arns = concat(local.deploy_task_definition_arns, local.deploy_migration_definition_arns, [for mode in ["database", "realm"] : "arn:aws:ecs:${var.region}:${var.expected_account_id}:task-definition/nigson-${var.environment}-${mode}-setup:*"], ["arn:aws:ecs:${var.region}:${var.expected_account_id}:task-definition/nigson-${var.environment}-identity-bootstrap:*"])
}
resource "aws_iam_policy" "runtime_task_boundary" {
  name = "nigson-${var.environment}-runtime-task-boundary"
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"], Resource = "arn:aws:ecr:${var.region}:${var.expected_account_id}:repository/nigson/${var.environment}/*" },
    { Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = "arn:aws:logs:${var.region}:${var.expected_account_id}:log-group:/nigson/${var.environment}/*" },
    { Effect = "Allow", Action = ["secretsmanager:GetSecretValue"], Resource = "arn:aws:secretsmanager:${var.region}:${var.expected_account_id}:secret:nigson/${var.environment}/*" },
    { Effect = "Allow", Action = ["secretsmanager:GetSecretValue"], Resource = "arn:aws:secretsmanager:${var.region}:${var.expected_account_id}:secret:rds!db-*", Condition = { StringEquals = { "aws:ResourceTag/aws:rds:primaryDBInstanceArn" = [for name in ["commerce", "identity"] : "arn:aws:rds:${var.region}:${var.expected_account_id}:db:nigson-${var.environment}-${name}"] } } },
    { Effect = "Allow", Action = ["kms:Decrypt"], Resource = "arn:aws:kms:${var.region}:${var.expected_account_id}:key/*", Condition = { StringEquals = { "kms:ViaService" = "secretsmanager.${var.region}.amazonaws.com" } } }
  ] })
}
resource "aws_iam_role" "github_runtime_setup" {
  name                 = "nigson-${var.environment}-github-runtime-setup"
  max_session_duration = 7200
  assume_role_policy   = aws_iam_role.github_publish.assume_role_policy
}
locals {
  runtime_setup_statements = concat(jsondecode(aws_iam_role_policy.github_deploy.policy).Statement, [
    { Sid = "StateBucketMetadata", Effect = "Allow", Action = ["s3:ListBucket", "s3:GetBucketLocation"], Resource = aws_s3_bucket.state.arn },
    { Sid = "ReadBootstrapAndPlatformState", Effect = "Allow", Action = ["s3:GetObject"], Resource = ["${aws_s3_bucket.state.arn}/nigson/bootstrap/${var.environment}/terraform.tfstate", "${aws_s3_bucket.state.arn}/nigson/first-account/${var.environment}/terraform.tfstate"] },
    { Sid = "WritePlatformStateAndLock", Effect = "Allow", Action = ["s3:PutObject"], Resource = ["${aws_s3_bucket.state.arn}/nigson/first-account/${var.environment}/terraform.tfstate", "${aws_s3_bucket.state.arn}/nigson/first-account/${var.environment}/terraform.tfstate.tflock"] },
    { Sid = "PlatformLock", Effect = "Allow", Action = ["s3:GetObject", "s3:DeleteObject"], Resource = "${aws_s3_bucket.state.arn}/nigson/first-account/${var.environment}/terraform.tfstate.tflock" },
    { Sid = "ReadPlatformMetadata", Effect = "Allow", Action = ["ec2:Describe*", "elasticloadbalancing:Describe*", "rds:Describe*", "rds:ListTagsForResource", "logs:DescribeLogGroups", "logs:ListTagsForResource", "logs:ListTagsLogGroup", "acm:DescribeCertificate", "acm:ListTagsForCertificate", "wafv2:Get*", "wafv2:ListTagsForResource", "cloudfront:Get*", "cloudfront:ListTagsForResource", "route53:Get*", "route53:ListResourceRecordSets", "route53:ListTagsForResource", "sns:GetTopicAttributes", "sns:ListTagsForResource", "sqs:GetQueueAttributes", "sqs:GetQueueUrl", "sqs:ListQueueTags", "servicediscovery:GetNamespace", "servicediscovery:GetService", "servicediscovery:ListTagsForResource", "application-autoscaling:DescribeScalableTargets", "application-autoscaling:DescribeScalingPolicies", "application-autoscaling:ListTagsForResource"], Resource = "*" },
    { Sid = "ReadStorageMetadata", Effect = "Allow", Action = ["s3:GetBucket*", "s3:GetEncryptionConfiguration", "s3:GetLifecycleConfiguration", "s3:GetReplicationConfiguration", "s3:GetAccelerateConfiguration", "s3:GetBucketObjectLockConfiguration"], Resource = "arn:aws:s3:::nigson-${var.environment}-*" },
    { Sid = "ReadRoleMetadata", Effect = "Allow", Action = ["iam:GetRole", "iam:GetRolePolicy", "iam:ListRolePolicies", "iam:ListAttachedRolePolicies", "iam:GetPolicy", "iam:GetPolicyVersion"], Resource = ["arn:aws:iam::${var.expected_account_id}:role/nigson-${var.environment}-*", aws_iam_policy.runtime_task_boundary.arn] },
    { Sid = "CreateBoundedSetupRoles", Effect = "Allow", Action = ["iam:CreateRole", "iam:PutRolePermissionsBoundary"], Resource = local.setup_role_arns, Condition = { StringEquals = { "iam:PermissionsBoundary" = aws_iam_policy.runtime_task_boundary.arn } } },
    { Sid = "ConfigureSetupRoles", Effect = "Allow", Action = ["iam:PutRolePolicy", "iam:TagRole", "iam:UpdateAssumeRolePolicy"], Resource = local.setup_role_arns },
    { Sid = "PassSetupRoles", Effect = "Allow", Action = ["iam:PassRole"], Resource = concat(local.setup_role_arns, ["arn:aws:iam::${var.expected_account_id}:role/nigson-${var.environment}-identity-bootstrap-execution"]), Condition = { StringEquals = { "iam:PassedToService" = "ecs-tasks.amazonaws.com" } } },
    { Sid = "ReadKeys", Effect = "Allow", Action = ["kms:DescribeKey", "kms:GetKeyPolicy", "kms:GetKeyRotationStatus", "kms:ListResourceTags", "kms:GetPublicKey"], Resource = "arn:aws:kms:${var.region}:${var.expected_account_id}:key/*" },
    { Sid = "SeedWrappedSessionKey", Effect = "Allow", Action = ["kms:GenerateDataKeyWithoutPlaintext"], Resource = "arn:aws:kms:${var.region}:${var.expected_account_id}:key/*", Condition = { StringEquals = { "aws:ResourceTag/Environment" = var.environment, "kms:EncryptionContext:purpose" = "nigson-commerce-session" }, Null = { "kms:EncryptionContext:revision" = "false" } } },
    { Sid = "SeedInternalSecrets", Effect = "Allow", Action = ["secretsmanager:DescribeSecret", "secretsmanager:GetResourcePolicy", "secretsmanager:PutSecretValue"], Resource = "arn:aws:secretsmanager:${var.region}:${var.expected_account_id}:secret:nigson/${var.environment}/*" },
    { Sid = "InspectRdsSecretMetadata", Effect = "Allow", Action = ["secretsmanager:DescribeSecret"], Resource = "arn:aws:secretsmanager:${var.region}:${var.expected_account_id}:secret:rds!db-*" },
    { Sid = "RegisterAndRunInitialTasks", Effect = "Allow", Action = ["ecs:RegisterTaskDefinition", "ecs:RunTask", "ecs:TagResource"], Resource = concat(local.setup_definition_arns, ["arn:aws:ecs:${var.region}:${var.expected_account_id}:task/nigson-${var.environment}/*"]) },
    { Sid = "CreateInitialServices", Effect = "Allow", Action = ["ecs:CreateService", "ecs:TagResource"], Resource = local.deploy_service_arns, Condition = { ArnEquals = { "ecs:cluster" = local.deploy_cluster_arn } } },
    { Sid = "InspectInitialTasks", Effect = "Allow", Action = ["ecs:DescribeClusters", "ecs:ListTasks"], Resource = local.deploy_cluster_arn },
    { Sid = "ReadTaskTags", Effect = "Allow", Action = ["ecs:ListTagsForResource"], Resource = concat(local.setup_definition_arns, local.deploy_service_arns, [local.deploy_cluster_arn]) },
    { Sid = "InitialRoutes", Effect = "Allow", Action = ["elasticloadbalancing:CreateRule", "elasticloadbalancing:AddTags", "elasticloadbalancing:SetRulePriorities"], Resource = ["arn:aws:elasticloadbalancing:${var.region}:${var.expected_account_id}:listener/app/nigson-${var.environment}/*/*", "arn:aws:elasticloadbalancing:${var.region}:${var.expected_account_id}:listener-rule/app/nigson-${var.environment}/*/*/*"] },
    { Sid = "InitialScaling", Effect = "Allow", Action = ["application-autoscaling:RegisterScalableTarget", "application-autoscaling:PutScalingPolicy", "application-autoscaling:TagResource"], Resource = "arn:aws:application-autoscaling:${var.region}:${var.expected_account_id}:scalable-target/*", Condition = { StringEqualsIfExists = { "application-autoscaling:service-namespace" = "ecs", "application-autoscaling:scalable-dimension" = "ecs:service:DesiredCount" } } },
    { Sid = "ServiceLinkedRoles", Effect = "Allow", Action = ["iam:CreateServiceLinkedRole"], Resource = ["arn:aws:iam::${var.expected_account_id}:role/aws-service-role/ecs.amazonaws.com/AWSServiceRoleForECS", "arn:aws:iam::${var.expected_account_id}:role/aws-service-role/ecs.application-autoscaling.amazonaws.com/AWSServiceRoleForApplicationAutoScaling_ECSService"], Condition = { StringEquals = { "iam:AWSServiceName" = ["ecs.amazonaws.com", "ecs.application-autoscaling.amazonaws.com"] } } }
  ])
}
resource "aws_iam_policy" "github_runtime_setup" {
  for_each = { for index, statements in chunklist(local.runtime_setup_statements, 6) : tostring(index) => statements }
  name     = "nigson-${var.environment}-first-runtime-${each.key}"
  policy   = jsonencode({ Version = "2012-10-17", Statement = each.value })
}
resource "aws_iam_role_policy_attachment" "github_runtime_setup" {
  for_each   = aws_iam_policy.github_runtime_setup
  role       = aws_iam_role.github_runtime_setup.name
  policy_arn = each.value.arn
}
output "github_runtime_setup_role_arn" { value = aws_iam_role.github_runtime_setup.arn }
