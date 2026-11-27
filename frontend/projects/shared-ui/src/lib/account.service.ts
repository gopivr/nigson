import { Injectable, inject, signal } from "@angular/core";
import { HttpClient, HttpContext, HttpHeaders } from "@angular/common/http";
import { Observable, of, tap, catchError, map } from "rxjs";
import { SessionService } from "./session.service";
import { ShopService } from "./shop.service";
import { apiFailureDetail, LOCAL_API_FAILURE_CODES } from "./api-errors.service";

/**
 * Backend Profile DTO (API-005, API-006 - TASK-47, TASK-48)
 */
export interface ProfileDTO {
  id: string;
  displayName: string;
  email: string;
  emailVerified: boolean;
  phone: string | null;
  locale: string;
  version: number;
}

export interface ProfilePatch {
  displayName?: string;
  phone?: string | null;
  locale?: string;
}

/**
 * Backend Address DTO (API-007, API-008, API-009, API-010 - TASK-49)
 */
export interface AddressDTO {
  id: string;
  label?: string | null;
  recipient: string;
  phone: string;
  line1: string;
  line2?: string | null;
  city: string;
  region?: string | null;
  postalCode?: string | null;
  countryCode: string;
  latitude?: number | null;
  longitude?: number | null;
  organizationId?: string | null;
  isDefault: boolean;
  version: number;
}

export interface AddressInput {
  label?: string;
  recipient: string;
  phone: string;
  line1: string;
  line2?: string;
  city: string;
  region?: string;
  postalCode?: string;
  countryCode: string;
  latitude?: number | null;
  longitude?: number | null;
  isDefault?: boolean;
}

export interface AddressPatch {
  label?: string | null;
  recipient?: string;
  phone?: string;
  line1?: string;
  line2?: string | null;
  city?: string;
  region?: string | null;
  postalCode?: string | null;
  countryCode?: string;
  latitude?: number | null;
  longitude?: number | null;
  isDefault?: boolean;
}

function sanitizeAddressInput(input: AddressInput): Record<string, unknown> {
  const result: Record<string, unknown> = {
    recipient: input.recipient?.trim() ?? '',
    phone: input.phone?.trim() ?? '',
    line1: input.line1?.trim() ?? '',
    city: input.city?.trim() ?? '',
    countryCode: (input.countryCode?.trim() || 'NG').toUpperCase(),
    isDefault: Boolean(input.isDefault)
  };
  if (input.label && input.label.trim()) {
    result['label'] = input.label.trim();
  }
  if (input.line2 && input.line2.trim()) {
    result['line2'] = input.line2.trim();
  }
  if (input.region && input.region.trim()) {
    result['region'] = input.region.trim();
  }
  if (input.postalCode && input.postalCode.trim()) {
    result['postalCode'] = input.postalCode.trim();
  }
  return result;
}

function sanitizeAddressPatch(patch: AddressPatch): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  if (patch.recipient !== undefined) result['recipient'] = patch.recipient.trim();
  if (patch.phone !== undefined) result['phone'] = patch.phone.trim();
  if (patch.line1 !== undefined) result['line1'] = patch.line1.trim();
  if (patch.city !== undefined) result['city'] = patch.city.trim();
  if (patch.countryCode !== undefined) result['countryCode'] = patch.countryCode.trim().toUpperCase();
  if (patch.isDefault !== undefined) result['isDefault'] = Boolean(patch.isDefault);
  if (patch.label !== undefined) result['label'] = patch.label?.trim() ? patch.label.trim() : null;
  if (patch.line2 !== undefined) result['line2'] = patch.line2?.trim() ? patch.line2.trim() : null;
  if (patch.region !== undefined) result['region'] = patch.region?.trim() ? patch.region.trim() : null;
  if (patch.postalCode !== undefined) result['postalCode'] = patch.postalCode?.trim() ? patch.postalCode.trim() : null;
  return result;
}


/**
 * Backend Reward DTO (API-062 - TASK-50)
 */
export interface RewardBalanceDTO {
  currency: string;
  availableMinorUnits: number;
  lifetimeEarnedMinorUnits?: number;
  tierName?: string;
}

export interface RewardEntryDTO {
  id: string;
  type: string;
  minorUnits: number;
  currency: string;
  description: string;
  createdAt: string;
}

