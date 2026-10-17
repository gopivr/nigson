# Network module

Creates a two-or-more-AZ VPC with separate edge, application and isolated data
subnets. Only edge subnets have an internet-gateway route. Application subnets
use one NAT gateway per AZ and private interface endpoints for named AWS APIs;
data subnets have no default route. Subnets never auto-assign public addresses.
Endpoint HTTPS access is restricted to the configured application subnet CIDRs.

The caller must supply the approved region, AZs, non-overlapping CIDRs and policy
revision. CIDR overlap across different prefixes and the AWS service availability
of requested endpoints are confirmed by a reviewed plan in the target region.
NAT gateways have material recurring cost; no Terraform apply is performed by
module tests.
