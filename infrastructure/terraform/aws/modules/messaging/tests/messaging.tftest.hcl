mock_provider "aws" {
  mock_resource "aws_sns_topic" {
    defaults = { arn = "arn:aws:sns:eu-west-1:123456789012:nigson-stage-domain-events" }
  }
  mock_resource "aws_sqs_queue" {
    defaults = {
      arn = "arn:aws:sqs:eu-west-1:123456789012:nigson-stage-queue"
      url = "https://sqs.eu-west-1.amazonaws.com/123456789012/nigson-stage-queue"
    }
  }
}

variables {
  environment   = "stage"
  product_phase = 1
  kms_key_arn   = "arn:aws:kms:eu-west-1:123456789012:key/01234567-89ab-cdef-0123-456789abcdef"
  publisher_roles = {
    commerce   = "nigson-stage-commerce-task"
    experience = "nigson-stage-experience-task"
  }
  consumer_roles = {
    integration-work       = "nigson-stage-integration-task"
    experience-projections = "nigson-stage-experience-task"
    commerce-domain        = "nigson-stage-commerce-task"
    unregistered           = "nigson-stage-unregistered-task"
  }
}

run "bp1_fanout_is_encrypted_filtered_and_role_scoped" {
  command = apply

  assert {
    condition     = aws_sns_topic.domain.kms_master_key_id == var.kms_key_arn && aws_sns_topic.domain.name == "nigson-${var.environment}-domain-events"
    error_message = "Domain events must use one KMS-encrypted SNS standard topic."
  }
  assert {
    condition     = length(aws_sqs_queue.consumer) == 3 && length(aws_sqs_queue.dead) == 3 && alltrue([for name in ["commerce-domain", "experience-projections", "integration-work"] : contains(keys(aws_sqs_queue.consumer), name) && contains(keys(aws_sqs_queue.dead), name)])
    error_message = "BP1 may create only the three declared consumer queues and paired DLQs."
  }
  assert {
    condition     = alltrue([for queue in values(aws_sqs_queue.consumer) : queue.kms_master_key_id == var.kms_key_arn && queue.visibility_timeout_seconds == 120 && queue.receive_wait_time_seconds == 20]) && alltrue([for queue in values(aws_sqs_queue.dead) : queue.kms_master_key_id == var.kms_key_arn && queue.message_retention_seconds == 1209600])
    error_message = "Work and dead-letter queues must use KMS, bounded polling and explicit retention."
  }
  assert {
    condition     = alltrue([for name, subscription in aws_sns_topic_subscription.consumer : subscription.raw_message_delivery && subscription.filter_policy_scope == "MessageBody" && jsondecode(subscription.filter_policy) == local.filters[name]])
    error_message = "Every queue subscription must use the exact phase-owned event filter and raw message envelope."
  }
  assert {
    condition     = jsondecode(aws_sqs_queue.consumer["integration-work"].redrive_policy).maxReceiveCount == 5 && jsondecode(aws_sqs_queue.consumer["integration-work"].redrive_policy).deadLetterTargetArn == aws_sqs_queue.dead["integration-work"].arn
    error_message = "Consumer failures must reach their own bounded retry DLQ."
  }
  assert {
    condition     = alltrue([for policy in values(aws_sqs_queue_policy.subscription) : jsondecode(policy.policy).Statement[0].Action == "sqs:SendMessage" && jsondecode(policy.policy).Statement[0].Condition.ArnEquals["aws:SourceArn"] == aws_sns_topic.domain.arn])
    error_message = "Only the declared SNS topic may deliver to a consumer queue."
  }
  assert {
    condition     = alltrue([for policy in values(aws_iam_role_policy.publisher) : jsondecode(policy.policy).Statement[0].Action == ["sns:Publish"] && jsondecode(policy.policy).Statement[0].Resource == aws_sns_topic.domain.arn])
    error_message = "Publisher task roles may publish only to the owner event topic."
  }
  assert {
    condition     = length(aws_iam_role_policy.consumer) == 3 && !contains(keys(aws_iam_role_policy.consumer), "unregistered") && alltrue([for name, policy in aws_iam_role_policy.consumer : jsondecode(policy.policy).Statement[0].Resource == aws_sqs_queue.consumer[name].arn && jsondecode(policy.policy).Statement[1].Resource == aws_sqs_queue.dead[name].arn])
    error_message = "Consumers may read only their own queue and send only to its paired DLQ."
  }
}

run "ai_consumer_is_phase_five_only" {
  command = apply
  variables {
    product_phase = 5
    consumer_roles = {
      integration-work       = "nigson-stage-integration-task"
      experience-projections = "nigson-stage-experience-task"
      commerce-domain        = "nigson-stage-commerce-task"
      ai-ingestion           = "nigson-stage-ai-task"
    }
  }

  assert {
    condition     = contains(keys(aws_sqs_queue.consumer), "ai-ingestion") && contains(keys(aws_iam_role_policy.consumer), "ai-ingestion")
    error_message = "AI queue and IAM access must exist only in BP5."
  }
  assert {
    condition     = jsondecode(aws_sns_topic_subscription.consumer["ai-ingestion"].filter_policy) == local.filters["ai-ingestion"]
    error_message = "AI may receive only the exact BP5 public-source change event filter."
  }
}
