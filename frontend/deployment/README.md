# Production artifact

Run `npm run build:release -- --output /absolute/path/to/new-release` after `npm ci`. It builds the shell and all remotes in the federation manifest using production configuration and same-origin URLs.

AWS uploads that artifact to private S3 through the backend repository's `scripts/infra/publish-frontend.py`. OVH builds a digest-pinned image with `docker build -f deployment/Dockerfile -t REGISTRY/nigson-frontend:RELEASE /absolute/path/to/new-release`; publish it for your VM architecture.

Terraform roots and TLS/backend setup instructions live in the backend repository under `infrastructure/terraform/{aws,ovh}/frontend/{stage,prod}` and `infrastructure/terraform/shared/frontend/README.md`. No production secrets belong in the artifact.

For the current first-account staging infrastructure, use [the UI GitHub Actions deployment guide](AWS.md). The workflow builds in this repository, publishes an immutable S3 release and activates only its CloudFront release path through the existing backend-owned Terraform state.
