terraform {
  required_version = "= 1.12.2"
  required_providers {
    aws = { source = "hashicorp/aws", version = "6.15.0" }
  }
}
locals {
  administrators = [{ Sid = "NamedKeyAdministration", Effect = "Allow", Principal = { AWS = var.key_administrator_arns }, Action = ["kms:DescribeKey", "kms:GetKeyPolicy", "kms:PutKeyPolicy", "kms:EnableKey", "kms:DisableKey", "kms:UpdateKeyDescription", "kms:ScheduleKeyDeletion", "kms:CancelKeyDeletion", "kms:ListResourceTags", "kms:TagResource", "kms:UntagResource", "kms:EnableKeyRotation", "kms:DisableKeyRotation", "kms:GetKeyRotationStatus"], Resource = "*" }]
}
resource "aws_kms_key" "actor" {
  description              = "Nigson ${var.environment} commerce actor delegation ${var.policy_revision}"
  key_usage                = "SIGN_VERIFY"
  customer_master_key_spec = "ECC_NIST_P256"
  deletion_window_in_days  = var.key_deletion_window_days
  policy = jsonencode({ Version = "2012-10-17", Statement = concat(local.administrators, [{
    Sid       = "CommerceActorSigning", Effect = "Allow", Principal = { AWS = var.commerce_task_role_arn }, Action = ["kms:Sign"], Resource = "*",
    Condition = { StringEquals = { "kms:SigningAlgorithm" = "ECDSA_SHA_256", "kms:MessageType" = "DIGEST" } }
  }]) })
  lifecycle { prevent_destroy = true }
}
resource "aws_kms_key" "session" {
  description             = "Nigson ${var.environment} commerce session encryption ${var.policy_revision}"
  enable_key_rotation     = true
  deletion_window_in_days = var.key_deletion_window_days
  policy = jsonencode({ Version = "2012-10-17", Statement = concat(local.administrators, [{
    Sid       = "CommerceSessionUnwrap", Effect = "Allow", Principal = { AWS = var.commerce_task_role_arn }, Action = ["kms:Decrypt"], Resource = "*",
    Condition = { StringEquals = { "kms:EncryptionContext:purpose" = "nigson-commerce-session" }, Null = { "kms:EncryptionContext:revision" = "false" } }
    }, {
    Sid       = "NamedSessionKeyProvisioning", Effect = "Allow", Principal = { AWS = var.session_key_provisioner_arn }, Action = ["kms:Encrypt", "kms:GenerateDataKey"], Resource = "*",
    Condition = { StringEquals = { "kms:EncryptionContext:purpose" = "nigson-commerce-session" }, Null = { "kms:EncryptionContext:revision" = "false" } }
  }]) })
  lifecycle { prevent_destroy = true }
}
resource "aws_iam_role_policy" "commerce" {
  name = "nigson-private-identity-${var.policy_revision}"
  role = var.commerce_task_role_name
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["kms:Sign"], Resource = aws_kms_key.actor.arn, Condition = { StringEquals = { "kms:SigningAlgorithm" = "ECDSA_SHA_256", "kms:MessageType" = "DIGEST" } } },
    { Effect = "Allow", Action = ["kms:Decrypt"], Resource = aws_kms_key.session.arn, Condition = { StringEquals = { "kms:EncryptionContext:purpose" = "nigson-commerce-session" }, Null = { "kms:EncryptionContext:revision" = "false" } } }
  ] })
}
resource "aws_vpc_security_group_ingress_rule" "private_hops" {
  for_each                     = var.private_hops
  security_group_id            = each.value.target_security_group_id
  referenced_security_group_id = each.value.source_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = each.value.target_port
  to_port                      = each.value.target_port
  description                  = "Nigson compiled private operation ${each.key}"
}
output "actor_kms_key_arn" { value = aws_kms_key.actor.arn }
output "session_kms_key_arn" { value = aws_kms_key.session.arn }
output "private_peer_contract" {
  value = "Application independently verifies configured socket-peer CIDRs and exact workload/actor operations; public ALB must block /internal/**."
}
