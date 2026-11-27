# Frontend release packaging

See [AWS deployment](../../docs/DEPLOYMENT.md) for the normal GitHub Actions flow.
The root `.github/workflows/aws-stage-ui.yml` owns staging UI builds/deployments.

For a manual local artifact, from `frontend/`:

```sh
npm ci
# Set NIGSON_GOOGLE_RECAPTCHA_SITE_KEY to the real public browser site key first.
npm run build:release -- --output /absolute/path/to/new-release
```

The directory must be new. The build contains the shell and every configured
remote, using same-origin federation URLs. Do not put runtime credentials in it.
AWS publication uses the root `scripts/infra/` helpers and existing Terraform state.
The Dockerfile/nginx configuration in this directory remains available for the
optional container-based frontend deployment.
