mock_provider "aws" {}

variables {
  environment        = "stage"
  region             = "eu-west-1"
  policy_revision    = "network-v1"
  vpc_cidr           = "10.40.0.0/16"
  availability_zones = ["eu-west-1a", "eu-west-1b"]
  subnet_cidrs = {
    eu-west-1a = { edge = "10.40.0.0/24", application = "10.40.10.0/24", data = "10.40.20.0/24" }
    eu-west-1b = { edge = "10.40.1.0/24", application = "10.40.11.0/24", data = "10.40.21.0/24" }
  }
}

run "private_topology_plan" {
  command = apply

  assert {
    condition     = length(aws_nat_gateway.egress) == 2
    error_message = "Application egress must be available through a per-AZ NAT gateway."
  }
  assert {
    condition     = length(aws_route_table.data) == 2 && alltrue([for table in aws_route_table.data : length(table.route) == 0])
    error_message = "Data subnets must remain isolated from internet/NAT routes."
  }
  assert {
    condition     = alltrue([for subnet in aws_subnet.edge : subnet.map_public_ip_on_launch == false])
    error_message = "No subnet may assign public IPs to launched workloads."
  }
}

run "reject_public_vpc_range" {
  command = plan
  variables {
    vpc_cidr = "8.8.0.0/16"
  }
  expect_failures = [var.vpc_cidr]
}
