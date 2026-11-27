import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LucideLoader2, LucideShieldCheck, LucideLock } from '@lucide/angular';
import { AuthShellComponent } from '../auth-shell/auth-shell.component';
import { SessionService } from 'shared-ui';

/**
 * ResetPasswordComponent (TASK-11)
 *
 * Password reset in Nigson is handled exclusively by Keycloak.
 * The browser never collects old/new passwords directly.
 *
 * The BFF does not expose a password-reset action. This screen directs the
 * user to the normal BFF sign-in flow, where the identity provider may expose
 * its configured recovery option.
 */
@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, RouterLink, AuthShellComponent, LucideLoader2, LucideShieldCheck, LucideLock],
  template: `
    <app-auth-shell
      eyebrow="Reset Password"
      title="Create new password"
      subtitle="Password recovery is handled by the identity provider."
      [hasFooter]="false"
    >
      <div class="flex flex-col items-center gap-6 py-4">
        <div class="grid h-16 w-16 place-items-center rounded-full bg-brand/10">
          <svg *ngIf="!redirecting()" lucideLock class="h-8 w-8 text-brand"></svg>
          <svg *ngIf="redirecting()" lucideLoader2 class="h-8 w-8 text-brand animate-spin"></svg>
        </div>

        <div class="rounded-2xl border border-hairline bg-surface-alt p-4 w-full">
          <p class="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-ink/60">
            <svg lucideShieldCheck class="h-3.5 w-3.5 text-brand"></svg>
            Secure Password Reset
          </p>
          <p class="mt-2 text-sm text-ink/70">
            Nigson does not collect your password. Continue to the identity
            provider sign-in page and use its recovery option if enabled.
          </p>
        </div>

        <button
          id="btn-bff-reset"
          (click)="redirectToReset()"
          [disabled]="redirecting()"
          class="w-full inline-flex items-center justify-center gap-2 rounded-full bg-brand text-white py-3.5 text-sm font-semibold hover:bg-brand-deep transition-colors disabled:opacity-70"
        >
          <svg *ngIf="redirecting()" lucideLoader2 class="h-4 w-4 animate-spin"></svg>
          {{ redirecting() ? 'Redirecting...' : 'Reset Password' }}
        </button>

        <a routerLink="/auth/login" class="text-xs text-muted-ink hover:text-brand transition-colors">
          Back to sign in
        </a>
      </div>
    </app-auth-shell>
  `
})
export class ResetPasswordComponent {
  private session = inject(SessionService);
  readonly redirecting = signal(false);

  redirectToReset() {
    this.redirecting.set(true);
    this.session.signIn('/account');
  }
}
