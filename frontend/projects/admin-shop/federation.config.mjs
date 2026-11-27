import { withNativeFederation, shareAll } from '@angular-architects/native-federation/config';

export default withNativeFederation({
  name: 'admin-shop',

  exposes: {
    './CatalogueImport': './projects/admin-shop/src/app/inventory/catalogue-import.component.ts',
    './Categories': './projects/admin-shop/src/app/inventory/categories.component.ts',
    './Routes': './projects/admin-shop/src/app/app.routes.ts',
    './InventoryDashboard': './projects/admin-shop/src/app/inventory/inventory-dashboard.component.ts',
    './ProductList': './projects/admin-shop/src/app/inventory/product-list.component.ts',
    './ProductEdit': './projects/admin-shop/src/app/inventory/product-edit.component.ts',
    './StockAdjustment': './projects/admin-shop/src/app/inventory/stock-adjustment.component.ts',
    './StockMovements': './projects/admin-shop/src/app/inventory/stock-movements.component.ts',
    './StockReport': './projects/admin-shop/src/app/inventory/stock-report.component.ts',
    './Orders': './projects/admin-shop/src/app/sales/orders.component.ts',
    './AffiliatePayouts': './projects/admin-shop/src/app/sales/affiliate-payouts.component.ts',
    './Customers': './projects/admin-shop/src/app/sales/customers.component.ts',
    './Quotes': './projects/admin-shop/src/app/sales/quotes.component.ts',
    './Applications': './projects/admin-shop/src/app/sales/applications.component.ts',
    './Inquiries': './projects/admin-shop/src/app/sales/inquiries.component.ts',
  },

  shared: {
    'shared-ui': { singleton: true, strictVersion: true, requiredVersion: 'auto', build: 'package' },
    ...shareAll(
      { singleton: true, strictVersion: true, requiredVersion: 'auto', build: 'package' },
      {
        overrides: {
          // includeSecondaries is an opt-out of ignoreUnusedDeps, so all of
          // @angular/core is shared to prevent mismatches.
          '@angular/core': {
            singleton: true,
            strictVersion: true,
            requiredVersion: 'auto',
            build: 'package',
            includeSecondaries: { keepAll: true },
          },
        },
      },
    ),
  },

  skip: [
    'rxjs/ajax',
    'rxjs/fetch',
    'rxjs/testing',
    'rxjs/webSocket',
    // Add further packages you don't need at runtime
  ],

  // Please read our FAQ about sharing libs:
  // https://shorturl.at/jmzH0

  features: {
    // ignoreUnusedDeps is enabled by default now
    // ignoreUnusedDeps: true,

    // Opt-in: groups chunks in remoteEntry.json for smaller metadata file
    denseChunking: true,
  },
});
