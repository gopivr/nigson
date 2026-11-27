import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { tap } from 'rxjs/operators';

const VERSIONED_METHODS = new Set(['PUT', 'PATCH', 'DELETE']);

/** In-memory ETag cache: URL path -> ETag value */
const etagCache = new Map<string, string>();

function urlPath(url: string): string {
  try {
    return new URL(url).pathname;
  } catch {
    return url;
  }
}

/**
 * Ensures ETag is enclosed in strict double quotes as required by Spring Boot:
 * ApiValues.version() checks: value.matches("\"(0|[1-9][0-9]{0,18})\"")
 */
function quoteEtag(val: string): string {
  const trimmed = val.trim();
  if (trimmed.startsWith('"') && trimmed.endsWith('"')) {
    return trimmed;
  }
  return `"${trimmed.replace(/^"+|"+$/g, '')}"`;
}

export const ifMatchInterceptor: HttpInterceptorFn = (req, next) => {
  // Do not attach to Keycloak
  if (req.url.includes(':8180') || req.url.includes('/realms/') || req.url.includes('/admin/realms/')) {
    return next(req);
  }

  // --- Request side: attach or normalize If-Match for mutations ---
  if (VERSIONED_METHODS.has(req.method)) {
    const existing = req.headers.get('If-Match');
    if (existing) {
      req = req.clone({ headers: req.headers.set('If-Match', quoteEtag(existing)) });
    } else {
      const cached = etagCache.get(urlPath(req.url));
      if (cached) {
        req = req.clone({ headers: req.headers.set('If-Match', quoteEtag(cached)) });
      }
    }
  }

  return next(req).pipe(
    tap(event => {
      // --- Response side: cache ETag from GET / HEAD responses ---
      if (event instanceof HttpResponse) {
        const etag = event.headers.get('ETag');
        if (etag) {
          const quoted = etag.startsWith('"') ? etag : `"${etag}"`;
          etagCache.set(urlPath(event.url ?? req.url), quoted);
        }
        if (Array.isArray(event.body)) {
          const basePath = urlPath(event.url ?? req.url);
          for (const item of event.body) {
            if (item && typeof item === 'object' && item.id && (typeof item.version === 'number' || typeof item.version === 'string')) {
              const v = String(item.version);
              etagCache.set(`${basePath}/${item.id}`, v.startsWith('"') ? v : `"${v}"`);
            }
          }
        } else if (event.body && typeof event.body === 'object') {
          const body = event.body as Record<string, unknown>;
          if (typeof body['version'] === 'number' || typeof body['version'] === 'string') {
            const v = String(body['version']);
            const quoted = v.startsWith('"') ? v : `"${v}"`;
            etagCache.set(urlPath(event.url ?? req.url), quoted);
            if (body['id']) {
              etagCache.set(`${urlPath(event.url ?? req.url)}/${body['id']}`, quoted);
            }
          }
        }
      }
    })
  );
};

export function clearEtag(url: string): void {
  etagCache.delete(urlPath(url));
}
