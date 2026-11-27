import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { SessionService } from './session.service';
import { ShopService } from './shop.service';

/**
 * Auth 401 Interceptor
 *
 * Logs 401s without aggressively wiping active user sessions,
 * allowing UI services to gracefully handle missing optional endpoints.
 */
export const auth401Interceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((err: any) => {
      if (err instanceof HttpErrorResponse && err.status === 401) {
        console.warn(`[AuthInterceptor] 401 from ${req.url}`);
      }
      return throwError(() => err);
    })
  );
};

