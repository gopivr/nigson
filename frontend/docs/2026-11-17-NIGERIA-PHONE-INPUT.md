# Nigeria phone input: checkout and profile

Date: 17 November 2026.

Checkout and profile editing now use the shared PhoneInputComponent, defaulting to Nigeria (+234) with an empty subscriber field. The visible country code is never stored as a completed phone number. Local 08012345678, national 8012345678 and full +2348012345678 normalize to +2348012345678. Pasted full country codes are not duplicated; spacing, parentheses and hyphens are removed. Existing international E.164 numbers remain international. The selector includes an International mode for other country codes. This is format validation, not ownership/OTP verification.

The profile phone remains optional: clearing the subscriber field submits null; an incomplete explicit +234 is rejected. Checkout requires a complete delivery phone. Profile saves remain editable on backend/version/network failure and show success only after acknowledgement. The account service propagates failures rather than converting them to successful null responses.

Checkout explicitly loads the stored profile. A selected address phone takes precedence over profile prefilling, regardless of response order; deliberate user edits take precedence over both. Editing the phone invalidates an existing delivery quote. Checkout continues to save the delivery contact on the chosen address rather than silently rewriting the profile or every saved address.

## Files
- projects/shared-ui/src/lib/phone-number.ts and phone-number.spec.ts
- projects/shared-ui/src/lib/phone-input.component.ts and phone-input.component.spec.ts
- projects/shared-ui/src/public-api.ts
- projects/shared-ui/src/lib/account.service.ts and account.service.spec.ts
- projects/shop/src/app/checkout/checkout.component.ts and checkout.component.spec.ts
- projects/account/src/app/profile/profile.component.ts and profile.component.spec.ts
- vitest.checkout.config.mjs

## Existing backend
No backend code, API schema, migration, login or OTP changes. Commerce already persists optional customer_profile.phone through GET/PATCH /api/v1/me and required address.phone through the existing address APIs. Existing CSRF, idempotency and If-Match interceptors remain in use.

## Verification
PASS: node node_modules/vitest/vitest.mjs run --config vitest.checkout.config.mjs: 57 tests across 9 files. Covers local/full/international normalization, incomplete and duplicate codes, optional clearing, acknowledged/failed/unconfirmed saves, duplicate in-flight saves, and profile/address prefilling in either response order with deliberate edits preserved.
PASS: Angular compiler --noEmit checks for projects/shop/tsconfig.app.json, projects/account/tsconfig.app.json and projects/shell/tsconfig.app.json.
PASS: git diff --check.
No production bundles or copied asset folders generated. Live browser acceptance and real backend data mutations were not performed.

## Browser checks
1. Open checkout/profile: Nigeria (+234) is selected, with no fabricated subscriber number.
2. Enter a Nigerian local number, save profile and reload; verify the full canonical number persists and checkout can prefill it.
3. Paste a full +234 number: country code must appear only once. Verify an existing international number remains intact.
4. Check incomplete-number errors, optional profile clearing and an unsuccessful save: no success banner on failure.
5. Select a saved delivery address and change the number manually; profile/address loads must not overwrite the edit.
