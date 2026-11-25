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
