import { apiErrorInterceptor } from 'shared-ui';
import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  APP_INITIALIZER,
} from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import {
  csrfInterceptor,
  idempotencyInterceptor,
  ifMatchInterceptor,
  auth401Interceptor,
  CartService,
  SessionService,
  AccountService,
  CatalogService,
} from 'shared-ui';
import { switchMap, of, forkJoin, catchError } from 'rxjs';

/**
 * Shell app.config.ts (TASK-01, TASK-05, TASK-07)
 *
 * Registers:
 *   - provideHttpClient with all three interceptors
 *   - APP_INITIALIZER that calls SessionService.loadSession() so the BFF
 *     session (CSRF token, roles, userId) is resolved before any route guard runs
 */
function initApp(session: SessionService, cart: CartService, account: AccountService, catalog: CatalogService) {
  return () => session.loadSession().pipe(
    switchMap(() => catalog.validateMarket()),
    switchMap(() => session.authenticated() ? forkJoin([cart.loadCart(), account.loadProfile()]) : of(null)),
    catchError(() => of(null))
  );
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' })),
    provideHttpClient(
      withInterceptors([apiErrorInterceptor, csrfInterceptor, idempotencyInterceptor, ifMatchInterceptor, auth401Interceptor])
    ),
    {
      provide: APP_INITIALIZER,
      useFactory: initApp,
      deps: [SessionService, CartService, AccountService, CatalogService],
      multi: true,
    },
  ],
};
