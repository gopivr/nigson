mock_provider "aws" {
  mock_resource "aws_s3_bucket" {
    defaults = { arn = "arn:aws:s3:::synthetic-nigson-mfe", bucket_regional_domain_name = "synthetic-nigson-mfe.s3.eu-west-1.amazonaws.com" }
  }
  mock_resource "aws_cloudfront_distribution" {
    defaults = { arn = "arn:aws:cloudfront::123456789012:distribution/SYNTHETIC", hosted_zone_id = "Z2FDTNDATAQYW2" }
  }
}
variables {
  environment             = "stage"
  bucket_name             = "synthetic-nigson-mfe"
  domain_name             = "shop.synthetic.test"
  backend_origin_hostname = "api.synthetic.test"
  certificate_arn         = "arn:aws:acm:us-east-1:123456789012:certificate/synthetic"
  web_acl_arn             = "arn:aws:wafv2:us-east-1:123456789012:global/webacl/synthetic/id"
  release_id              = "synthetic-release-1"
}
run "private_static_and_uncached_api" {
  command = plan
  assert {
    condition     = aws_s3_bucket_public_access_block.frontend.block_public_policy && aws_cloudfront_origin_access_control.frontend.signing_behavior == "always" && aws_s3_bucket_versioning.frontend.versioning_configuration[0].status == "Enabled"
    error_message = "Frontend storage must remain private, versioned and accessible through signed CloudFront requests."
  }
  assert {
    condition     = length(aws_cloudfront_distribution.frontend.ordered_cache_behavior) == 4 && aws_cloudfront_cache_policy.private.max_ttl == 0 && aws_cloudfront_origin_request_policy.api.cookies_config[0].cookie_behavior == "all" && aws_cloudfront_origin_request_policy.api.query_strings_config[0].query_string_behavior == "all"
    error_message = "API/BFF cookies and query strings must pass through with caching disabled."
  }
  assert {
    condition     = length(aws_cloudfront_distribution.frontend.custom_error_response) == 0 && aws_cloudfront_distribution.frontend.viewer_certificate[0].minimum_protocol_version == "TLSv1.2_2021"
    error_message = "Backend errors must never be converted to SPA success; public TLS must be enforced."
  }
}
run "reject_wrong_certificate_region" {
  command = plan
  variables { certificate_arn = "arn:aws:acm:eu-west-1:123456789012:certificate/synthetic" }
  expect_failures = [var.certificate_arn]
}
run "reject_origin_loop" {
  command = plan
  variables { backend_origin_hostname = "shop.synthetic.test" }
  expect_failures = [var.backend_origin_hostname]
}
run "reject_release_traversal" {
  command = plan
  variables { release_id = "../other" }
  expect_failures = [var.release_id]
}
