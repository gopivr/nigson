import { apiErrorInterceptor } from 'shared-ui';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { csrfInterceptor, idempotencyInterceptor, ifMatchInterceptor } from 'shared-ui';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([apiErrorInterceptor, csrfInterceptor, idempotencyInterceptor, ifMatchInterceptor])
    ),
  ],
};
