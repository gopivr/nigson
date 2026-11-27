import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideEye, LucideEyeOff } from '@lucide/angular';
import { BFF_BASE } from 'shared-ui';
import { AuthShellComponent } from '../auth-shell/auth-shell.component';

interface RegistrationRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AuthShellComponent, LucideEye, LucideEyeOff],
  template: `
    <app-auth-shell eyebrow="Create an account" title="Join Nigson"
      subtitle="Create your Nigson account. Your password is securely sent to our identity service and is never stored by this application." [hasFooter]="true">
      <form *ngIf="!complete()" #signupForm="ngForm" (ngSubmit)="submit(signupForm)" class="flex flex-col gap-4">
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <label class="flex flex-col gap-1.5 text-sm font-medium text-ink">First name
            <input name="firstName" [(ngModel)]="firstName" required maxlength="80" autocomplete="given-name"
              class="rounded-xl border border-hairline px-4 py-3 font-normal" />
          </label>
          <label class="flex flex-col gap-1.5 text-sm font-medium text-ink">Last name
            <input name="lastName" [(ngModel)]="lastName" required maxlength="80" autocomplete="family-name"
              class="rounded-xl border border-hairline px-4 py-3 font-normal" />
          </label>
        </div>
        <label class="flex flex-col gap-1.5 text-sm font-medium text-ink">Email
          <input name="email" [(ngModel)]="email" type="email" required maxlength="254" autocomplete="email"
            class="rounded-xl border border-hairline px-4 py-3 font-normal" />
        </label>
        <label class="flex flex-col gap-1.5 text-sm font-medium text-ink">
          <span>Password</span>
          <div class="relative flex items-center">
            <input name="password" [(ngModel)]="password" [type]="showPassword() ? 'text' : 'password'" required minlength="12" maxlength="128" autocomplete="new-password"
              class="w-full rounded-xl border border-hairline px-4 py-3 pr-11 font-normal focus:border-brand focus:outline-none" />
            <button type="button" (click)="togglePassword($event)" [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'"
              class="absolute right-3 grid h-8 w-8 place-items-center rounded-lg text-muted-ink hover:text-ink transition-colors">
              <svg *ngIf="!showPassword()" lucideEye class="h-4 w-4"></svg>
              <svg *ngIf="showPassword()" lucideEyeOff class="h-4 w-4"></svg>
            </button>
          </div>
          <span class="text-xs font-normal text-muted-ink">Use at least 12 characters.</span>
        </label>
        <label class="flex flex-col gap-1.5 text-sm font-medium text-ink">
          <span>Confirm password</span>
          <div class="relative flex items-center">
            <input name="confirmPassword" [(ngModel)]="confirmPassword" [type]="showConfirmPassword() ? 'text' : 'password'" required minlength="12" maxlength="128" autocomplete="new-password"
              class="w-full rounded-xl border border-hairline px-4 py-3 pr-11 font-normal focus:border-brand focus:outline-none" />
            <button type="button" (click)="toggleConfirmPassword($event)" [attr.aria-label]="showConfirmPassword() ? 'Hide confirm password' : 'Show confirm password'"
              class="absolute right-3 grid h-8 w-8 place-items-center rounded-lg text-muted-ink hover:text-ink transition-colors">
              <svg *ngIf="!showConfirmPassword()" lucideEye class="h-4 w-4"></svg>
              <svg *ngIf="showConfirmPassword()" lucideEyeOff class="h-4 w-4"></svg>
            </button>
          </div>
        </label>
        <p *ngIf="error()" role="alert" class="rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-700">{{ error() }}</p>
        <button type="submit" [disabled]="signupForm.invalid || submitting()"
          class="w-full rounded-full bg-brand py-3 text-sm font-semibold text-white disabled:opacity-60">
          {{ submitting() ? 'Creating account…' : 'Create account' }}
        </button>
        <p class="text-center text-xs leading-5 text-muted-ink">Nigson does not store your password. Account credentials are managed by our secure identity service.</p>
      </form>
      <div *ngIf="complete()" class="flex flex-col gap-4 py-2">
        <div role="status" class="rounded-2xl border border-green-200 bg-green-50 p-4 text-sm leading-6 text-green-900">
          If this email is available, registration is complete. Sign in to continue. If you already have an account, use the sign-in page.
        </div>
        <a routerLink="/auth/login" class="w-full rounded-full bg-brand py-3 text-center text-sm font-semibold text-white">Continue to sign in</a>
      </div>
      <div footer>Already have an account? <a routerLink="/auth/login" class="font-semibold text-brand hover:underline">Sign in</a></div>
    </app-auth-shell>
  `
})
export class SignupComponent {
  private readonly http = inject(HttpClient);
  readonly submitting = signal(false);
  readonly complete = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);
  readonly showConfirmPassword = signal(false);
  firstName = '';
  lastName = '';
  email = '';
  password = '';
  confirmPassword = '';

  togglePassword(event?: MouseEvent): void {
    event?.preventDefault();
    this.showPassword.update(show => !show);
  }

  toggleConfirmPassword(event?: MouseEvent): void {
    event?.preventDefault();
    this.showConfirmPassword.update(show => !show);
  }

  submit(form: { valid: boolean | null }): void {
    this.error.set(null);
    if (form.valid !== true) return;
    if (this.password !== this.confirmPassword) {
      this.error.set('Passwords do not match.');
      return;
    }
    const request: RegistrationRequest = {
      firstName: this.firstName.trim(),
      lastName: this.lastName.trim(),
      email: this.email.trim(),
      password: this.password
    };
    this.submitting.set(true);
    this.http.post<void>(`${BFF_BASE}/bff/register`, request, { withCredentials: true }).subscribe({
      next: () => {
        this.password = '';
        this.confirmPassword = '';
        this.complete.set(true);
        this.submitting.set(false);
      },
      error: (failure: HttpErrorResponse) => {
        this.submitting.set(false);
        this.error.set(failure.status === 429
          ? 'Too many attempts. Wait a minute before trying again.'
          : failure.status === 400 || failure.status === 422
            ? 'Check the information and password requirements, then try again.'
            : 'Registration is temporarily unavailable. Please try again later.');
      }
    });
  }
}
