import {apiFailureDetail} from './api-errors.service';
import { Injectable, inject, signal } from "@angular/core";
import { HttpClient, HttpHeaders, HttpErrorResponse } from "@angular/common/http";
import { Observable, of, from, tap, catchError, map, switchMap, finalize } from "rxjs";
import { SessionService } from "./session.service";
import { GoogleRecaptchaService } from "./google-recaptcha.service";

/** Normalize phone input to strict E.164 (+[1-9][0-9]{7,14}). Returns null if empty, '' if invalid. */
function normalizeInternationalPhone(rawPhone?: string): string | null {
  if (!rawPhone || !rawPhone.trim()) return null;
  let cleaned = rawPhone.trim().replace(/[^0-9+]/g, '');
  if (/^0\d{9,10}$/.test(cleaned)) {
    cleaned = '+234' + cleaned.slice(1);
  } else if (!cleaned.startsWith('+') && cleaned.length >= 8 && cleaned.length <= 15) {
    const noZero = cleaned.replace(/^0+/, '');
    cleaned = noZero.length >= 8 ? '+' + noZero : '';
  }
  return /^\+[1-9][0-9]{7,14}$/.test(cleaned) ? cleaned : '';
}

function sanitizeSingleLine(value: string | undefined, max: number): string {
  return (value || '').replace(/[\u0000-\u001F\u007F]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0, max).trim();
}

export interface EnquiryResult {
  success: boolean;
  status: number;
  data?: any;
  error?: string;
}

export interface DistributorApplicationResult {
  success: boolean;
  status: number;
  data?: BackendDistributorApplication | null;
  error?: string;
}

/**
 * Enquiry Input DTO (API-087 - TASK-56)
 */
export interface EnquiryPayload {
  type: "CONTACT" | "COMPLAINT" | "QUOTE" | "DISTRIBUTOR";
  name: string;
  email: string;
  phone?: string;
  subject: string;
  message: string;
  antiSpamToken?: string;
}

/**
 * Distributor Application Input DTO (API-092 - TASK-60)
 */
export interface DistributorApplicationPayload {
  partnerType?: "DISTRIBUTOR" | "WHOLESALE" | "AFFILIATE";
  businessDetails?: string;
  organizationName: string;
  countryCode: string;
  documentIds?: string[];
  businessReference?: string;
}

export type DistributorApplicationState =
  | "SUBMITTED"
  | "UNDER_REVIEW"
  | "ACTIVATION_PENDING"
  | "APPROVED"
  | "REJECTED"
  | "SUSPENSION_PENDING"
  | "SUSPENDED";

export interface BackendDistributorApplication {
  partnerType?: "DISTRIBUTOR" | "WHOLESALE" | "AFFILIATE";
  id: string;
  state: DistributorApplicationState | string;
  organizationId?: string | null;
  submittedAt: string;
  version: number;
}

/**
 * Career Opportunity DTO (API-103, API-105 - TASK-58, TASK-59)
 */
export interface CareerOpportunity {
  id: string;
  title: string;
  slug: string;
  kind: string;
  description: string;
  closesAt?: string;
  state: string;
}

@Injectable({ providedIn: "root" })
export class MarketingService {
  private http = inject(HttpClient);
  private session = inject(SessionService);
  private recaptcha = inject(GoogleRecaptchaService);

  readonly submittingEnquiry = signal(false);
  readonly submittingDistributor = signal(false);
  readonly subscribingNewsletter = signal(false);
  readonly myApplicationsError = signal<string | null>(null);

  readonly careers = signal<CareerOpportunity[]>([]);
  readonly loadingCareers = signal(false);

  /** TASK-56: Submit contact, distributor, or partner enquiry (POST /api/v1/enquiries) */
  submitEnquiry(payload: EnquiryPayload): Observable<EnquiryResult> {
    const normalizedPhone = normalizeInternationalPhone(payload.phone);
    if (normalizedPhone === '') {
      return of({
        success: false,
        status: 400,
        error: 'Enter a valid phone number with 8-15 digits (e.g. +2348012345678 or 08012345678).'
      });
    }

    this.submittingEnquiry.set(true);
    const idempotencyKey = crypto.randomUUID();
    // Backend LeadValues.validate requires type to be CONTACT or COMPLAINT for public intake,
    // while experience.lead database stores the full structured details in message and subject.
    const intakeType = (payload.type === 'DISTRIBUTOR' || payload.type === 'QUOTE') ? 'CONTACT' : payload.type;

    return from(payload.antiSpamToken ? Promise.resolve(payload.antiSpamToken) : this.recaptcha.execute("lead_submit")).pipe(
      switchMap(token => {
        const body: Record<string, any> = {
          type: intakeType,
          name: sanitizeSingleLine(payload.name, 160),
          email: (payload.email || '').trim().toLowerCase().slice(0, 254),
          subject: sanitizeSingleLine(payload.subject || 'Commercial Partner Enquiry', 200),
          message: (payload.message || '').trim(),
          antiSpamToken: token
        };

        if (normalizedPhone) {
          body['phone'] = normalizedPhone;
        }

        const headers = new HttpHeaders({
          'Content-Type': 'application/json',
          'Idempotency-Key': idempotencyKey
        });

        return this.http.post<any>("/api/v1/enquiries", body, { headers }).pipe(
          map(res => {
            this.submittingEnquiry.set(false);
            return { success: true, status: 201, data: res };
          }),
          catchError((err: HttpErrorResponse) => {
            console.error("[MarketingService] submitEnquiry failed:", err);
            this.submittingEnquiry.set(false);
            const detail = apiFailureDetail(err, 'Submission error');
            return of({
              success: false,
              status: err.status || 500,
              error: detail
            });
          })
        );
      })
    ).pipe(
      catchError((error: HttpErrorResponse | Error) => {
        this.submittingEnquiry.set(false);
        const detail = error instanceof Error ? error.message
          : error?.error?.detail || error?.error?.title || error?.message || 'Submission error';
        return of({ success: false, status: error instanceof Error ? 503 : error.status || 500, error: detail });
      })
    );
  }

