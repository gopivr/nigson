# Local demo API validation

Sign in at http://localhost:4200 using `demo.validator@example.invalid` and the local `.env` value of `KEYCLOAK_FIXTURE_PASSWORD`. This is a local synthetic account, not a production user. The password is not stored in this guide or provisioning script.

The account is linked to the seeded Commerce party `cbf263f6-a371-59b0-843b-96d34a6cdbdd`. It has CUSTOMER plus ADMIN, CONTENT_EDITOR, CONTENT_PUBLISHER, SALES, OPERATIONS, FINANCE and ANALYST. These are active Commerce-owned business grants; Keycloak realm roles alone are not API permissions.

After signing in, run these requests from the browser console on the application origin:

```js
await fetch('/bff/session').then(r => r.json());
await fetch('/api/v1/me').then(r => r.json());
await fetch('/api/v1/me/addresses').then(r => r.json());
await fetch('/api/v1/staff/products').then(r => r.json());
await fetch('/api/v1/orders').then(r => r.json());
await fetch('/api/v1/staff/orders').then(r => r.json());
```

The seeded product is `Demo Solar Panel`; the order number is `DEMO-ORDER-001`. Staff catalogue APIs include draft products. The order is cancelled with a failed payment, so successful processing/refund/payment transitions must not be expected from that record. The seeded demo price book remains disabled. The local NG market is enabled for catalogue visibility; a login does not activate checkout, tax or delivery rules.

For Postman or another API client, use base URL `http://localhost:4200` and copy your own `nigson-local-session` cookie from the browser's application storage into that client's localhost cookie jar. The BFF accepts its session cookie, not a Keycloak bearer token. Get `/bff/session` to obtain the current `csrfToken`. Mutations require `X-CSRF-TOKEN`, `Content-Type: application/json`, and `Origin: http://localhost:4200`; individual APIs may also require `Idempotency-Key` and `If-Match` from the current resource version. Keep cookie and CSRF values out of shared collections and screenshots.

Sensitive APIs continue to require recent real MFA. Roles do not bypass MFA, object ownership, state validation or release gates. This account does not fabricate an MFA assertion. Integration and Experience private owner APIs require workload credentials and signed actor assertions; they cannot all be called directly with a browser login. Several Experience routes currently point directly to their owner in the frontend development proxy, which does not itself provide a signed Commerce relay. AI and later-release APIs are not installed in this BP1 environment.

To recreate/verify this local account setup after seeding:

```sh
python3 scripts/local/seed_demo.py --apply
python3 scripts/local/provision_demo_user.py
```

The provisioning script requires the `nigson-local` project, local APP_ENV, a local Docker endpoint and the existing local registration service account. It does not reset an existing password. It records the created Keycloak identity ID in ignored `.build/demo-validator-identity.json` and refuses to take over a different existing account if that ID is absent or mismatched. Keep that non-secret ID record while reusing the local realm. Only synthetic `created_by='demo-seed-v1'` records are linked; unrelated customers and identities are preserved.

## Electronics inventory

Run `python3 scripts/local/seed_electronics.py --publish` to add and publish 24 synthetic electronics products in eight categories: Laptops, Smartphones, Tablets, Audio, Gaming, Networking, Storage, and Power and Accessories. This adds 24 SKUs and stock balances in the active Demo Electronics Lagos Warehouse, with matching opening stock movements. There are 489 units, five low-stock products and two out-of-stock products. Repeating the command preserves existing rows and stock adjustments.

Open http://localhost:4200/admin/inventory/products with the validation login. The published electronics are visible in both the staff catalogue and the public NG shop, including anonymous visitors and ordinary customer accounts. The older solar fixture remains a draft. Omit `--publish` when seeding new draft-only fixtures; rerunning without that flag does not unpublish existing products. Admin stock is joined by real SKU IDs and sums warehouse balances. Catalogue prices without a configured price source display “Not configured.” The `--publish` option enables local NG catalogue visibility and the electronics eligibility records. It does not configure prices, tax, returns, delivery or checkout.

## Existing admin login

The existing local `admin` fixture (email `admin@example.invalid`) also has all seven application staff roles, including ADMIN, and can use http://localhost:4200/admin. Its password remains the local `KEYCLOAK_FIXTURE_PASSWORD`. Sign out and back in, or refresh an authenticated page, to refresh the browser's session roles. Actual access to staff users, products, inventory and orders was verified.

The local-only grant can be checked/reapplied with `python3 scripts/local/grant_admin.py` after this existing fixture has signed in once. It targets the checked-in admin identity subject and effective issuer, adds an audit record and does not alter passwords or MFA settings. Recent MFA remains required for sensitive changes.
