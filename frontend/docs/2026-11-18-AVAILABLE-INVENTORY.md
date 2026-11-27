# Admin available inventory display

Products now displays Available to sell as its main inventory number, with On hand, Reserved and Allocated alongside it. Stock badges, inventory overview totals and low/out-of-stock reports use available quantities. Product-page entry reloads server data and a Refresh button supports an already-open page after another tab creates an order. Refresh requests cannot be repeated while in flight. This is refreshed server state, not a realtime subscription.

Shared inventory mapping preserves stock as the physical on-hand count for existing physical-count/adjustment consumers; adds onHand, allocated and available; aggregates by actual SKU across warehouse balances; prefers the backend available value and falls back to onHand minus reserved minus allocated for older responses. Decimal quantities are normalized to three places. Operations-only loading uses the same mapper. Content-only users still do not request or see Operations stock.

The inventory editor and stock-adjustment labels explicitly identify physical/on-hand quantities. No physical counts, adjustments, reservation/allocation/dispatch rules, backend APIs or database values were changed. Paid orders remain physically on hand until dispatch; available quantities already exclude them.

Files: shared-ui admin/inventory-mapping.ts and tests; admin.service.ts and product-loading.spec.ts; admin-shop inventory product-list, inventory-dashboard, stock-report, product-edit and stock-adjustment components; new inventory-availability.spec.ts; vitest.admin.config.mjs.

Verification PASS: 71 tests across 10 files in vitest.admin.config.mjs, including mapping, owner-scoped loads, reservation-to-allocation consistency, dashboard/report filtering and refresh behaviour. Angular --noEmit checks passed for admin-shop and shell; git diff --check passed. No generated bundles/assets produced. Live browser acceptance not performed.

Browser checks: Products should show 14 on hand, 0 reserved, 2 allocated and 12 available for the observed example, subject to newer orders. After a successful order click Refresh or reopen the page. Fully committed stock should show zero available and Out of Stock even before dispatch. Check low-stock reports against available units and confirm physical-count inputs retain the on-hand number.
