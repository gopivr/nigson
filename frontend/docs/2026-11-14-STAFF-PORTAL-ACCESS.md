# Staff portal access

Date: 2026-11-14
Status: locally verified implementation; authenticated real-role browser qualification not performed.

## Behavior

Recognized staff enter the common /admin portal through staffGuard and the staff layout. staffSectionGuard runs on every child navigation, including direct links and a reused parent layout. Admin users and role management retain adminGuard. All active admin remotes apply equivalent guards to their own route exports. One staff-access.ts policy drives section access, sidebar permissions, safe login returns and deterministic multi-role landing selection.

Default sign-in returns staff to their work area. Explicit checkout/account destinations remain valid. Approved staff deep links are remembered only as validated navigation paths, consumed once after the server session is established, and rechecked against current server roles. The BFF's existing return-path allowlist is not broadened. External, backslash and traversal returns are rejected. Backend role grants and tokens are never kept in this marker.

The public desktop/mobile header and account menu expose Staff Portal to all recognized staff. Layout labels show Staff Portal for non-admins. Role-specific dashboards hide unrelated cards. Analytics uses the ANALYST report response rather than making unauthorized orders, customers or inventory calls; unavailable reports are not fabricated as zero-value business summaries. Sales customer pages no longer request Operations-only orders. Finance order-processing controls require Operations even when the Finance role can read orders.

Operations-only inventory reads use the actual inventory API and SKU identifiers; they do not request Content Editor catalogue data or manufacture catalogue metadata. Content Editor product lists skip inventory reads when Operations is absent. Product stock reads are likewise role-gated. CMS editors can save drafts, while publication controls require the roles needed by the current complete pipeline. These are frontend access/data-flow corrections; no backend permissions were weakened.

## Existing owner-policy limits

ADMIN is not an implicit domain role in the existing owner APIs. The administrator accounts shown with all domain roles retain all matching sections. FINANCE order reads still require recent MFA; the earlier normal-admin pricing exception does not change that policy. CONTENT_PUBLISHER alone cannot read the CMS draft queue; the current API requires CONTENT_EDITOR too. Publisher-only users can moderate reviews, and the dashboard explains the draft-read limitation. OPERATIONS-only stock lists identify SKUs by their actual UUIDs where no authorized catalogue metadata is available.

## Main source changes

- shared-ui staff-access.ts (new), auth.guard.ts, session.service.ts, admin/admin.service.ts and public-api.ts.
- shell app.routes.ts, bff-callback component, and desktop/mobile header.
- auth login and account layout.
- admin-core routes, layout, dashboard and analytics.
- admin-shop routes, inventory views/product editor, customer and order pages.
- admin-cms routes and publishing controls; shared CMS capability check.
- staff-access.spec.ts (new), product-loading.spec.ts, cms.service.spec.ts and vitest.admin.config.mjs.

Earlier pricing and reCAPTCHA work is preserved. No staff grants, identities, credentials, migrations or provider configuration were changed.

## Verification

The first TypeScript check found a stale layout isAdmin reference and type-only re-export requirements after extracting the shared policy; both were corrected. The subsequent access tests passed. Final checks: node node_modules/vitest/vitest.mjs run --config vitest.admin.config.mjs passed 38 tests across 6 files. npm.cmd run build:all passed all 8 configured applications (marketing, shop, auth, account, admin-core, admin-shop, admin-cms, shell). After final owner-action refinements, admin-shop, admin-cms and shell production builds passed again. Existing CSS warnings remain: 16 selectors skipped per build. The shell header received one final staff-shortcut update; its final production rebuild also passed (node node_modules/@angular/cli/bin/ng.js build shell). git diff --check passed.

Tests cover every recognized role, owner-aligned direct-link denials, ADMIN-only management routes, multi-role unions, anonymous/customer rejection, delayed session loading, permission changes, safe deep-link returns and one-use return consumption. Additional tests cover Operations-only and Content-Editor-only API isolation and refusal to send a CMS publication request without the required role combination.

Backend tests were not rerun because no backend source changed. Authenticated real-role browser acceptance is not claimed; the frontend processes must be restarted/reloaded for the updated routes and remotes to be used. No live data edits were performed.


## Follow-up: Roles & Permissions explanations

The role overview now separates Page access, Allowed actions, and Additional requirements & limits. A current-account summary combines the actual assigned roles through the same canonical section policy used by the guards. A prominent explanation records normal-login ADMIN selling-price authority and the separate Content Editor requirement for opening the product editor. Each card explains its role-specific actions and existing MFA/additional-role requirements. Member counts explicitly describe overlapping role membership. A direct assignment link opens Admin Users. No authorization rules or staff assignments were changed in this presentation update.

Source: projects/admin-core/src/app/roles/roles.component.ts. Verification: admin-core and shell production builds passed; existing CSS-selector warnings remain. No new tests were added for this presentation-only change, and no authenticated browser rendering was claimed.
