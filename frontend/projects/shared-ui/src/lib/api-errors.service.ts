import { Injectable, signal, inject } from '@angular/core';
import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

export type ApiFailure = { id: number; message: string; page?: string };

/** Opt in only when the caller displays these specific failures in its own view. */
export const LOCAL_API_FAILURE_CODES = new HttpContextToken<readonly string[]>(() => []);

export function isAnonymousSessionUnauthorized(error: unknown, method: string, url: string): boolean {
  if (method !== 'GET' || url.split('?')[0] !== '/bff/session' || !(error instanceof HttpErrorResponse) || error.status !== 401) {
    return false;
  }
  return error.error !== null && typeof error.error === 'object' && error.error.code === 'AUTH_REQUIRED';
}

export function apiFailureDetail(error:any,fallback='The operation failed.'):string {
  const problem=error?.error;
  const detail=problem?.detail||problem?.title||error?.message||fallback;
  const violations=Array.isArray(problem?.violations)?problem.violations.map((v:any)=>v.message).filter((v:any)=>typeof v==='string').join(' '):'';
  return [detail,violations].filter(Boolean).join(' ');
}

/** Display the backend's problem response; never turn a failed request into domain data. */
@Injectable({ providedIn: 'root' })
export class ApiErrorsService {
  readonly failures = signal<ApiFailure[]>([]);
  private sequence = 0;
  visibleFor(page:string):ApiFailure[] {const path=page.split(/[?#]/)[0];return this.failures().filter(item=>!item.page||item.page===path);}
  report(message: string,page?:string): void {
    this.failures.update(items => items.some(x => x.message === message && x.page === page) ? items : [...items.slice(-4), { id: ++this.sequence, message, page }]);
  }
  dismiss(id: number): void { this.failures.update(items => items.filter(x => x.id !== id)); }
  describe(error: unknown, method = '', url = ''): string {
    if (!(error instanceof HttpErrorResponse)) return error instanceof Error ? error.message : 'The operation failed.';
    const path = url.split('?')[0];
    const prefix = [method, path].filter(Boolean).join(' ');
    if (error.status === 0) return `${prefix}: backend unreachable. Check the service and API proxy connection.`;
    const problem = error.error && typeof error.error === 'object' ? error.error : {};
    const code = typeof problem.code === 'string' ? problem.code : error.statusText || 'REQUEST_FAILED';
    const gateway = error.status === 502 || error.status === 504;
    const detail = typeof problem.detail === 'string' ? problem.detail : gateway ? 'The API proxy could not reach the backend. Check the service container, startup configuration and proxy target.' : error.status === 503 ? 'The service is unavailable. Check its health and required configuration.' : 'The backend rejected this request.';
    const violations = Array.isArray(problem.violations) ? problem.violations.map((v: any) => [v.field, v.message].filter(x => typeof x === 'string').join(': ')).filter(Boolean).join('; ') : '';
    const correlation = typeof problem.correlationId === 'string' ? ` Reference: ${problem.correlationId}.` : '';
    if (error.status === 422 && code === 'MARKET_RULE_VIOLATION') {
      return `${prefix}: HTTP 422 MARKET_RULE_VIOLATION. The requested market is not activated for selling, so catalogue and pricing data are unavailable until approved market configuration is applied.${correlation}`;
    }
    return `${prefix}: HTTP ${error.status} ${code}. ${detail}${violations ? ' ' + violations : ''}${correlation}`;
  }
  unavailable<T>(message: string) {
    this.report(message);
    return throwError(() => new Error(message));
  }
}

export const apiErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const errors = inject(ApiErrorsService);
  const page=typeof window==='undefined'?undefined:window.location.pathname;
  return next(request).pipe(catchError(error => {
    const handledLocally = error instanceof HttpErrorResponse &&
      request.context.get(LOCAL_API_FAILURE_CODES).includes(`${error.status}:${error.error?.code}`);
    if (!handledLocally && (request.url.startsWith('/api/') || request.url.startsWith('/bff/')) &&
        !isAnonymousSessionUnauthorized(error, request.method, request.url)) {
      errors.report(errors.describe(error, request.method, request.url),page);
    }
    return throwError(() => error);
  }));
};