export interface RewardLedgerDTO {
  availableByCurrency: RewardBalanceDTO[];
  entries: RewardEntryDTO[];
}

/**
 * Backend Referral DTO (API-063, API-161 - TASK-51)
 */
export interface ReferralDTO {
  code: string;
  link: string;
  qualifiedReferralCount: number;
}

@Injectable({ providedIn: "root" })
export class AccountService {
  private http = inject(HttpClient);
  private session = inject(SessionService);
  private shop = inject(ShopService);

  readonly profile = signal<ProfileDTO | null>(null);
  readonly addresses = signal<AddressDTO[]>([]);
  readonly rewards = signal<RewardLedgerDTO | null>(null);
  readonly referral = signal<ReferralDTO | null>(null);
  /** User-facing failure for referral code creation; the card renders it inline. */
  readonly referralError = signal<string | null>(null);

  readonly loadingProfile = signal(false);
  readonly loadingAddresses = signal(false);
  readonly loadingRewards = signal(false);
  readonly loadingReferral = signal(false);

  /** TASK-47: Load profile from GET /api/v1/me and sync to ShopService.user */
  loadProfile(): Observable<ProfileDTO | null> {
    if (!this.session.authenticated()) return of(null);
    this.loadingProfile.set(true);
    return this.http.get<ProfileDTO>("/api/v1/me").pipe(
      tap(p => {
        this.profile.set(p);
        this.loadingProfile.set(false);
        // Sync into ShopService.user signal
        this.shop.user.set({
          id: p.id,
          fullName: p.displayName,
          email: p.email,
          phone: p.phone ?? undefined,
          roles: this.session.roles(),
          createdAt: new Date().toISOString()
        });
      }),
      catchError(err => {
        console.error("[AccountService] Backend /api/v1/me unavailable:", err?.status ?? err);
        this.profile.set(null);
        this.loadingProfile.set(false);
        return of(null);
      })
    );
  }

  /** TASK-48: Update profile via PATCH /api/v1/me */
  updateProfile(patch: ProfilePatch): Observable<ProfileDTO> {
    return this.http.patch<ProfileDTO>("/api/v1/me", patch, {context:new HttpContext().set(LOCAL_API_FAILURE_CODES,['0:undefined','400:VALIDATION_ERROR','401:AUTH_REQUIRED','403:ACCESS_DENIED','409:IDEMPOTENCY_IN_PROGRESS','412:VERSION_MISMATCH','422:BUSINESS_RULE_VIOLATION','500:INTERNAL_ERROR'])}).pipe(
      tap(p => {
        this.profile.set(p);
        if (this.shop.user()) {
          this.shop.user.update(u => u ? {
            ...u,
            fullName: p.displayName,
            phone: p.phone ?? undefined
          } : null);
        }
      })
    );
  }

  /** TASK-49: Load saved addresses (GET /api/v1/me/addresses) */
  loadAddresses(): Observable<AddressDTO[]> {
    if (!this.session.authenticated()) return of([]);
    this.loadingAddresses.set(true);
    return this.http.get<AddressDTO[]>("/api/v1/me/addresses").pipe(
      tap(addrs => {
        this.addresses.set(addrs ?? []);
        this.loadingAddresses.set(false);
      }),
      catchError(err => {
        console.error("[AccountService] Backend /api/v1/me/addresses unavailable:", err?.status ?? err);
        this.addresses.set([]);
        this.loadingAddresses.set(false);
        return of([]);
      })
    );
  }

  /** TASK-49: Add a new address (POST /api/v1/me/addresses) */
  addAddress(address: AddressInput): Observable<AddressDTO | null> {
    const payload = sanitizeAddressInput(address);
    const headers = new HttpHeaders({
      'Idempotency-Key': crypto.randomUUID()
    });
    return this.http.post<AddressDTO>("/api/v1/me/addresses", payload, { headers }).pipe(
      tap(created => {
        this.addresses.update(list => [...list, created]);
      }),
      catchError(err => {
        console.error("[AccountService] addAddress failed:", err);
        return of(null);
      })
    );
  }

