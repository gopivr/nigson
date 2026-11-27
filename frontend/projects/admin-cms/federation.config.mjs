import { withNativeFederation, shareAll } from '@angular-architects/native-federation/config';

export default withNativeFederation({
  name: 'admin-cms',

  exposes: {
    './Routes': './projects/admin-cms/src/app/app.routes.ts',
    './Blog': './projects/admin-cms/src/app/cms/blog.component.ts',
    './Pages': './projects/admin-cms/src/app/cms/pages.component.ts',
    './Reviews': './projects/admin-cms/src/app/cms/reviews.component.ts',
    './VisualHome': './projects/admin-cms/src/app/cms/visual-home-editor.component.ts',
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
