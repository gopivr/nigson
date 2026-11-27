import { HttpInterceptorFn } from '@angular/common/http';

const MUTATION_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/**
 * Idempotency-Key Interceptor (TASK-04)
 *
 * Generates a unique UUID and attaches it as the `Idempotency-Key` header
 * on every state-mutating request (POST, PUT, PATCH, DELETE) to the Nigson backend.
 *
 * Excludes Keycloak and third-party identity providers to avoid CORS preflight rejection.
 */
export const idempotencyInterceptor: HttpInterceptorFn = (req, next) => {
  if (!MUTATION_METHODS.has(req.method)) {
    return next(req);
  }

  // Do not attach to Keycloak or non-Nigson API requests
  if (req.url.includes(':8180') || req.url.includes('/realms/') || req.url.includes('/admin/realms/')) {
    return next(req);
  }

  // Only add if not already present (allows callers to override when replaying)
  if (req.headers.has('Idempotency-Key')) {
    return next(req);
  }

  const key = crypto.randomUUID();
  const cloned = req.clone({
    headers: req.headers.set('Idempotency-Key', key),
  });

  return next(cloned);
};
