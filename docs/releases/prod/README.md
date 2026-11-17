# Production release manifests

This directory holds reviewed production release and rollback manifests only.

- `release-<id>.json` declares `"environment": "prod"`, the production cluster,
  successful owner-migration task receipts, immutable production task
  definitions, `nigson/prod/*` image digests, and HTTPS health URLs.
- `rollback-<id>.json` declares `"environment": "prod"` and the previously
  reviewed task definition for every service in the release manifest.
- A production dispatch requires the protected `prod` GitHub Environment
  approval. Never commit credentials or customer data here, and never copy a
  stage manifest into this directory.
