terraform {
  required_version = "= 1.12.2"
  required_providers {
    aws = { source = "hashicorp/aws", version = "6.15.0" }
  }
}

locals {
  azs               = toset(var.availability_zones)
  application_cidrs = [for az in var.availability_zones : var.subnet_cidrs[az].application]
  tags = {
    Application = "nigson"
    Environment = var.environment
    ManagedBy   = "terraform"
    Policy      = var.policy_revision
  }
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = merge(local.tags, { Name = "nigson-${var.environment}" })
}

resource "aws_internet_gateway" "edge" {
  vpc_id = aws_vpc.main.id
  tags   = local.tags
}

resource "aws_subnet" "edge" {
  for_each                = local.azs
  vpc_id                  = aws_vpc.main.id
  availability_zone       = each.key
  cidr_block              = var.subnet_cidrs[each.key].edge
  map_public_ip_on_launch = false
  tags                    = merge(local.tags, { Tier = "edge", Name = "nigson-${var.environment}-${each.key}-edge" })
}

resource "aws_subnet" "application" {
  for_each                = local.azs
  vpc_id                  = aws_vpc.main.id
  availability_zone       = each.key
  cidr_block              = var.subnet_cidrs[each.key].application
  map_public_ip_on_launch = false
  tags                    = merge(local.tags, { Tier = "application", Name = "nigson-${var.environment}-${each.key}-app" })
}

resource "aws_subnet" "data" {
  for_each                = local.azs
  vpc_id                  = aws_vpc.main.id
  availability_zone       = each.key
  cidr_block              = var.subnet_cidrs[each.key].data
  map_public_ip_on_launch = false
  tags                    = merge(local.tags, { Tier = "data", Name = "nigson-${var.environment}-${each.key}-data" })
}

resource "aws_route_table" "edge" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.edge.id
  }
  tags = merge(local.tags, { Tier = "edge" })
}

resource "aws_route_table_association" "edge" {
  for_each       = local.azs
  subnet_id      = aws_subnet.edge[each.key].id
  route_table_id = aws_route_table.edge.id
}

resource "aws_eip" "nat" {
  for_each = local.azs
  domain   = "vpc"
  tags     = merge(local.tags, { Name = "nigson-${var.environment}-${each.key}-nat" })
}

resource "aws_nat_gateway" "egress" {
  for_each      = local.azs
  allocation_id = aws_eip.nat[each.key].id
  subnet_id     = aws_subnet.edge[each.key].id
  depends_on    = [aws_internet_gateway.edge]
  tags          = merge(local.tags, { Name = "nigson-${var.environment}-${each.key}-egress" })
}

resource "aws_route_table" "application" {
  for_each = local.azs
  vpc_id   = aws_vpc.main.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.egress[each.key].id
  }
  tags = merge(local.tags, { Tier = "application", AvailabilityZone = each.key })
}

resource "aws_route_table_association" "application" {
  for_each       = local.azs
  subnet_id      = aws_subnet.application[each.key].id
  route_table_id = aws_route_table.application[each.key].id
}

# Data subnets intentionally have no default route or NAT path.
resource "aws_route_table" "data" {
  for_each = local.azs
  vpc_id   = aws_vpc.main.id
  tags     = merge(local.tags, { Tier = "data", AvailabilityZone = each.key })
}

resource "aws_route_table_association" "data" {
  for_each       = local.azs
  subnet_id      = aws_subnet.data[each.key].id
  route_table_id = aws_route_table.data[each.key].id
}

resource "aws_security_group" "endpoints" {
  name        = "nigson-${var.environment}-vpc-endpoints"
  description = "HTTPS from application tier to private AWS endpoints"
  vpc_id      = aws_vpc.main.id
  ingress {
    description = "Application workloads to interface endpoints"
    protocol    = "tcp"
    from_port   = 443
    to_port     = 443
    cidr_blocks = local.application_cidrs
  }
  egress {
    protocol    = "-1"
    from_port   = 0
    to_port     = 0
    cidr_blocks = local.application_cidrs
  }
  tags = local.tags
}

resource "aws_vpc_endpoint" "interface" {
  for_each            = var.interface_endpoints
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${var.region}.${each.key}"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = [for az in sort(tolist(local.azs)) : aws_subnet.application[az].id]
  security_group_ids  = [aws_security_group.endpoints.id]
  tags                = merge(local.tags, { Service = each.key })
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = concat(values(aws_route_table.application)[*].id, values(aws_route_table.data)[*].id)
  tags              = merge(local.tags, { Service = "s3" })
}

output "vpc_id" { value = aws_vpc.main.id }
output "vpc_cidr" { value = aws_vpc.main.cidr_block }
output "edge_subnet_ids" { value = [for az in sort(tolist(local.azs)) : aws_subnet.edge[az].id] }
output "application_subnet_ids" { value = [for az in sort(tolist(local.azs)) : aws_subnet.application[az].id] }
output "data_subnet_ids" { value = [for az in sort(tolist(local.azs)) : aws_subnet.data[az].id] }
output "endpoint_security_group_id" { value = aws_security_group.endpoints.id }
