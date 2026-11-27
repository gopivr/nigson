import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { SessionService } from './session.service';

const MUTATION_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/**
 * CSRF & Session Credentials Interceptor (TASK-03)
 *
 * 1. Attaches withCredentials: true to all Nigson BFF and API endpoints so
 *    the browser sends the authenticated session cookie.
 * 2. Attaches the CSRF token obtained from GET /bff/session onto every
 *    state-mutating request (POST, PUT, PATCH, DELETE) to the Commerce BFF.
 *
 * Excludes Keycloak identity requests.
 */
export const csrfInterceptor: HttpInterceptorFn = (req, next) => {
  if (!MUTATION_METHODS.has(req.method)) {
    return next(req);
  }

  const session = inject(SessionService);

  // Do not attach to Keycloak or non-Nigson API requests
  if (req.url.includes(':8180') || req.url.includes('/realms/') || req.url.includes('/admin/realms/')) {
    return next(req);
  }

  const isBffOrApi =
    req.url.startsWith('/api/') ||
    req.url.startsWith('/bff/') ||
    req.url.includes('localhost:4200/api/') ||
    req.url.includes('localhost:4200/bff/');

  const shouldWithCreds = isBffOrApi || req.withCredentials;

  let headers = req.headers;
  if (MUTATION_METHODS.has(req.method)) {
    const token = session.csrfToken();
    if (token && !headers.has('X-CSRF-TOKEN')) {
      headers = headers.set('X-CSRF-TOKEN', token);
    }
  }

  const cloned = req.clone({
    headers,
    withCredentials: shouldWithCreds
  });

  return next(cloned);
};
