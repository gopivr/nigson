import { apiErrorInterceptor } from 'shared-ui';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { csrfInterceptor, idempotencyInterceptor, ifMatchInterceptor } from 'shared-ui';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' })),
    provideHttpClient(
      withInterceptors([apiErrorInterceptor, csrfInterceptor, idempotencyInterceptor, ifMatchInterceptor])
    ),
  ],
};
