import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Base URLs for the two backend services.
 * Commerce  → http://localhost:8081  (catalogue, cart, orders, checkout, pricing, inventory)
 * Experience → http://localhost:8082 (enquiries, content, careers, distributor, newsletter)
 */
export const COMMERCE_BASE = '';
export const EXPERIENCE_BASE = '';
export const BFF_BASE = ''; // BFF lives on the Commerce service port

export interface RequestOptions {
  headers?: HttpHeaders | Record<string, string>;
  params?: HttpParams | Record<string, string | string[]>;
  context?: HttpContext;
}

/**
 * ApiService — typed wrapper around Angular HttpClient.
 * Every method prepends the correct base URL so callers only supply the path.
 * CSRF, Idempotency-Key, and If-Match interceptors (registered in app.config.ts)
 * apply automatically to every call made through this service.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  // --- Commerce (port 8081) ---

  commerceGet<T>(path: string, options?: RequestOptions): Observable<T> {
    return this.http.get<T>(`${COMMERCE_BASE}${path}`, options);
  }

  commercePost<T>(path: string, body: unknown, options?: RequestOptions): Observable<T> {
    return this.http.post<T>(`${COMMERCE_BASE}${path}`, body, options);
  }

  commercePut<T>(path: string, body: unknown, options?: RequestOptions): Observable<T> {
    return this.http.put<T>(`${COMMERCE_BASE}${path}`, body, options);
  }

  commercePatch<T>(path: string, body: unknown, options?: RequestOptions): Observable<T> {
    return this.http.patch<T>(`${COMMERCE_BASE}${path}`, body, options);
  }

  commerceDelete<T>(path: string, options?: RequestOptions): Observable<T> {
    return this.http.delete<T>(`${COMMERCE_BASE}${path}`, options);
  }

  // --- Experience (port 8082) ---

  experienceGet<T>(path: string, options?: RequestOptions): Observable<T> {
    return this.http.get<T>(`${EXPERIENCE_BASE}${path}`, options);
  }

  experiencePost<T>(path: string, body: unknown, options?: RequestOptions): Observable<T> {
    return this.http.post<T>(`${EXPERIENCE_BASE}${path}`, body, options);
  }

  experiencePut<T>(path: string, body: unknown, options?: RequestOptions): Observable<T> {
    return this.http.put<T>(`${EXPERIENCE_BASE}${path}`, body, options);
  }

  experiencePatch<T>(path: string, body: unknown, options?: RequestOptions): Observable<T> {
    return this.http.patch<T>(`${EXPERIENCE_BASE}${path}`, body, options);
  }

  experienceDelete<T>(path: string, options?: RequestOptions): Observable<T> {
    return this.http.delete<T>(`${EXPERIENCE_BASE}${path}`, options);
  }

  // --- BFF (port 8081, /bff/ prefix) ---

  bffGet<T>(path: string, options?: RequestOptions): Observable<T> {
    return this.http.get<T>(`${BFF_BASE}${path}`, { ...options, withCredentials: true });
  }

  bffPost<T>(path: string, body: unknown, options?: RequestOptions): Observable<T> {
    return this.http.post<T>(`${BFF_BASE}${path}`, body, { ...options, withCredentials: true });
  }
}
