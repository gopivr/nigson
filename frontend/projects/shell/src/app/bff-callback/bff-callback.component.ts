import {loginDestination} from 'shared-ui';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { SessionService } from 'shared-ui';
import { LucideLoader2 } from '@lucide/angular';

/**
 * BffCallbackComponent (TASK-09)
 *
 * Handles the OAuth2 redirect from Keycloak after successful login.
 * The BFF completes the token exchange server-side, then redirects the
 * browser to this route (/auth/callback).
 *
 * This component:
 *  1. Calls GET /bff/session to load the now-established session
 *  2. Populates SessionService signals (csrfToken, roles, userId, etc.)
 *  3. Navigates to the original return URL (from query param or home)
 */
@Component({
  selector: 'app-bff-callback',
  standalone: true,
  imports: [CommonModule, LucideLoader2],
  template: `
    <div class="min-h-screen flex flex-col items-center justify-center gap-4 bg-surface">
      <svg lucideLoader2 class="h-10 w-10 text-brand animate-spin"></svg>
      <p class="text-sm font-semibold text-ink/70">Completing sign-in...</p>
    </div>
  `
})
export class BffCallbackComponent implements OnInit {
  private session = inject(SessionService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  ngOnInit() {
    this.session.loadSession().subscribe(() => {
      const returnUrl = this.session.consumeLoginReturn() ?? loginDestination(this.session.roles(), this.route.snapshot.queryParams['redirect']);
      if (this.session.authenticated()) {
        this.router.navigateByUrl(returnUrl);
      } else {
        this.router.navigate(['/auth/login']);
      }
    });
  }
}
