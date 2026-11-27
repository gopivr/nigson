import {safeReturnPath, loginDestination} from './staff-access';
import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError, finalize, map, switchMap, tap } from 'rxjs/operators';
import { ApiErrorsService, isAnonymousSessionUnauthorized } from './api-errors.service';
import { BFF_BASE } from './api.service';

export const PASSWORD_CHANGE_RETURN_PATH = '/account/settings';

/**
 * Build a BFF login URL. `action` is a closed action name: the BFF maps
 * `password` to Keycloak's UPDATE_PASSWORD required action and rejects anything else.
 * The local Compose callback defaults to localhost:4200. Host-only session cookies
 * created on 127.0.0.1 are not sent to that callback host.
 */
function identityLoginUrl(target: string, action?: 'password'): string {
  const loginUrl = new URL(`${BFF_BASE}/bff/login`, window.location.origin);
  if (!BFF_BASE && window.location.protocol === 'http:' &&
    window.location.hostname === '127.0.0.1' && window.location.port === '4200') {
    loginUrl.hostname = 'localhost';
  }
  loginUrl.searchParams.set('returnTo', target);
  if (action === 'password') loginUrl.searchParams.set('action', 'password');
  return loginUrl.toString();
}

/** Where the Change password action sends the browser; identity renders the form. */
export function changePasswordUrl(returnTo: string = PASSWORD_CHANGE_RETURN_PATH): string {
  return identityLoginUrl(safeReturnPath(returnTo) ?? PASSWORD_CHANGE_RETURN_PATH, 'password');
}

/** The server-side Commerce BFF session view. The browser never stores identity tokens. */
export interface BffSession {
  authenticated: boolean;
  partyId?: string | null;
  displayName?: string | null;
  roles?: string[];
  organizationMemberships?: Array<{ organizationId: string; role: string }>;
  expiresAt?: string | null;
  csrfToken?: string | null;
}

@Injectable({ providedIn: 'root' })
export class SessionService {
  private http = inject(HttpClient);
  private errors = inject(ApiErrorsService);
  readonly csrfToken = signal<string | null>(null);
  readonly authenticated = signal(false);
  readonly userId = signal<string | null>(null);
  readonly fullName = signal<string | null>(null);
  readonly email = signal<string | null>(null);
  readonly roles = signal<string[]>([]);
  readonly sessionLoaded = signal(false);

  constructor() { this.loadSession().subscribe({error: () => {}}); }

  /** Load only the session asserted by the Java BFF cookie. */
  loadSession(): Observable<BffSession> {
    return this.http.get<BffSession>(`${BFF_BASE}/bff/session`, {withCredentials:true}).pipe(
      tap(session=>this.applySession(session)),
      catchError(error=>{
        this.clearSessionState();
        if (isAnonymousSessionUnauthorized(error, 'GET', `${BFF_BASE}/bff/session`)) {
          return of({ authenticated: false });
        }
        this.errors.report(this.errors.describe(error,'GET','/bff/session'));
        return throwError(()=>error);
      })
    );
  }

  /** Activate local admin developer session fixture on localhost */
  

  /** Start the Keycloak authorization-code flow through the Java BFF. */
  consumeLoginReturn():string|null {
    try {
      const pending=sessionStorage.getItem('nigson_login_return');
      if(pending===null)return null;
      sessionStorage.removeItem('nigson_login_return');
      return loginDestination(this.roles(),pending==='DEFAULT'?undefined:pending);
    } catch { return null; }
  }

  signIn(returnUrl?: string): void {
    const requested=safeReturnPath(returnUrl);
    try { sessionStorage.setItem('nigson_login_return',requested??'DEFAULT'); } catch { }
    returnUrl=requested??'/account';
    const target = ['/', '/account', '/shop', '/checkout', '/admin', '/distributor'].includes(returnUrl)
      ? returnUrl
      : returnUrl.startsWith('/distributor')
        ? '/distributor'
        : returnUrl.startsWith('/checkout') || returnUrl.startsWith('/cart')
          ? '/checkout'
          : returnUrl.startsWith('/admin')
            ? '/admin'
            : '/account';
    window.location.assign(identityLoginUrl(target));
  }

  /**
   * Delegate credential change to Keycloak. Commerce never sees or stores passwords;
   * identity renders the form after the BFF starts the closed UPDATE_PASSWORD action.
   */
  changePassword(returnUrl: string = PASSWORD_CHANGE_RETURN_PATH): void {
    window.location.assign(changePasswordUrl(returnUrl));
  }

  clearSession(): void {
    this.logout().subscribe();
  }

  /** Use the current CSRF proof (loading it if needed), end one server session, then clear local state. */
  logout(): Observable<void> {
    const session$=this.csrfToken()?of({authenticated:true,csrfToken:this.csrfToken()} as BffSession):this.loadSession();
    return session$.pipe(switchMap(session=>session.authenticated ? this.http.post<void>(`${BFF_BASE}/bff/logout`,{}, {withCredentials:true,headers:{'X-CSRF-TOKEN':session.csrfToken ?? ''}}) : of(undefined)),tap(()=>this.clearSessionState()));
  }

  clearSessionState(): void {
    try {
      if (typeof localStorage !== 'undefined') {
        localStorage.removeItem('nigson_local_admin_session');
      }
    } catch { }
    this.authenticated.set(false);
    this.userId.set(null);
    this.fullName.set(null);
    this.email.set(null);
    this.roles.set([]);
    this.csrfToken.set(null);
    this.sessionLoaded.set(true);
  }

  private applySession(session: BffSession): void {
    this.authenticated.set(session.authenticated === true);
    this.userId.set(session.authenticated ? session.partyId ?? null : null);
    this.fullName.set(session.authenticated ? session.displayName ?? null : null);
    this.email.set(null);
    this.roles.set(session.authenticated ? [...(session.roles ?? [])] : []);
    this.csrfToken.set(session.authenticated ? session.csrfToken ?? null : null);
    this.sessionLoaded.set(true);
  }

  hasRole(role: string): boolean {
    const r = role.toUpperCase();
    return this.roles().some(x => x.toUpperCase() === r);
  }

  isAdmin(): boolean { return this.hasRole('ADMIN'); }
  isCustomer(): boolean { return this.hasRole('CUSTOMER'); }
  isStaff(): boolean { return ['ADMIN', 'SALES', 'OPERATIONS', 'FINANCE', 'CONTENT_EDITOR', 'CONTENT_PUBLISHER', 'ANALYST'].some(role => this.hasRole(role)); }
  isSales(): boolean { return this.hasRole('SALES'); }
  isOperations(): boolean { return this.hasRole('OPERATIONS'); }
  isContentEditor(): boolean { return this.hasRole('CONTENT_EDITOR'); }
  isContentPublisher(): boolean { return this.hasRole('CONTENT_PUBLISHER'); }
}
