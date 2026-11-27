// Dev globals
if (typeof (globalThis as any).ngDevMode === 'undefined') {
  (globalThis as any).ngDevMode = true;
}
if (typeof (globalThis as any).ngServerMode === 'undefined') {
  (globalThis as any).ngServerMode = false;
}

(globalThis as any).ngDevMode = (globalThis as any).ngDevMode ?? true;
(globalThis as any).ngServerMode = (globalThis as any).ngServerMode ?? false;

﻿/*
 * Public API Surface of shared-ui
 */

export * from './lib/shared-ui';
export * from './lib/button/button';
export * from './lib/input/input';
export * from './lib/qty-stepper/qty-stepper.component';
export * from './lib/login-required/login-required.component';
export * from './lib/country-select/country-select.component';
export * from './lib/otp-input/otp-input.component';
export * from './lib/utils';
export * from './lib/reviews';
export * from './lib/shop.service';
export * from './lib/cms.service';
export * from './lib/catalog.service';
export * from './lib/admin/admin.service';
export * from './lib/admin/distributor-workflow';
export * from './lib/admin-ui/admin-heading.component';
export * from './lib/admin-ui/stat-card.component';
export * from './lib/admin-ui/badge.component';
export * from './lib/marketing/page-header.component';

// HTTP infrastructure (TASK-01 to TASK-07)
export * from './lib/api.service';
export * from './lib/api-errors.service';
export * from './lib/session.service';
export * from './lib/csrf.interceptor';
export * from './lib/idempotency.interceptor';
export * from './lib/if-match.interceptor';
export * from './lib/auth-401.interceptor';

export * from './lib/auth.guard';

export * from './lib/cart.service';

export * from './lib/order.service';

export * from './lib/quote.service';

export * from './lib/account.service';

export * from './lib/review.service';

export * from './lib/marketing.service';

export * from './lib/keycloak.service';

export * from './lib/retail-pricing.service';

export * from './lib/staff-access';

export * from './lib/wishlist.service';
export * from './lib/wishlist-button.component';

export * from './lib/phone-number';
export * from './lib/phone-input.component';

export * from './lib/business-pricing.service';
export * from './lib/business-price-editor.component';

export * from './lib/business-price-assignment.component';

export * from './lib/product-prices.service';
export * from './lib/stock-status.component';

export * from './lib/affiliate.service';
export * from './lib/affiliate-payout.service';
