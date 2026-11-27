import { withNativeFederation, shareAll } from '@angular-architects/native-federation/config';

export default withNativeFederation({
  name: 'inventory',

  exposes: {
    './Component': './projects/inventory/src/app/app.ts',
    './Routes': './projects/inventory/src/app/app.routes.ts',
  },

  shared: {
    'shared-ui': { singleton: true, strictVersion: true, requiredVersion: 'auto', build: 'package' },
    ...shareAll(
      { singleton: true, strictVersion: true, requiredVersion: 'auto', build: 'package' },
      {
        overrides: {
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
  ],

  features: {
    denseChunking: true,
  },
});
