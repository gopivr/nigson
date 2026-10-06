terraform {
  required_version = ">= 1.12.2, < 2.0.0"
  required_providers { aws = { source = "hashicorp/aws", version = "6.15.0" } }
}
variable "environment" { type = string }
variable "kms_key_arn" { type = string }
variable "product_phase" {
  type    = number
  default = 1
  validation {
    condition     = contains([1, 2, 3, 4, 5], var.product_phase)
    error_message = "Product phase must be BP1 through BP5."
  }
}
variable "publisher_roles" { type = map(string) }
variable "consumer_roles" { type = map(string) }
locals {
  filters = { for name, policy in jsondecode(file("${path.module}/filters.json"))[tostring(var.product_phase)] : name => policy }
}
resource "aws_sns_topic" "domain" {
  name              = "nigson-${var.environment}-domain-events"
  kms_master_key_id = var.kms_key_arn
}
resource "aws_sqs_queue" "dead" {
  for_each                  = local.filters
  name                      = "nigson-${var.environment}-${each.key}-dlq"
  message_retention_seconds = 1209600
  kms_master_key_id         = var.kms_key_arn
}
resource "aws_sqs_queue" "consumer" {
  for_each                   = local.filters
  name                       = "nigson-${var.environment}-${each.key}"
  visibility_timeout_seconds = 120
  receive_wait_time_seconds  = 20
  message_retention_seconds  = 345600
  kms_master_key_id          = var.kms_key_arn
  redrive_policy             = jsonencode({ deadLetterTargetArn = aws_sqs_queue.dead[each.key].arn, maxReceiveCount = 5 })
}
resource "aws_sqs_queue_policy" "subscription" {
  for_each  = local.filters
  queue_url = aws_sqs_queue.consumer[each.key].url
  policy    = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "sns.amazonaws.com" }, Action = "sqs:SendMessage", Resource = aws_sqs_queue.consumer[each.key].arn, Condition = { ArnEquals = { "aws:SourceArn" = aws_sns_topic.domain.arn } } }] })
}
resource "aws_sns_topic_subscription" "consumer" {
  for_each             = local.filters
  topic_arn            = aws_sns_topic.domain.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.consumer[each.key].arn
  raw_message_delivery = true
  filter_policy_scope  = "MessageBody"
  filter_policy        = jsonencode(each.value)
  depends_on           = [aws_sqs_queue_policy.subscription]
}
resource "aws_iam_role_policy" "publisher" {
  for_each = var.publisher_roles
  role     = each.value
  policy   = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Action = ["sns:Publish"], Resource = aws_sns_topic.domain.arn }, { Effect = "Allow", Action = ["kms:GenerateDataKey", "kms:Decrypt"], Resource = var.kms_key_arn }] })
}
resource "aws_iam_role_policy" "consumer" {
  for_each = { for name, role in var.consumer_roles : name => role if contains(keys(local.filters), name) }
  role     = each.value
  policy   = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Action = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility", "sqs:GetQueueAttributes"], Resource = aws_sqs_queue.consumer[each.key].arn }, { Effect = "Allow", Action = ["sqs:SendMessage"], Resource = aws_sqs_queue.dead[each.key].arn }, { Effect = "Allow", Action = ["kms:Decrypt", "kms:GenerateDataKey"], Resource = var.kms_key_arn }] })
}
output "topic_arn" { value = aws_sns_topic.domain.arn }
output "queues" { value = { for name, q in aws_sqs_queue.consumer : name => q.url } }
