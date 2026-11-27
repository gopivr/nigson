terraform {
  required_providers { aws = { source = "hashicorp/aws", version = "6.15.0" } }
}
variable "release_id" { type = string }
module "frontend" {
  source     = "./distribution"
  release_id = var.release_id
}
