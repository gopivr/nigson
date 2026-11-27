// Polyfill dev globals in module scope
if (typeof (globalThis as any).ngDevMode === 'undefined') {
  (globalThis as any).ngDevMode = true;
}
if (typeof (globalThis as any).ngServerMode === 'undefined') {
  (globalThis as any).ngServerMode = false;
}

import { Component, inject, signal } from '@angular/core';
import { RouterOutlet, Router, NavigationEnd, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { filter } from 'rxjs/operators';
import { HeaderComponent } from './layout/header/header';
import { FooterComponent } from './layout/footer/footer';
import { FloatingActionsComponent } from './layout/floating-actions.component';
import { LoginRequiredComponent, ShopService, ApiErrorsService } from 'shared-ui';
import { LucideCheck, LucideX } from '@lucide/angular';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule, RouterOutlet, RouterLink,
    HeaderComponent, FooterComponent, FloatingActionsComponent,
    LoginRequiredComponent, LucideCheck, LucideX
  ],
  template: `
    <div class="flex min-h-screen flex-col font-sans antialiased text-foreground bg-background relative">
      <app-header *ngIf="!isAdminRoute()"></app-header>
      <section *ngIf="apiErrors.visibleFor(currentPage()).length" aria-label="Backend errors" class="m-4 space-y-2">
        <div *ngFor="let failure of apiErrors.failures()" role="alert" class="flex justify-between gap-4 rounded-xl border border-red-300 bg-red-50 p-4 text-sm text-red-900">
          <span>{{failure.message}}</span><button type="button" (click)="apiErrors.dismiss(failure.id)" aria-label="Dismiss backend error">✕</button>
        </div>
      </section>
      <main class="flex-1">
        <router-outlet></router-outlet>
      </main>
      <app-footer *ngIf="!isAdminRoute()"></app-footer>
      <app-floating-actions *ngIf="!isAdminRoute()"></app-floating-actions>
      <ui-login-required></ui-login-required>

      <!-- Cart Item Added Toast Notification -->
      <aside
        *ngIf="shop.lastAddedToast(); let toast"
        role="status"
        aria-live="polite"
        class="fixed top-24 right-5 sm:right-8 z-50 flex items-center gap-3.5 rounded-2xl bg-white/95 backdrop-blur-md border border-border px-4 py-3.5 shadow-[0_20px_50px_-15px_rgba(0,0,0,0.35)] animate-fade-in transition-all duration-300 max-w-sm sm:max-w-md"
      >
        <div class="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-emerald-100 text-emerald-700">
          <svg lucideCheck class="h-5 w-5 stroke-[2.5]"></svg>
        </div>
        <div class="min-w-0 flex-1">
          <p class="text-[11px] font-bold uppercase tracking-wider text-emerald-700">Added to Cart!</p>
          <p class="text-sm font-bold text-ink truncate">{{ toast.name }}</p>
          <p *ngIf="toast.price" class="text-xs text-muted-ink font-medium">₦{{ toast.price.toLocaleString('en-NG', { minimumFractionDigits: 2 }) }}</p>
        </div>
        <a
          routerLink="/cart"
          (click)="shop.dismissToast()"
          class="shrink-0 rounded-full bg-ink px-3.5 py-1.5 text-xs font-bold text-white hover:bg-brand transition-colors whitespace-nowrap shadow-sm"
        >
          View Cart →
        </a>
        <button
          type="button"
          (click)="shop.dismissToast()"
          class="shrink-0 p-1 text-muted-ink hover:text-ink transition-colors rounded-full"
          aria-label="Dismiss notification"
          title="Dismiss"
        >
          <svg lucideX class="h-4 w-4"></svg>
        </button>
      </aside>
    </div>
  `
})
export class AppComponent {
  title = 'shell';
  readonly currentPage=signal(typeof window!=='undefined'?window.location.pathname:'/');
  isAdminRoute = signal(typeof window !== 'undefined' && window.location.pathname.startsWith('/admin'));
  shop = inject(ShopService);
  apiErrors = inject(ApiErrorsService);

  constructor() {
    const router = inject(Router);
    router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: any) => {
      this.currentPage.set(event.urlAfterRedirects.split(/[?#]/)[0]);
      this.isAdminRoute.set(event.urlAfterRedirects.startsWith('/admin'));
      if (typeof window !== 'undefined' && !event.urlAfterRedirects.includes('#')) {
        window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
        document.documentElement.scrollTop = 0;
        document.body.scrollTop = 0;
      }
    });
  }
}
