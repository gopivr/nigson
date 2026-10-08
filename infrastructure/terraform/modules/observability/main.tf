terraform {
  required_version = ">= 1.12.2, < 2.0.0"
  required_providers { aws = { source = "hashicorp/aws", version = "6.15.0" } }
}
variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "qa", "stage", "prod"], var.environment)
    error_message = "Explicit AWS environment required."
  }
}
variable "product_phase" {
  type = number
  validation {
    condition     = contains([1, 2, 3, 4, 5], var.product_phase)
    error_message = "BP1 through BP5 required."
  }
}
variable "kms_key_arn" {
  type = string
  validation {
    condition     = can(regex("^arn:aws[a-z-]*:kms:[a-z0-9-]+:[0-9]{12}:key/[A-Za-z0-9-]+$", var.kms_key_arn))
    error_message = "An explicit KMS key ARN is required; encryption cannot be disabled."
  }
}
variable "log_retention_days" {
  type = number
  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653], var.log_retention_days)
    error_message = "An approved CloudWatch retention duration is required."
  }
}
variable "policy_revision" {
  type = string
  validation {
    condition     = length(trimspace(var.policy_revision)) > 0
    error_message = "Approved telemetry and retention policy revision required."
  }
}
variable "named_operator" {
  type = string
  validation {
    condition     = length(trimspace(var.named_operator)) > 0
    error_message = "A named operator is required."
  }
}
variable "alert_destination_arns" {
  type = list(string)
  validation {
    condition     = length(var.alert_destination_arns) > 0 && alltrue([for arn in var.alert_destination_arns : can(regex("^arn:aws[a-z-]*:sns:", arn))])
    error_message = "Explicit operator SNS alert destinations required."
  }
}
variable "queue_names" { type = map(object({ work = string, dead = string })) }
# Domain owners must emit these observations as their roadmap tasks are implemented.
# Missing data remains INSUFFICIENT_DATA. This module never fabricates healthy domain observations.
locals {
  owners    = toset(var.product_phase == 5 ? ["commerce", "experience", "integration", "ai"] : ["commerce", "experience", "integration"])
  namespace = "Nigson/${var.environment}"
  tags      = { Operator = var.named_operator, PolicyRevision = var.policy_revision, ProductPhase = "BP${var.product_phase}" }
  foundation = { for pair in setproduct(local.owners, ["outbox", "pool"]) : "${pair[0]}-${pair[1]}" => {
    owner = pair[0], metric = pair[1] == "outbox" ? "nigson.outbox.oldest.age" : "nigson.db.pool.utilization", threshold = pair[1] == "outbox" ? 60 : 0.8, periods = pair[1] == "outbox" ? 5 : 10
  } if pair[0] != "ai" || pair[1] != "outbox" }
  domain = merge({
    payment-unknown           = { owner = "commerce", metric = "nigson.payment.unknown.age", threshold = 900, comparison = "GreaterThanThreshold" }
    stock-negative-difference = { owner = "commerce", metric = "nigson.stock.ledger.difference", threshold = 0, comparison = "LessThanThreshold" }
    stock-difference          = { owner = "commerce", metric = "nigson.stock.ledger.difference", threshold = 0, comparison = "GreaterThanThreshold" }
    refund-pending            = { owner = "commerce", metric = "nigson.refund.pending.age", threshold = 1800, comparison = "GreaterThanThreshold" }
    }, var.product_phase == 5 ? {
    ai-budget-warning = { owner = "ai", metric = "nigson.ai.daily.budget.fraction", threshold = 0.8, comparison = "GreaterThanOrEqualToThreshold" }
    ai-budget-cutoff  = { owner = "ai", metric = "nigson.ai.daily.budget.fraction", threshold = 1, comparison = "GreaterThanOrEqualToThreshold" }
  } : {})
}
resource "aws_cloudwatch_log_group" "owner" {
  for_each          = local.owners
  name              = "/nigson/${var.environment}/${each.key}"
  kms_key_id        = var.kms_key_arn
  retention_in_days = var.log_retention_days
  tags              = local.tags
}
resource "aws_cloudwatch_metric_alarm" "http_errors" {
  for_each            = local.owners
  alarm_name          = "nigson-${var.environment}-${each.key}-http-5xx"
  alarm_description   = "Operator ${var.named_operator}; API 5xx >1% for five consecutive minutes; ${var.policy_revision}"
  comparison_operator = "GreaterThanThreshold"
  threshold           = 1
  evaluation_periods  = 5
  datapoints_to_alarm = 5
  treat_missing_data  = "missing"
  alarm_actions       = var.alert_destination_arns
  ok_actions          = var.alert_destination_arns
  metric_query {
    id          = "rate"
    expression  = "IF(requests>0,100*errors/requests,0)"
    return_data = true
  }
  metric_query {
    id = "errors"
    metric {
      metric_name = "nigson.http.errors"
      namespace   = local.namespace
      period      = 60
      stat        = "Sum"
      dimensions  = { owner = each.key }
    }
  }
  metric_query {
    id = "requests"
    metric {
      metric_name = "nigson.http.requests"
      namespace   = local.namespace
      period      = 60
      stat        = "Sum"
      dimensions  = { owner = each.key }
    }
  }
  tags = local.tags
}
resource "aws_cloudwatch_metric_alarm" "foundation" {
  for_each            = local.foundation
  alarm_name          = "nigson-${var.environment}-${each.key}"
  alarm_description   = "Operator ${var.named_operator}; ${var.policy_revision}"
  namespace           = local.namespace
  metric_name         = each.value.metric
  dimensions          = { owner = each.value.owner }
  period              = 60
  statistic           = "Maximum"
  comparison_operator = "GreaterThanThreshold"
  threshold           = each.value.threshold
  evaluation_periods  = each.value.periods
  datapoints_to_alarm = each.value.periods
  treat_missing_data  = "missing"
  alarm_actions       = var.alert_destination_arns
  tags                = local.tags
}
resource "aws_cloudwatch_metric_alarm" "queue_age" {
  for_each            = var.queue_names
  alarm_name          = "nigson-${var.environment}-${each.key}-queue-age"
  alarm_description   = "Operator ${var.named_operator}; ${var.policy_revision}"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateAgeOfOldestMessage"
  dimensions          = { QueueName = each.value.work }
  period              = 60
  statistic           = "Maximum"
  comparison_operator = "GreaterThanThreshold"
  threshold           = 120
  evaluation_periods  = 5
  datapoints_to_alarm = 5
  treat_missing_data  = "missing"
  alarm_actions       = var.alert_destination_arns
  tags                = local.tags
}
resource "aws_cloudwatch_metric_alarm" "dead_letters" {
  for_each            = var.queue_names
  alarm_name          = "nigson-${var.environment}-${each.key}-dlq"
  alarm_description   = "Operator ${var.named_operator}; ${var.policy_revision}"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  dimensions          = { QueueName = each.value.dead }
  period              = 60
  statistic           = "Maximum"
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  evaluation_periods  = 1
  treat_missing_data  = "missing"
  alarm_actions       = var.alert_destination_arns
  tags                = local.tags
}
resource "aws_cloudwatch_metric_alarm" "domain" {
  for_each            = local.domain
  alarm_name          = "nigson-${var.environment}-${each.key}"
  alarm_description   = "Operator ${var.named_operator}; awaiting actual owner observations; ${var.policy_revision}"
  namespace           = local.namespace
  metric_name         = each.value.metric
  dimensions          = { owner = each.value.owner }
  period              = 60
  statistic           = "Maximum"
  comparison_operator = each.value.comparison
  threshold           = each.value.threshold
  evaluation_periods  = 1
  treat_missing_data  = "missing"
  alarm_actions       = var.alert_destination_arns
  tags                = local.tags
}
resource "aws_cloudwatch_dashboard" "operations" {
  dashboard_name = "nigson-${var.environment}-operations"
  dashboard_body = jsonencode({ widgets = concat([
    for owner in sort(tolist(local.owners)) : {
      type = "metric", width = 12, height = 6,
      properties = { title = "${owner}: HTTP and owner health", region = data.aws_region.current.region, view = "timeSeries", period = 60,
      metrics = [for metric in ["nigson.http.requests", "nigson.http.errors", "nigson.outbox.pending", "nigson.outbox.oldest.age", "nigson.db.pool.utilization", "nigson.db.pool.waiting"] : [local.namespace, metric, "owner", owner]] }
    }
    ], [for name, queue in var.queue_names : {
      type = "metric", width = 12, height = 6,
      properties = { title = "${name}: queue work and DLQ", region = data.aws_region.current.region, view = "timeSeries", period = 60,
      metrics = [["AWS/SQS", "ApproximateAgeOfOldestMessage", "QueueName", queue.work], ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", queue.work], ["AWS/SQS", "ApproximateNumberOfMessagesNotVisible", "QueueName", queue.work], ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", queue.dead]] }
  }]) })
}
data "aws_region" "current" {}
output "log_groups" { value = { for owner, group in aws_cloudwatch_log_group.owner : owner => group.name } }
output "alarm_arns" { value = concat([for alarm in aws_cloudwatch_metric_alarm.http_errors : alarm.arn], [for alarm in aws_cloudwatch_metric_alarm.foundation : alarm.arn], [for alarm in aws_cloudwatch_metric_alarm.queue_age : alarm.arn], [for alarm in aws_cloudwatch_metric_alarm.dead_letters : alarm.arn], [for alarm in aws_cloudwatch_metric_alarm.domain : alarm.arn]) }

resource "aws_cloudwatch_log_group" "metrics" {
  name              = "/nigson/${var.environment}/metrics"
  kms_key_id        = var.kms_key_arn
  retention_in_days = var.log_retention_days
  tags              = local.tags
}
output "collector_configuration" {
  value = templatefile("${path.module}/collector.yaml.tftpl", { region = data.aws_region.current.region, environment = var.environment, namespace = local.namespace, log_group = aws_cloudwatch_log_group.metrics.name })
}
output "collector_iam_policy" {
  value = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = "${aws_cloudwatch_log_group.metrics.arn}:*" },
    { Effect = "Allow", Action = ["xray:PutTraceSegments", "xray:PutTelemetryRecords"], Resource = "*" }
  ] })
}
