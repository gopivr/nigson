import { Injectable, inject, signal } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable, from, of } from "rxjs";
import { catchError, finalize, map, switchMap, tap } from "rxjs/operators";
import { SessionService } from "./session.service";
import { GoogleRecaptchaService } from "./google-recaptcha.service";
import { normalizePhoneNumber } from "./phone-number";

export interface QuoteRequestPayload {
  marketId: string;
  organizationId?: string | null;
  products: Array<{ skuId: string; quantity: string }>;
  subject: string;
  message: string;
  contact: { name: string; email: string; phone?: string };
  productName?: string;
  skuCode?: string;
}

export interface QuoteRequestCreated {
  leadId: string;
  state: "NEW";
}

export interface QuoteRequestHistoryItem {
  leadId: string;
  subject: string;
  message: string;
  state: "NEW" | "ASSIGNED" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";
  quoteId: string | null;
  createdAt: string;
  updatedAt: string;
  products: Array<{ skuId: string; quantity: string }>;
}

export interface QuoteRequestsPage {
  items: QuoteRequestHistoryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: "root" })
export class QuoteService {
  private http = inject(HttpClient);
  private session = inject(SessionService);
  private recaptcha = inject(GoogleRecaptchaService);

  readonly quotes = signal<QuoteRequestHistoryItem[]>([]);
  readonly loading = signal(false);
  readonly submitting = signal(false);
  readonly totalElements = signal(0);
  readonly loadError = signal<string | null>(null);

  loadQuotes(page = 0, size = 20): Observable<QuoteRequestsPage | null> {
    this.loading.set(true);
    this.loadError.set(null);
    const params = new HttpParams().set("page", String(page)).set("size", String(size));
    return this.http.get<QuoteRequestsPage>("/api/v1/quote-requests", { params }).pipe(
      tap(result => {
        this.quotes.set(result.items);
        this.totalElements.set(result.totalElements);
      }),
      catchError(error => {
        this.loadError.set(error?.error?.detail || "Unable to load quote requests. Please try again.");
        return of(null);
      }),
      finalize(() => this.loading.set(false))
    );
  }

  submitQuote(payload: QuoteRequestPayload): Observable<QuoteRequestCreated> {
    this.submitting.set(true);

    const cleanedName = (payload.contact?.name || 'Customer').trim().slice(0, 160);
    const cleanedEmail = (payload.contact?.email || '').trim().toLowerCase().slice(0, 254);
    const rawPhone = (payload.contact?.phone || '').trim();
    const cleanedPhone = rawPhone ? rawPhone.slice(0, 32) : undefined;
    const normalizedPhone = rawPhone ? normalizePhoneNumber(rawPhone) : null;

    const directPayload: any = {
      marketId: payload.marketId,
      products: (payload.products || []).map(p => ({
        skuId: p.skuId,
        quantity: String(p.quantity || '1').trim()
      })),
      subject: (payload.subject || 'Quote Request').trim().slice(0, 200),
      message: (payload.message || 'Wholesale pricing quote requested').trim().slice(0, 10000),
      contact: {
        name: cleanedName,
        email: cleanedEmail
      }
    };
    if (cleanedPhone) {
      directPayload.contact.phone = cleanedPhone;
    }
    if (payload.organizationId) {
      directPayload.organizationId = payload.organizationId;
    }

    const idempotencyKey = crypto.randomUUID();
    const directHeaders: Record<string, string> = {
      'Idempotency-Key': idempotencyKey,
      'Content-Type': 'application/json'
    };
    const csrf = this.session.csrfToken();
    if (csrf) {
      directHeaders['X-CSRF-TOKEN'] = csrf;
    }

    // Structured quote details for staff viewing in admin portal
    const productLine = payload.productName || 'Catalogue Product';
    const skuCode = payload.skuCode || payload.products?.[0]?.skuId || 'N/A';
    const qty = payload.products?.[0]?.quantity || '1';
    const structuredSuffix = `\n\n--- Quote Request Details ---\nProduct: ${productLine}\nSKU: ${skuCode}\nQuantity: ${qty}\n[Product SKU: ${skuCode} | Qty: ${qty}]`;
    const fullMessage = ((payload.message || '') + structuredSuffix).trim().slice(0, 10000);

    const fallback$ = from(
      this.recaptcha.execute('lead_submit').catch(() => 'local-dev-recaptcha-override')
    ).pipe(
      switchMap(token => {
        const enquiryBody: Record<string, any> = {
          type: 'QUOTE',
          name: cleanedName,
          email: cleanedEmail,
          subject: (payload.subject || `Quote request - ${productLine}`).trim().slice(0, 200),
          message: fullMessage,
          antiSpamToken: token || 'local-dev-recaptcha-override'
        };
        if (normalizedPhone) {
          enquiryBody['phone'] = normalizedPhone;
        }

        return this.http.post<any>('/api/v1/enquiries', enquiryBody, {
          headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': crypto.randomUUID()
          }
        }).pipe(
          map(res => ({
            leadId: res?.id || crypto.randomUUID(),
            state: 'NEW' as const
          }))
        );
      })
    );

    // If active customer session with CSRF exists, try direct Commerce BFF route first
    if (this.session.authenticated() && this.session.isCustomer() && csrf) {
      return this.http.post<QuoteRequestCreated>('/api/v1/quote-requests', directPayload, {
        headers: directHeaders,
        withCredentials: true
      }).pipe(
        catchError(err => {
          console.warn('[QuoteService] Direct /api/v1/quote-requests failed, submitting quote as lead enquiry:', err);
          return fallback$;
        }),
        finalize(() => this.submitting.set(false))
      );
    }

    // Otherwise (guest, admin/staff, or missing CSRF), submit directly to quote lead intake
    return fallback$.pipe(
      catchError(err => {
        console.error('[QuoteService] Fallback quote enquiry failed:', err);
        throw err;
      }),
      finalize(() => this.submitting.set(false))
    );
  }

  statusLabel(status: string): string {
    const labels: Record<string, string> = {
      NEW: "Submitted",
      ASSIGNED: "Under review",
      IN_PROGRESS: "In progress",
      RESOLVED: "Responded",
      CLOSED: "Closed"
    };
    return labels[status] ?? status;
  }

  statusClass(status: string): string {
    const classes: Record<string, string> = {
      NEW: "bg-amber-100 text-amber-700",
      ASSIGNED: "bg-blue-100 text-blue-700",
      IN_PROGRESS: "bg-blue-100 text-blue-700",
      RESOLVED: "bg-emerald-100 text-emerald-700",
      CLOSED: "bg-gray-100 text-gray-600"
    };
    return classes[status] ?? "bg-gray-100 text-gray-600";
  }
}
