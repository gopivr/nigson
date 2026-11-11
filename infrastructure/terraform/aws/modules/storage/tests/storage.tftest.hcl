mock_provider "aws" {}

variables {
  environment                 = "stage"
  region                      = "eu-west-1"
  policy_revision             = "storage-v1"
  kms_key_arn                 = "arn:aws:kms:eu-west-1:123456789012:key/01234567-89ab-cdef-0123-456789abcdef"
  cloudfront_distribution_arn = "arn:aws:cloudfront::123456789012:distribution/EDFDVBD6EXAMPLE"
  bucket_names = {
    public_media   = "nigson-stage-public-media-example"
    quarantine     = "nigson-stage-quarantine-example"
    private_assets = "nigson-stage-private-assets-example"
    carrier_labels = "nigson-stage-carrier-labels-example"
  }
  lifecycle_days = {
    public_media   = { current_version_days = 365, noncurrent_version_days = 90, incomplete_upload_days = 7 }
    quarantine     = { current_version_days = 30, noncurrent_version_days = 30, incomplete_upload_days = 7 }
    private_assets = { current_version_days = 365, noncurrent_version_days = 90, incomplete_upload_days = 7 }
    carrier_labels = { current_version_days = 90, noncurrent_version_days = 90, incomplete_upload_days = 7 }
  }
  bucket_access = {
    public_media = { readers = {}, writers = {} }
    quarantine   = { readers = {}, writers = {} }
    private_assets = {
      readers = {
        commerce_reader = { role_arn = "arn:aws:iam::123456789012:role/nigson-commerce-task", prefixes = ["catalog/"] }
      }
      writers = {}
    }
    carrier_labels = { readers = {}, writers = {} }
  }
}

run "secure_versioned_buckets_and_cloudfront_origin" {
  command = plan

  assert {
    condition     = alltrue([for block in aws_s3_bucket_public_access_block.owned : block.block_public_acls && block.block_public_policy && block.ignore_public_acls && block.restrict_public_buckets])
    error_message = "All four buckets must block public ACL and policy access."
  }
  assert {
    condition     = aws_s3_bucket_versioning.owned["quarantine"].versioning_configuration[0].status == "Enabled"
    error_message = "Quarantine evidence must retain object versions."
  }
}
