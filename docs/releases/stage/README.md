# Stage release manifests

This directory holds reviewed stage release and rollback manifests only.

- `release-<id>.json` declares `"environment": "stage"`, the stage cluster,
  successful owner-migration task receipts, immutable stage task definitions,
  `nigson/stage/*` image digests, and HTTPS health URLs.
- `rollback-<id>.json` declares `"environment": "stage"` and the previously
  reviewed task definition for every service in the release manifest.
- Never commit credentials, account identifiers beyond the resource ARNs, or
  customer data here. Use `docs/releases/prod/` for production.
