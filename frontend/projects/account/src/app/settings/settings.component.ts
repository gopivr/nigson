import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ShopService, SessionService } from 'shared-ui';
import {
  LucideLogOut, LucideTrash2, LucideX, LucideSave,
  LucideShieldCheck, LucideLock
} from '@lucide/angular';

/**
 * SettingsComponent (TASK-12, TASK-48)
 *
 * Changes from fake implementation:
 * - saveAccount()   → PATCH /api/v1/me  (email field is now READ-ONLY)
 * - savePassword()  → BFF starts Keycloak's closed UPDATE_PASSWORD action (identity owns passwords)
 * - logout()        → POST /bff/logout  (was: localStorage.removeItem)
 * - deleteAccount() → shows contact-support message (no backend delete API)
 */
@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    LucideLogOut, LucideTrash2,
    LucideX, LucideSave, LucideShieldCheck, LucideLock
  ],
  template: `
    <div class="space-y-6" *ngIf="user()">
      <!-- Account -->
      <div class="rounded-2xl border border-hairline bg-white p-6 md:p-8">
        <h3 class="text-sm font-extrabold uppercase tracking-widest">Account</h3>

        <div *ngIf="saveSuccess()" class="mt-4 rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm font-medium text-green-700">
          Account details updated successfully.
        </div>
        <div *ngIf="saveError()" class="mt-4 rounded-xl border border-brand/30 bg-brand/5 px-4 py-3 text-sm font-medium text-brand">
          {{ saveError() }}
        </div>

        <div class="mt-5 grid gap-4 md:grid-cols-2">
          <label class="block">
            <span class="text-[10px] font-semibold uppercase tracking-widest text-muted-ink">Full Name</span>
            <input
              [(ngModel)]="fullName"
              class="mt-1.5 w-full rounded-xl border border-hairline bg-white px-4 py-2.5 text-sm focus:outline-none focus:border-brand"
            />
          </label>
          <label class="block">
            <span class="text-[10px] font-semibold uppercase tracking-widest text-muted-ink">
              Email Address
              <span class="ml-1 text-muted-ink font-normal normal-case tracking-normal">(cannot be changed)</span>
            </span>
            <!-- Email is IMMUTABLE in backend — display only -->
            <input
              [value]="email()"
              readonly
              class="mt-1.5 w-full rounded-xl border border-hairline bg-surface-alt px-4 py-2.5 text-sm text-muted-ink cursor-not-allowed"
            />
          </label>
        </div>
        <div class="mt-6 flex flex-wrap justify-end gap-2">
          <button (click)="resetAccount()" class="inline-flex items-center gap-2 rounded-full border border-hairline px-5 py-2.5 text-xs font-semibold hover:border-brand hover:text-brand">
            <svg lucideX class="h-3.5 w-3.5"></svg> Cancel
          </button>
          <button (click)="saveAccount()" [disabled]="saving()" class="inline-flex items-center gap-2 rounded-full bg-brand text-white px-5 py-2.5 text-xs font-semibold hover:bg-brand-deep disabled:opacity-70">
            <svg lucideSave class="h-3.5 w-3.5"></svg>
            {{ saving() ? 'Saving...' : 'Save Changes' }}
          </button>
        </div>
      </div>

      <!-- Security -->
      <div class="rounded-2xl border border-hairline bg-white p-6 md:p-8">
        <h3 class="text-sm font-extrabold uppercase tracking-widest">Security</h3>
        <div class="mt-5 rounded-2xl border border-hairline bg-surface-alt p-4">
          <p class="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-ink/60">
            <svg lucideShieldCheck class="h-3.5 w-3.5 text-brand"></svg>
            Password managed by Nigson Identity
          </p>
          <p class="mt-2 text-sm text-ink/70">
            Nigson never stores your password. Change it on Nigson Identity, then
            you will return here automatically.
          </p>
          <button (click)="changePassword()" class="mt-4 inline-flex items-center gap-2 rounded-full bg-brand text-white px-5 py-2.5 text-xs font-semibold hover:bg-brand-deep">
            <svg lucideLock class="h-3.5 w-3.5"></svg> Change password
          </button>
        </div>
      </div>

      <!-- Privacy -->
      <div class="rounded-2xl border border-hairline bg-white p-6 md:p-8">
        <h3 class="text-sm font-extrabold uppercase tracking-widest">Privacy</h3>
        <div class="mt-5 grid gap-3 md:grid-cols-2">
          <button (click)="logout()" [disabled]="loggingOut()" class="inline-flex items-center justify-center gap-2 rounded-full border border-ink px-5 py-3 text-sm font-semibold text-ink hover:bg-ink hover:text-white transition-colors disabled:opacity-70">
            <svg lucideLogOut class="h-4 w-4"></svg>
            {{ loggingOut() ? 'Signing out...' : 'Sign out' }}
          </button>
          <button (click)="deleteAccount()" class="inline-flex items-center justify-center gap-2 rounded-full bg-brand text-white px-5 py-3 text-sm font-semibold hover:bg-brand-deep transition-colors">
            <svg lucideTrash2 class="h-4 w-4"></svg> Delete account
          </button>
        </div>
      </div>
    </div>
  `
})
export class SettingsComponent implements OnInit {
  private shop = inject(ShopService);
  private session = inject(SessionService);
  private http = inject(HttpClient);

  user = this.shop.user;

  fullName = '';
  email = this.session.email;

  saving = signal(false);
  saveSuccess = signal(false);
  saveError = signal<string | null>(null);
  loggingOut = signal(false);

  ngOnInit() {
    this.resetAccount();
  }

  resetAccount() {
    const u = this.user();
    if (u) this.fullName = u.fullName;
  }

  saveAccount() {
    if (!this.fullName.trim()) { this.saveError.set('Name is required.'); return; }
    this.saving.set(true);
    this.saveSuccess.set(false);
    this.saveError.set(null);

    // PATCH /api/v1/me — If-Match interceptor attaches version header automatically
    this.http.patch(`/api/v1/me`, { fullName: this.fullName.trim() })
      .subscribe({
        next: (updated: any) => {
          if (this.user()) {
            this.shop.user.set({ ...this.user()!, fullName: updated.fullName ?? this.fullName.trim() });
          }
          this.saving.set(false);
          this.saveSuccess.set(true);
          setTimeout(() => this.saveSuccess.set(false), 4000);
        },
        error: (err) => {
          this.saving.set(false);
          this.saveError.set(err?.error?.message ?? 'Failed to save. Please try again.');
        }
      });
  }

  logout() {
    this.loggingOut.set(true);
    this.shop.signOut().subscribe(() => { window.location.href = '/'; });
  }

  changePassword() {
    this.session.changePassword('/account/settings');
  }

  deleteAccount() {
    if (confirm('To delete your account, please contact Nigson support at support@nigson.com. Continue to support?')) {
      window.location.href = '/support';
    }
  }
}
