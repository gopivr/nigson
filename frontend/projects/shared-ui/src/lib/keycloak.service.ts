import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { SessionService } from './session.service';

/** Keycloak is reached only by the Java BFF; no browser token/admin API calls. */
export interface KeycloakLoginResult { success: boolean; error?: string; }

@Injectable({ providedIn: 'root' })
export class KeycloakService {
  private session = inject(SessionService);

  redirectToLogin(returnUrl = '/account'): Promise<void> {
    this.session.signIn(returnUrl);
    return Promise.resolve();
  }

  /** Registration is not exposed by the current Java BFF contract. */
  redirectToRegister(_returnUrl = '/account'): Promise<void> {
    window.location.assign('/auth/signup');
    return Promise.resolve();
  }

  /** The BFF consumes OIDC callbacks server-side and redirects to returnTo. */
  handleCallback(_code: string): Observable<{ success: boolean; returnUrl: string; error?: string }> {
    return of({ success: false, returnUrl: '/account', error: 'Sign-in callbacks are handled by the Java BFF.' });
  }

  /** Direct credential grants and Keycloak Admin API access are intentionally unsupported. */
  loginWithPassword(_username: string, _password: string): Observable<KeycloakLoginResult> {
    return of({ success: false, error: 'Use the Java BFF sign-in button to authenticate with Keycloak.' });
  }

  registerDirect(_input: unknown): Observable<KeycloakLoginResult> {
    return of({ success: false, error: 'Registration is not exposed by the current Java BFF contract.' });
  }

  resetPasswordDirect(_email: string, _password: string): Observable<KeycloakLoginResult> {
    return of({ success: false, error: 'Password recovery must be completed in the Keycloak sign-in portal.' });
  }
}
