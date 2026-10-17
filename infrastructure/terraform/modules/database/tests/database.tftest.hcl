mock_provider "aws" {
  mock_resource "aws_db_instance" {
    defaults = {
      master_user_secret = [{ secret_arn = "arn:aws:secretsmanager:eu-west-1:123456789012:secret:mock-rds-master" }]
    }
  }
}

variables {
  environment                = "prod"
  policy_revision            = "db-v1"
  product_phase              = 1
  vpc_id                     = "vpc-0123456789abcdef0"
  endpoint_security_group_id = "sg-endpoints"
  data_subnet_ids            = ["subnet-data-a", "subnet-data-b"]
  owner_task_security_groups = {
    commerce    = "sg-0123456789abcdef0"
    experience  = "sg-1123456789abcdef0"
    integration = "sg-2123456789abcdef0"
  }
  kms_key_arn               = "arn:aws:kms:eu-west-1:123456789012:key/01234567-89ab-cdef-0123-456789abcdef"
  engine_version            = "18.1"
  instance_class            = "db.r7g.large"
  allocated_storage_gib     = 100
  max_allocated_storage_gib = 500
  backup_retention_days     = 14
  log_retention_days        = 90
  multi_az                  = false
  deletion_protection       = false
}

run "production_database_is_private_and_protected" {
  command = apply

  assert {
    condition     = aws_db_instance.database.publicly_accessible == false
    error_message = "RDS must remain private."
  }
  assert {
    condition     = aws_db_instance.database.storage_encrypted == true && aws_db_instance.database.deletion_protection == true && aws_db_instance.database.multi_az == true
    error_message = "Production database must be encrypted, Multi-AZ and deletion-protected."
  }
  assert {
    condition     = length(aws_vpc_security_group_ingress_rule.owner_tasks) == 3
    error_message = "Only explicitly named owner task groups may be admitted to PostgreSQL."
  }
  assert {
    condition     = aws_vpc_security_group_ingress_rule.migration_tasks.referenced_security_group_id == aws_security_group.migration_tasks.id && aws_vpc_security_group_egress_rule.migration_database.referenced_security_group_id == aws_security_group.database.id && length(aws_security_group.migration_tasks.ingress) == 0
    error_message = "One-shot migration tasks need private endpoint/database access and no inbound connectivity."
  }
}

run "reject_invalid_postgres_major" {
  command = plan
  variables {
    engine_version = "17.6"
  }
  expect_failures = [var.engine_version]
}