  /** TASK-57: Subscribe to newsletter with double opt-in (POST /api/v1/newsletter-subscriptions) */
  subscribeNewsletter(email: string): Observable<boolean> {
    this.subscribingNewsletter.set(true);
    const body = {
      email,
      policyVersion: "",
      antiSpamToken: ""
    };

    return this.http.post("/api/v1/newsletter-subscriptions", body).pipe(
      map(() => true),
      tap(() => this.subscribingNewsletter.set(false)),
      catchError(err => {
        console.error("[MarketingService] subscribeNewsletter failed:", err);
        this.subscribingNewsletter.set(false);
        return of(false);
      })
    );
  }

  /** TASK-58: Load public career opportunities (GET /api/v1/career-opportunities) */
  loadCareers(): Observable<CareerOpportunity[]> {
    this.loadingCareers.set(true);
    return this.http.get<{ items: CareerOpportunity[] }>("/api/v1/career-opportunities").pipe(
      map(res => res?.items ?? []),
      tap(items => {
        this.careers.set(items);
        this.loadingCareers.set(false);
      }),
      catchError(err => {
        console.warn("[MarketingService] loadCareers failed:", err);
        this.loadingCareers.set(false);
        return of([]);
      })
    );
  }

  /** TASK-59: Submit career job application (POST /api/v1/career-opportunities/:id/applications) */
  submitCareerApplication(opportunityId: string, payload: {
    name: string;
    email: string;
    answers?: Record<string, any>;
    cvFileId?: string;
  }): Observable<boolean> {
    return this.http.post(`/api/v1/career-opportunities/${opportunityId}/applications`, payload).pipe(
      map(() => true),
      catchError(err => {
        console.error("[MarketingService] submitCareerApplication failed:", err);
        return of(false);
      })
    );
  }

  /** TASK-60: Submit distributor application (POST /api/v1/distributor-applications) */
  submitDistributorApplication(payload: DistributorApplicationPayload, operationKey?: string): Observable<BackendDistributorApplication> {
    this.submittingDistributor.set(true);
    const cleanedRef = sanitizeSingleLine(payload.businessReference, 100);
    const body: Record<string, any> = {
      organizationName: sanitizeSingleLine(payload.organizationName, 200),
      countryCode: (payload.countryCode || "NG").trim().slice(0, 2).toUpperCase(),
      documentIds: payload.documentIds || [],
      partnerType: payload.partnerType || "DISTRIBUTOR",
      ...(payload.businessDetails ? {businessDetails: payload.businessDetails} : {})
    };
    if (cleanedRef) {
      body['businessReference'] = cleanedRef;
    }

    const headers: Record<string, string> = {
      'Idempotency-Key': operationKey || crypto.randomUUID()
    };

    return this.http.post<BackendDistributorApplication>("/api/v1/distributor-applications", body, { headers }).pipe(
      finalize(() => this.submittingDistributor.set(false))
    );
  }

  /** TASK-60: Get my distributor applications (GET /api/v1/me/distributor-applications) */
  getMyDistributorApplications(): Observable<BackendDistributorApplication[]> {
    if (!this.session.authenticated()) {
      this.myApplicationsError.set(null);
      return of([]);
    }
    this.myApplicationsError.set(null);
    return this.http.get<BackendDistributorApplication[]>("/api/v1/me/distributor-applications", { withCredentials: true }).pipe(
      map(res => res ?? []),
      catchError(err => {
        console.warn("[MarketingService] getMyDistributorApplications failed:", err);
        this.myApplicationsError.set(err?.error?.detail || err?.error?.title || 'Unable to load your distributor applications from the server.');
        return of([]);
      })
    );
  }
}
