terraform {
  required_version = "= 1.12.2"
  required_providers { aws = { source = "hashicorp/aws", version = "6.15.0" } }
}
module "observability" {
  source                 = "../../../../modules/observability"
  environment            = "stage"
  product_phase          = 1
  kms_key_arn            = "arn:aws:kms:eu-west-1:123456789012:key/01234567-89ab-cdef-0123-456789abcdef"
  log_retention_days     = 30
  policy_revision        = "legacy-test"
  named_operator         = "first-account-operator"
  alert_destination_arns = ["arn:aws:sns:eu-west-1:123456789012:nigson-stage-alerts"]
  queue_names            = {}
}
