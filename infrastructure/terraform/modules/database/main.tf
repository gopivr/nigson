terraform {
  required_version = "= 1.12.2"
  required_providers {
    aws = { source = "hashicorp/aws", version = "6.15.0" }
  }
}

locals {
  tags = {
    Application = "nigson"
    Environment = var.environment
    ManagedBy   = "terraform"
    Policy      = var.policy_revision
  }
}

resource "aws_security_group" "database" {
  name        = "nigson-${var.environment}-commerce-database"
  description = "Private PostgreSQL ingress from explicitly named commerce owners"
  vpc_id      = var.vpc_id
  egress      = []
  tags        = local.tags
}

resource "aws_vpc_security_group_ingress_rule" "owner_tasks" {
  for_each                     = var.owner_task_security_groups
  security_group_id            = aws_security_group.database.id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "PostgreSQL from owner task ${each.key}"
}

resource "aws_db_subnet_group" "database" {
  name       = "nigson-${var.environment}-commerce"
  subnet_ids = var.data_subnet_ids
  tags       = local.tags
}

resource "aws_db_parameter_group" "database" {
  name   = "nigson-${var.environment}-postgres18"
  family = "postgres18"
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
  parameter {
    name  = "log_statement"
    value = "none"
  }
  parameter {
    name  = "log_min_error_statement"
    value = "panic"
  }
  tags = local.tags
}

resource "aws_cloudwatch_log_group" "database" {
  name              = "/nigson/${var.environment}/database/postgresql"
  kms_key_id        = var.kms_key_arn
  retention_in_days = var.log_retention_days
  tags              = local.tags
}

resource "aws_db_instance" "database" {
  identifier                      = "nigson-${var.environment}-commerce"
  engine                          = "postgres"
  engine_version                  = var.engine_version
  instance_class                  = var.instance_class
  allocated_storage               = var.allocated_storage_gib
  max_allocated_storage           = var.max_allocated_storage_gib
  storage_type                    = "gp3"
  storage_encrypted               = true
  kms_key_id                      = var.kms_key_arn
  username                        = "nigson_rds_admin"
  manage_master_user_password     = true
  master_user_secret_kms_key_id   = var.kms_key_arn
  db_name                         = "nigson"
  multi_az                        = var.environment == "prod" ? true : var.multi_az
  publicly_accessible             = false
  db_subnet_group_name            = aws_db_subnet_group.database.name
  vpc_security_group_ids          = [aws_security_group.database.id]
  parameter_group_name            = aws_db_parameter_group.database.name
  backup_retention_period         = var.backup_retention_days
  copy_tags_to_snapshot           = true
  deletion_protection             = var.environment == "prod" ? true : var.deletion_protection
  skip_final_snapshot             = false
  final_snapshot_identifier       = "nigson-${var.environment}-commerce-operator-final"
  auto_minor_version_upgrade      = false
  allow_major_version_upgrade     = false
  enabled_cloudwatch_logs_exports = ["postgresql"]
  depends_on                      = [aws_cloudwatch_log_group.database]
  tags                            = local.tags
}

output "database_address" { value = aws_db_instance.database.address }
output "database_port" { value = aws_db_instance.database.port }
output "database_security_group_id" { value = aws_security_group.database.id }
output "master_secret_arn" { value = aws_db_instance.database.master_user_secret[0].secret_arn }
