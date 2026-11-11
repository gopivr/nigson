# ADR-025: Optional OVHcloud deployment

User decision, 10 November 2026: retain AWS Terraform and add separate OVHcloud Terraform. AWS roots move under `infrastructure/terraform/aws` without changing remote state keys or resource addresses. OVH uses OpenStack VMs, private networking, an Octavia load balancer, managed PostgreSQL and private object storage. Kubernetes is not introduced. Commerce/Experience/Integration ownership and Keycloak remain unchanged.

This authorizes infrastructure code, not a cloud apply. Application SNS/SQS, KMS, AWS storage and workload credentials are separate migration dependencies; OVH resources do not emulate them. No mock services or dummy credentials are provisioned. Deployments require external runtime secrets, TLS, owner bootstrap/migrations and verified provider/database capabilities. OVH must use independent state; never initialize it against AWS state.
