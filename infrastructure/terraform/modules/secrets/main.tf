terraform {
  required_version = "= 1.12.2"
  required_providers {
    aws = { source = "hashicorp/aws", version = "6.15.0" }
  }
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  tags = {
    Application = "nigson"
    Environment = var.environment
    ManagedBy   = "terraform"
    Policy      = var.policy_revision
  }
  secret_entries = merge({}, [for service, secrets in var.secret_names : {
    for alias, name in secrets : "${service}-${alias}" => { service = service, alias = alias, name = name }
  }]...)
}

resource "aws_kms_key" "application" {
  description             = "Nigson ${var.environment} application data key ${var.policy_revision}"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AccountRootKeyAdministrationDelegation"
        Effect    = "Allow"
        Principal = { AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root" }
        Action    = "kms:*"
        Resource  = "*"
      },
      {
        Sid       = "NamedKeyAdministrators"
        Effect    = "Allow"
        Principal = { AWS = var.key_administrator_role_arns }
        Action    = ["kms:DescribeKey", "kms:GetKeyPolicy", "kms:PutKeyPolicy", "kms:EnableKey", "kms:DisableKey", "kms:UpdateKeyDescription", "kms:ScheduleKeyDeletion", "kms:CancelKeyDeletion", "kms:ListResourceTags", "kms:TagResource", "kms:UntagResource", "kms:EnableKeyRotation", "kms:DisableKeyRotation", "kms:GetKeyRotationStatus"]
        Resource  = "*"
      },
      {
        Sid       = "CloudWatchLogGroupEncryption"
        Effect    = "Allow"
        Principal = { Service = "logs.${data.aws_region.current.region}.amazonaws.com" }
        Action    = ["kms:Encrypt", "kms:Decrypt", "kms:ReEncrypt*", "kms:GenerateDataKey*", "kms:DescribeKey"]
        Resource  = "*"
        Condition = { ArnLike = { "kms:EncryptionContext:aws:logs:arn" = "arn:aws:logs:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:log-group:/nigson/${var.environment}/*" } }
      },
      {
        Sid       = "EcrRepositoryEncryption"
        Effect    = "Allow"
        Principal = { Service = "ecr.amazonaws.com" }
        Action    = ["kms:Decrypt", "kms:GenerateDataKey", "kms:DescribeKey"]
        Resource  = "*"
        Condition = { StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }, ArnLike = { "aws:SourceArn" = "arn:aws:ecr:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:repository/nigson/${var.environment}/*" } }
      },
      {
        Sid       = "RdsDatabaseEncryption"
        Effect    = "Allow"
        Principal = { Service = "rds.amazonaws.com" }
        Action    = ["kms:CreateGrant", "kms:DescribeKey", "kms:Encrypt", "kms:Decrypt", "kms:ReEncrypt*", "kms:GenerateDataKey*", "kms:RetireGrant"]
        Resource  = "*"
        Condition = { StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }, ArnLike = { "aws:SourceArn" = ["arn:aws:rds:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:db:nigson-${var.environment}-commerce", "arn:aws:rds:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:db:nigson-${var.environment}-identity"] } }
      },
      {
        Sid       = "SecretsManagerEncryption"
        Effect    = "Allow"
        Principal = { Service = "secretsmanager.${data.aws_region.current.region}.amazonaws.com" }
        Action    = ["kms:Encrypt", "kms:Decrypt", "kms:ReEncrypt*", "kms:GenerateDataKey*", "kms:DescribeKey"]
        Resource  = "*"
        Condition = { StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }, ArnLike = { "kms:EncryptionContext:SecretARN" = "arn:aws:secretsmanager:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:secret:nigson/${var.environment}/*" } }
      },
      {
        Sid       = "SnsTopicEncryption"
        Effect    = "Allow"
        Principal = { Service = "sns.amazonaws.com" }
        Action    = ["kms:Decrypt", "kms:GenerateDataKey*", "kms:DescribeKey"]
        Resource  = "*"
        Condition = { StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id, "kms:EncryptionContext:aws:sns:topicArn" = "arn:aws:sns:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:nigson-${var.environment}-domain-events" } }
      },
      {
        Sid       = "SqsQueueEncryption"
        Effect    = "Allow"
        Principal = { Service = "sqs.amazonaws.com" }
        Action    = ["kms:Decrypt", "kms:GenerateDataKey*", "kms:DescribeKey"]
        Resource  = "*"
        Condition = { StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }, ArnLike = { "kms:EncryptionContext:aws:sqs:arn" = "arn:aws:sqs:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:nigson-${var.environment}-*" } }
      }
    ]
  })
  lifecycle { prevent_destroy = true }
  tags = local.tags
}

resource "aws_kms_alias" "application" {
  name          = "alias/nigson/${var.environment}/application-data"
  target_key_id = aws_kms_key.application.key_id
}

resource "aws_secretsmanager_secret" "owned" {
  for_each                = local.secret_entries
  name                    = each.value.name
  description             = "Nigson ${var.environment} ${each.value.service} ${each.value.alias}; value provisioned outside Terraform"
  kms_key_id              = aws_kms_key.application.arn
  recovery_window_in_days = var.secret_recovery_window_days
  tags                    = merge(local.tags, { Service = each.value.service, SecretPurpose = each.value.alias })
  lifecycle { prevent_destroy = true }
}

output "kms_key_arn" { value = aws_kms_key.application.arn }
output "secret_arns" {
  value = { for service in keys(var.secret_names) : service => {
    for alias, _ in var.secret_names[service] : alias => aws_secretsmanager_secret.owned["${service}-${alias}"].arn
  } }
}
