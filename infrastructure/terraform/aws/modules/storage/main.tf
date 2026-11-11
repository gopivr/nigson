terraform {
  required_version = "= 1.12.2"
  required_providers {
    aws = { source = "hashicorp/aws", version = "6.15.0" }
  }
}

locals {
  tags = {
    Application = "nigson"
    Environment = var.environment
    ManagedBy   = "terraform"
    Policy      = var.policy_revision
  }
  read_grants = merge({}, [for bucket, access in var.bucket_access : {
    for alias, grant in access.readers : "${bucket}-${alias}" => { bucket = bucket, grant = grant }
  }]...)
  write_grants = merge({}, [for bucket, access in var.bucket_access : {
    for alias, grant in access.writers : "${bucket}-${alias}" => { bucket = bucket, grant = grant }
  }]...)
}

resource "aws_s3_bucket" "owned" {
  for_each      = var.bucket_names
  bucket        = each.value
  force_destroy = false
  tags          = merge(local.tags, { DataClass = each.key })
  lifecycle { prevent_destroy = true }
}

resource "aws_s3_bucket_ownership_controls" "owned" {
  for_each = aws_s3_bucket.owned
  bucket   = each.value.id
  rule { object_ownership = "BucketOwnerEnforced" }
}

resource "aws_s3_bucket_public_access_block" "owned" {
  for_each                = aws_s3_bucket.owned
  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "owned" {
  for_each = aws_s3_bucket.owned
  bucket   = each.value.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "owned" {
  for_each = aws_s3_bucket.owned
  bucket   = each.value.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = each.key == "public_media" ? "AES256" : "aws:kms"
      kms_master_key_id = each.key == "public_media" ? null : var.kms_key_arn
    }
    bucket_key_enabled = each.key != "public_media"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "owned" {
  for_each = aws_s3_bucket.owned
  bucket   = each.value.id
  rule {
    id     = "${each.key}-approved-retention"
    status = "Enabled"
    filter {}
    expiration { days = var.lifecycle_days[each.key].current_version_days }
    noncurrent_version_expiration { noncurrent_days = var.lifecycle_days[each.key].noncurrent_version_days }
    abort_incomplete_multipart_upload { days_after_initiation = var.lifecycle_days[each.key].incomplete_upload_days }
  }
  depends_on = [aws_s3_bucket_versioning.owned]
}

resource "aws_s3_bucket_policy" "owned" {
  for_each = aws_s3_bucket.owned
  bucket   = each.value.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat([
      {
        Sid       = "DenyInsecureTransport"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource  = [each.value.arn, "${each.value.arn}/*"]
        Condition = { Bool = { "aws:SecureTransport" = "false" } }
      }
      ], each.key == "public_media" ? [{
        Sid       = "CloudFrontOriginAccessOnly"
        Effect    = "Allow"
        Principal = { Service = "cloudfront.amazonaws.com" }
        Action    = ["s3:GetObject"]
        Resource  = "${each.value.arn}/*"
        Condition = { StringEquals = { "AWS:SourceArn" = var.cloudfront_distribution_arn } }
    }] : [])
  })
  depends_on = [aws_s3_bucket_public_access_block.owned]
}

resource "aws_iam_role_policy" "read_access" {
  for_each = local.read_grants
  name     = "nigson-${var.environment}-${each.value.bucket}-${each.key}-read"
  role     = split("/", each.value.grant.role_arn)[length(split("/", each.value.grant.role_arn)) - 1]
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["s3:ListBucket"], Resource = aws_s3_bucket.owned[each.value.bucket].arn, Condition = { StringLike = { "s3:prefix" = [for prefix in each.value.grant.prefixes : "${prefix}*"] } } },
    { Effect = "Allow", Action = ["s3:GetObject", "s3:GetObjectVersion"], Resource = [for prefix in each.value.grant.prefixes : "${aws_s3_bucket.owned[each.value.bucket].arn}/${prefix}*"] }
  ] })
}

resource "aws_iam_role_policy" "write_access" {
  for_each = local.write_grants
  name     = "nigson-${var.environment}-${each.value.bucket}-${each.key}-write"
  role     = split("/", each.value.grant.role_arn)[length(split("/", each.value.grant.role_arn)) - 1]
  policy = jsonencode({ Version = "2012-10-17", Statement = concat([
    { Effect = "Allow", Action = ["s3:PutObject", "s3:AbortMultipartUpload"], Resource = [for prefix in each.value.grant.prefixes : "${aws_s3_bucket.owned[each.value.bucket].arn}/${prefix}*"] }
    ], each.value.bucket == "public_media" ? [] : [
    { Effect = "Allow", Action = ["kms:GenerateDataKey", "kms:Decrypt"], Resource = var.kms_key_arn, Condition = { StringEquals = { "kms:ViaService" = "s3.${var.region}.amazonaws.com", "kms:EncryptionContext:aws:s3:arn" = aws_s3_bucket.owned[each.value.bucket].arn } } }
  ]) })
}

resource "aws_iam_role_policy" "read_kms" {
  for_each = { for name, grant in local.read_grants : name => grant if grant.bucket != "public_media" }
  name     = "nigson-${var.environment}-${each.value.bucket}-${each.key}-kms-read"
  role     = split("/", each.value.grant.role_arn)[length(split("/", each.value.grant.role_arn)) - 1]
  policy   = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Action = ["kms:Decrypt"], Resource = var.kms_key_arn, Condition = { StringEquals = { "kms:ViaService" = "s3.${var.region}.amazonaws.com", "kms:EncryptionContext:aws:s3:arn" = aws_s3_bucket.owned[each.value.bucket].arn } } }] })
}

output "bucket_names" { value = { for name, bucket in aws_s3_bucket.owned : name => bucket.id } }
output "bucket_arns" { value = { for name, bucket in aws_s3_bucket.owned : name => bucket.arn } }
