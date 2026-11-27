import { withNativeFederation, shareAll } from '@angular-architects/native-federation/config';

export default withNativeFederation({
  name: 'admin-core',

  exposes: {
    './Routes': './projects/admin-core/src/app/app.routes.ts',
    './AdminLayout': './projects/admin-core/src/app/layout/admin-layout.component.ts',
    './AdminDashboard': './projects/admin-core/src/app/dashboard/admin-dashboard.component.ts',
    './Analytics': './projects/admin-core/src/app/analytics/analytics.component.ts',
    './Users': './projects/admin-core/src/app/users/users.component.ts',
    './Roles': './projects/admin-core/src/app/roles/roles.component.ts',
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
