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
