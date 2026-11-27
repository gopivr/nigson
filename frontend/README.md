# Nigson Microfrontend

This Angular Native Federation workspace is the browser UI for the Java Nigson backend. It does not run a Node API or CMS database bridge. Angular’s development proxy forwards `/bff` and Commerce routes to `localhost:8081`, public Experience routes to `localhost:8082`, and LocalStack assets to `localhost:4566`.

## Run locally

1. Start the Java services and local dependencies from the backend workspace using its local application guide. Commerce is expected on port `8081`, Experience on `8082`; LocalStack is optional for image assets.
2. Install frontend dependencies and start the MFE from this directory:

   ```bash
   npm ci
   npm start
   ```

3. Open `http://localhost:4200`. Run `npm run start:all` when using the admin routes: they require the `admin-core` remote on port4205 and the `admin-shop` inventory remote on port4206 in addition to the shell. Every Angular serve target uses the same Java proxy. If a remote was started after the shell page was opened, refresh the page so federation initializes that remote.

The backend Compose foundation profile disables BFF sessions by default. For sign-in, configure the complete local Commerce BFF session/security profile, register its callback as `http://localhost:4200/bff/callback`, and allow `http://localhost:4200` in `NIGSON_BFF_ALLOWED_ORIGINS`. If using `npm run start:all`, also allow each Angular remote origin in use (`http://localhost:4201` through `http://localhost:4206` and `http://localhost:4211`). The browser redirects to `/bff/login`; Keycloak returns to the Java BFF, which sets the server-side session cookie and redirects back into the Angular app. The signup page collects account details in the Nigson UI and sends them to the local-only Commerce `/bff/register` endpoint, which provisions users through a separate Keycloak service account. Passwords are never stored by the frontend or Commerce. The frontend does not store access tokens or role grants.

The local `nigson-local` realm enables self-registration. The signup screen starts the same BFF authorization-code flow as sign-in; choose **Register** on Keycloak's sign-in page. Existing Keycloak data volumes are not overwritten by realm import, so enable **Realm settings → Login → User registration** and add the exact callback URI to the `nigson-bff` client's valid redirect URIs when upgrading an existing local realm. Password reset stays disabled until local email delivery is configured.

Public lead submissions use Google reCAPTCHA v3 and fail closed when no token can be obtained. Production release builds require the public `NIGSON_GOOGLE_RECAPTCHA_SITE_KEY` environment value:

```bash
NIGSON_GOOGLE_RECAPTCHA_SITE_KEY=your-public-site-key python3 scripts/deploy/build-release.py --output /path/to/new-release
```

Configure the matching reCAPTCHA secret, allowed hostnames, database rate-limit mode/key, and lead recipient on the backend. The site key is public; the secret and rate-limit HMAC key must be supplied through the backend secret manager. Do not use the test-only HMAC token in local application requests.

## API ownership

- Commerce and `/bff`: catalogue, market, cart, checkout, orders, account and Commerce staff endpoints.
- Experience: public content, enquiries, newsletter, careers, channels, campaigns and quote requests.
- AI: the shell assistant uses the Java AI service on port `8084`, with an anonymous conversation capability held only in memory.
- Review reads go to Experience; review submissions and moderation go through Commerce’s BFF relay.
- Protected Experience paths are proxied to Experience for correct service ownership, but the browser does not send actor assertions. The current Java Commerce BFF has no relay for distributor applications, quote requests, lead queues, or career application administration, so those calls correctly receive an authorization error until the corresponding trusted BFF routes are implemented.

`npm run build` builds the shell; `npm run build:all` builds the configured shell and remote applications. The AI Compose profile is optional. The assistant returns the service’s feature-disabled response until its conversation TTL/market, provider, policy and knowledge configuration are supplied. The Angular proxy is for local development only. Deployments must provide an equivalent same-origin reverse proxy for these Java paths and preserve the BFF session cookie and CSRF header. Use the backend API contracts and local guide for provider/security setup; no carrier, price, tax, sender, or identity configuration is supplied by the frontend.


## Staff portal access

All staff use the shell's /admin portal. The route entrance, layout, sidebar and child routes share the same owner-aligned section policy. The account menu exposes Staff Portal to recognized staff; a normal login without an explicit destination selects a role-specific landing page. Authorized deep links survive the BFF round trip through a validated, one-use navigation marker in sessionStorage (no credentials or role grants are stored).

| Role | Available sections under current backend permissions |
| --- | --- |
| ADMIN | Dashboard and staff/role management; domain sections follow additional explicitly assigned roles |
| SALES | Quotes, distributor applications, customers and enquiries |
| OPERATIONS | Orders, stock inventory and adjustments |
| FINANCE | Orders (existing backend recent-MFA policy still applies) |
| CONTENT_EDITOR | Products, CMS drafts/pages and blog drafts |
| CONTENT_PUBLISHER | Review moderation |
| ANALYST | Commerce analytics report |

Multiple roles combine their sections. Customers cannot enter the staff portal. User/role management remains ADMIN-only, and hidden links cannot be bypassed by entering a direct URL. Backend authorization is unchanged.

Current API boundaries matter: catalogue reads require CONTENT_EDITOR; operational inventory can be viewed using its actual SKU identifiers without that grant. CMS draft reads require CONTENT_EDITOR, while publication also requires CONTENT_PUBLISHER. Publisher-only users therefore land on review moderation; give both content roles only when that combined authority is intended. FINANCE alone does not grant ANALYST reports. These requirements are reflected in page links, data loads and action controls rather than inferred or bypassed.

Details and verification: [Staff portal access implementation](docs/2026-11-14-STAFF-PORTAL-ACCESS.md).

AWS staging CI/CD is defined in `.github/workflows/aws-stage-ui.yml`. See [deployment/AWS.md](deployment/AWS.md) for the required UI repository secrets/public site-key variable and infrastructure preparation.