  /** TASK-49: Update existing address (PATCH /api/v1/me/addresses/:id) */
  updateAddress(id: string, patch: AddressPatch, version?: number): Observable<AddressDTO | null> {
    const existing = this.addresses().find(a => a.id === id);
    const ver = version ?? existing?.version ?? 0;
    const payload = sanitizeAddressPatch(patch);
    const headers = new HttpHeaders({
      'Idempotency-Key': crypto.randomUUID(),
      'If-Match': `"${ver}"`
    });
    return this.http.patch<AddressDTO>("/api/v1/me/addresses/" + id, payload, { headers }).pipe(
      tap(updated => {
        this.addresses.update(list => list.map(a => a.id === id ? updated : a));
      }),
      catchError(err => {
        console.error("[AccountService] updateAddress failed:", err);
        return of(null);
      })
    );
  }

  /** TASK-49: Delete address (DELETE /api/v1/me/addresses/:id) */
  deleteAddress(id: string, version?: number): Observable<boolean> {
    const existing = this.addresses().find(a => a.id === id);
    const ver = version ?? existing?.version ?? 0;
    const headers = new HttpHeaders({
      'Idempotency-Key': crypto.randomUUID(),
      'If-Match': `"${ver}"`
    });
    return this.http.delete("/api/v1/me/addresses/" + id, { headers }).pipe(
      tap(() => {
        this.addresses.update(list => list.filter(a => a.id !== id));
      }),
      map(() => true),
      catchError(err => {
        console.error("[AccountService] deleteAddress failed:", err);
        return of(false);
      })
    );
  }

  /** TASK-50: Load rewards ledger (GET /api/v1/me/rewards) */
  loadRewards(): Observable<RewardLedgerDTO | null> {
    if (!this.session.authenticated()) return of(null);
    this.loadingRewards.set(true);
    return this.http.get<RewardLedgerDTO>("/api/v1/me/rewards").pipe(
      tap(r => {
        this.rewards.set(r);
        this.loadingRewards.set(false);
      }),
      catchError(err => {
        console.error("[AccountService] Backend /api/v1/me/rewards unavailable:", err?.status ?? err);
        this.rewards.set(null);
        this.loadingRewards.set(false);
        return of(null);
      })
    );
  }

  /** TASK-51: Load referral code (GET /api/v1/me/referral-code) */
  loadReferral(): Observable<ReferralDTO | null> {
    if (!this.session.authenticated()) return of(null);
    this.loadingReferral.set(true);
    this.referralError.set(null);
    return this.http.get<ReferralDTO>("/api/v1/me/referral-code", {
      // API-063 documents 404 when no code has been created yet; the card offers
      // creation instead, so this expected 404 must not raise a global error.
      context: new HttpContext().set(LOCAL_API_FAILURE_CODES, ["404:NOT_FOUND"])
    }).pipe(
      tap(ref => {
        this.referral.set(ref);
        this.loadingReferral.set(false);
      }),
      catchError(err => {
        if (err?.status !== 404) {
          console.error("[AccountService] Backend /api/v1/me/referral-code unavailable:", err?.status ?? err);
          this.referralError.set(this.referralFailure(err));
        }
        this.referral.set(null);
        this.loadingReferral.set(false);
        return of(null);
      })
    );
  }

  /** Generate or create referral code (POST /api/v1/me/referral-code) */
  createReferral(): Observable<ReferralDTO | null> {
    if (!this.session.authenticated()) return of(null);
    this.loadingReferral.set(true);
    this.referralError.set(null);
    const headers = new HttpHeaders({ "Idempotency-Key": crypto.randomUUID() });
    return this.http.post<ReferralDTO>("/api/v1/me/referral-code", {}, { headers }).pipe(
      tap(ref => {
        this.referral.set(ref);
        this.loadingReferral.set(false);
      }),
      catchError(err => {
        this.referralError.set(this.referralFailure(err));
        this.loadingReferral.set(false);
        return of(null);
      })
    );
  }

  /** Explain a referral failure without inventing a code or a link. */
  private referralFailure(err: any): string {
    if (err?.status === 422 && err?.error?.code === "FEATURE_DISABLED") {
      return "Referral links are not configured for this store yet.";
    }
    if (err?.status === 401) return "Sign in again to create your referral code.";
    if (err?.status === 0) return "The store is unreachable. Check your connection and try again.";
    return apiFailureDetail(err, "The referral code could not be created.");
  }

  formatPrice(minorUnits: number): string {
    return String.fromCharCode(8358) + (minorUnits / 100).toLocaleString("en-NG", {
      minimumFractionDigits: 2, maximumFractionDigits: 2
    });
  }
}
