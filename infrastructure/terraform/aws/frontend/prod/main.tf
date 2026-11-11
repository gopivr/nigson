terraform {
  required_version = ">= 1.12.2, < 2.0.0"
  required_providers { aws = { source = "hashicorp/aws", version = "6.15.0" } }
  backend "s3" {}
}
provider "aws" {
  region              = var.region
  allowed_account_ids = [var.expected_account_id]
}
module "frontend" {
  source                  = "../../modules/frontend"
  environment             = "prod"
  bucket_name             = var.bucket_name
  domain_name             = var.domain_name
  certificate_arn         = var.certificate_arn
  backend_origin_hostname = var.backend_origin_hostname
  release_id              = var.release_id
  zone_id                 = var.zone_id
  web_acl_arn             = var.web_acl_arn
}
output "frontend" { value = { url = module.frontend.url, distribution_id = module.frontend.distribution_id, bucket_name = module.frontend.bucket_name, upload_prefix = module.frontend.upload_prefix } }
