# Nigson UI

Angular Native Federation shell and seven remotes, in the Nigson monorepo.

From the repository root, use `make ui-install`, `make ui-dev` and `make ui-test`.
Use Node 24.15.0. The dev server opens at http://localhost:4200 and proxies API
requests to the Java services.

- [Local setup](../docs/LOCAL_APPLICATION_GUIDE.md)
- [AWS deployment](../docs/DEPLOYMENT.md)
- [Frontend packaging](deployment/README.md)

`npm run build:release -- --output /absolute/path/to/new-release` builds all apps
with same-origin federation URLs. Set the real public
`NIGSON_GOOGLE_RECAPTCHA_SITE_KEY` first; the output directory must not exist.
The frontend never receives AWS keys, private reCAPTCHA keys or payment secrets.
