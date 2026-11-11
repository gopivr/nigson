mock_provider "aws" {}
variables {
  environment            = "stage"
  product_phase          = 1
  kms_key_arn            = "arn:aws:kms:us-east-1:000000000000:key/test-fixture"
  log_retention_days     = 7
  policy_revision        = "synthetic-test-policy"
  named_operator         = "synthetic-test-operator"
  alert_destination_arns = ["arn:aws:sns:us-east-1:000000000000:synthetic-test-alerts"]
  queue_names            = { commerce = { work = "test-commerce", dead = "test-commerce-dlq" } }
}
run "bp1" {
  command = plan
  assert {
    condition     = length(aws_cloudwatch_log_group.owner) == 3 && !contains(keys(aws_cloudwatch_log_group.owner), "ai")
    error_message = "AI cannot be introduced before BP5."
  }
  assert {
    condition     = alltrue([for alarm in aws_cloudwatch_metric_alarm.foundation : alarm.treat_missing_data == "missing"]) && aws_cloudwatch_metric_alarm.queue_age["commerce"].threshold == 120 && aws_cloudwatch_metric_alarm.dead_letters["commerce"].threshold == 0
    error_message = "Queue and unavailable telemetry must retain safe alarms."
  }
}
run "bp5" {
  command = plan
  variables { product_phase = 5 }
  assert {
    condition     = length(aws_cloudwatch_log_group.owner) == 4 && aws_cloudwatch_metric_alarm.domain["ai-budget-cutoff"].threshold == 1
    error_message = "BP5 requires AI and its budget alarms."
  }
}
run "operator_required" {
  command = plan
  variables { named_operator = "" }
  expect_failures = [var.named_operator]
}
run "destination_required" {
  command = plan
  variables { alert_destination_arns = [] }
  expect_failures = [var.alert_destination_arns]
}

run "policy_required" {
  command = plan
  variables { policy_revision = "" }
  expect_failures = [var.policy_revision]
}
run "sampling_and_alarm_thresholds" {
  command = plan
  assert {
    condition     = aws_cloudwatch_metric_alarm.http_errors["commerce"].threshold == 1 && aws_cloudwatch_metric_alarm.http_errors["commerce"].evaluation_periods == 5 && aws_cloudwatch_metric_alarm.foundation["commerce-pool"].threshold == 0.8 && aws_cloudwatch_metric_alarm.foundation["commerce-pool"].evaluation_periods == 10
    error_message = "Master alarm thresholds must be preserved."
  }
  assert {
    condition     = yamldecode(output.collector_configuration).processors.tail_sampling.policies[2].probabilistic.sampling_percentage == 10 && yamldecode(output.collector_configuration).processors.memory_limiter.limit_mib == 256
    error_message = "Collector trace sampling and memory must remain bounded."
  }
}

run "encryption_required" {
  command = plan
  variables { kms_key_arn = "" }
  expect_failures = [var.kms_key_arn]
}
