import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ShopService } from '../shop.service';
import { LucideX, LucideLogIn, LucideUserPlus } from '@lucide/angular';

@Component({
  selector: 'ui-login-required',
  standalone: true,
  imports: [CommonModule, LucideX, LucideLogIn, LucideUserPlus],
  template: `
    <div
      *ngIf="shop.loginRequired().open"
      class="fixed inset-0 z-[80] bg-black/60 backdrop-blur-sm grid place-items-center p-4 animate-fade-in"
      (click)="close()"
      role="dialog"
      aria-modal="true"
    >
      <div
        class="relative w-full max-w-md rounded-3xl bg-white p-8 md:p-10 shadow-[0_40px_100px_-30px_rgba(0,0,0,0.5)] animate-scale-in"
        (click)="$event.stopPropagation()"
      >
        <button
          (click)="close()"
          aria-label="Close"
          class="absolute top-4 right-4 grid h-9 w-9 place-items-center rounded-full border border-hairline hover:bg-brand hover:text-white transition-colors"
        >
          <svg lucideX class="h-4 w-4"></svg>
        </button>

        <div class="grid h-14 w-14 place-items-center rounded-2xl bg-brand/10 text-brand mb-6">
          <svg lucideLogIn class="h-6 w-6"></svg>
        </div>

        <p class="text-[11px] font-semibold uppercase tracking-[0.22em] text-brand">Sign in required</p>
        <h2 class="mt-2 text-2xl md:text-3xl font-extrabold leading-tight tracking-[-0.02em]">
          Sign in to continue
        </h2>
        <p class="mt-3 text-sm text-muted-ink leading-relaxed">
          {{ shop.loginRequired().reason || 'Please sign in or create an account to continue.' }}
        </p>

        <div class="mt-8 grid gap-3">
          <button
            (click)="go('/login')"
            class="inline-flex items-center justify-center gap-2 rounded-full bg-brand text-white px-6 py-3 text-sm font-semibold hover:bg-brand-deep transition-colors"
          >
            <svg lucideLogIn class="h-4 w-4"></svg> Login
          </button>
          <button
            (click)="go('/signup')"
            class="inline-flex items-center justify-center gap-2 rounded-full border border-ink bg-white text-ink px-6 py-3 text-sm font-semibold hover:bg-ink hover:text-white transition-colors"
          >
            <svg lucideUserPlus class="h-4 w-4"></svg> Create Account
          </button>
        </div>
      </div>
    </div>
  `
})
export class LoginRequiredComponent {
  shop = inject(ShopService);
  router = inject(Router);

  close() {
    this.shop.closeLoginRequired();
  }

  go(to: string) {
    this.close();
    // Redirect logic
    this.router.navigate([to], { queryParams: { redirect: this.router.url } });
  }
}
