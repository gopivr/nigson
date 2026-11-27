import { WishlistService } from 'shared-ui';
import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterOutlet, Router, RouterLinkActive } from '@angular/router';
import { AffiliateService, AccountService, OrderService, ShopService, SessionService } from 'shared-ui';
import {
  LucideUser, LucidePackage, LucideLogOut, LucideSettings,
  LucideHeart, LucideClipboardList, LucideMapPin, LucideHandshake
} from '@lucide/angular';

@Component({
  selector: 'app-account-layout',
  standalone: true,
  imports: [
    CommonModule, RouterLink, RouterOutlet, RouterLinkActive,
    LucideUser, LucidePackage, LucideLogOut, LucideSettings,
    LucideHeart, LucideClipboardList, LucideMapPin, LucideHandshake
  ],
  template: `
    <div *ngIf="!shop.user() && !session.authenticated()" class="pt-12 pb-16 text-center text-muted-ink">
      Redirecting to sign in...
    </div>

    <section *ngIf="shop.user() || session.authenticated()" class="pt-6 pb-16 md:pt-8 bg-white min-h-screen">
      <div class="mx-auto max-w-7xl px-6">
        <!-- Header -->
        <div class="mb-6 flex items-center gap-5">
          <div
            class="grid h-16 w-16 place-items-center rounded-full text-white text-xl font-extrabold"
            [ngStyle]="{'background': avatarBg()}"
          >
            {{ initialsOf(shop.user()?.fullName || session.fullName()) }}
          </div>
          <div>
            <p class="text-[11px] font-semibold uppercase tracking-[0.25em] text-brand">Welcome back</p>
            <h1 class="text-3xl md:text-4xl font-extrabold tracking-[-0.02em] text-ink">{{ shop.user()?.fullName || session.fullName() || 'Customer' }}</h1>
            <p class="text-sm text-muted-ink">{{ shop.user()?.email || session.email() }}</p>
          </div>
        </div>

        <div class="grid gap-8 lg:grid-cols-[260px_1fr]">
          <!-- Sidebar -->
          <aside class="h-fit rounded-2xl border border-hairline bg-white p-3 shadow-sm">
            <nav class="flex flex-col space-y-1">
              <!-- Profile -->
              <a 
                routerLink="/account" 
                routerLinkActive="bg-brand text-white" 
                [routerLinkActiveOptions]="{exact: true}"
                class="inline-flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-semibold transition-colors text-ink hover:bg-surface-alt"
              >
                <svg lucideUser class="h-4 w-4"></svg> My Profile
              </a>
              <!-- Orders -->
              <a 
                routerLink="/account/orders" 
                routerLinkActive="bg-brand text-white"
                class="inline-flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-semibold transition-colors text-ink hover:bg-surface-alt"
              >
                <svg lucidePackage class="h-4 w-4"></svg> My Orders
                <span *ngIf="orderSvc.totalElements()" class="ml-auto rounded-full bg-brand/10 px-2 py-0.5 text-[10px] font-bold text-brand">
                  {{ orderSvc.totalElements() }}
                </span>
              </a>
              <!-- Addresses (TASK-49) -->
              <a 
                routerLink="/account/addresses" 
                routerLinkActive="bg-brand text-white"
                class="inline-flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-semibold transition-colors text-ink hover:bg-surface-alt"
              >
                <svg lucideMapPin class="h-4 w-4"></svg> Addresses
                <span *ngIf="accountSvc.addresses().length" class="ml-auto rounded-full bg-surface-alt px-2 py-0.5 text-[10px] font-bold text-muted-ink">
                  {{ accountSvc.addresses().length }}
                </span>
              </a>
              <!-- Wishlist -->
              <a 
                routerLink="/account/wishlist" 
                routerLinkActive="bg-brand text-white"
                class="inline-flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-semibold transition-colors text-ink hover:bg-surface-alt"
              >
                <svg lucideHeart class="h-4 w-4"></svg> Wishlist
                <span *ngIf="wishlist.items().length" class="ml-auto rounded-full bg-brand/10 px-2 py-0.5 text-[10px] font-bold text-brand">
                  {{ wishlist.items().length }}
                </span>
              </a>
              <!-- Quote Requests -->
              <a 
                routerLink="/account/quotes" 
                routerLinkActive="bg-brand text-white"
                class="inline-flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-semibold transition-colors text-ink hover:bg-surface-alt"
              >
                <svg lucideClipboardList class="h-4 w-4"></svg> Quote Requests
              </a>
              <a *ngIf="affiliate.state()==='ACTIVE' || affiliate.state()==='SUSPENDED'" routerLink="/account/affiliate" routerLinkActive="bg-brand text-white" class="inline-flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-semibold transition-colors text-ink hover:bg-surface-alt"><svg lucideHandshake aria-hidden="true" class="h-4 w-4 shrink-0"></svg> Affiliate</a>
              <!-- Settings -->
              <a 
                routerLink="/account/settings" 
                routerLinkActive="bg-brand text-white"
                class="inline-flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-semibold transition-colors text-ink hover:bg-surface-alt"
              >
                <svg lucideSettings class="h-4 w-4"></svg> Settings
              </a>
              <!-- Admin Portal Shortcut -->
              <a 
                *ngIf="session.isStaff()"
                routerLink="/admin" 
                class="inline-flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-bold transition-colors text-brand hover:bg-brand/10 border border-brand/30 my-1 shadow-sm"
              >
                <svg lucideSettings class="h-4 w-4"></svg> Staff Portal →
              </a>
              <!-- Divider + Logout -->
              <button
                (click)="logout()"
                class="mt-2 inline-flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-semibold text-ink hover:bg-rose-50 hover:text-rose-600 border-t border-hairline transition-colors"
              >
                <svg lucideLogOut class="h-4 w-4"></svg> Logout
              </button>
            </nav>
          </aside>
          
          <!-- Main Content -->
          <div>
            <router-outlet></router-outlet>
          </div>
        </div>
      </div>
    </section>
  `
})
export class AccountLayoutComponent implements OnInit {
  readonly affiliate = inject(AffiliateService);
  readonly wishlist = inject(WishlistService);
  shop = inject(ShopService);
  session = inject(SessionService);
  accountSvc = inject(AccountService);
  orderSvc = inject(OrderService);
  router = inject(Router);

  ngOnInit() {
    this.affiliate.load().subscribe({error:()=>{}});
    const destination=this.session.consumeLoginReturn();
    if(destination && destination!==this.router.url){ this.router.navigateByUrl(destination); return; }
    if (!this.shop.user() && this.session.authenticated()) {
      this.shop.user.set({
        id: this.session.userId() || 'user',
        fullName: this.session.fullName() || 'Nigson Customer',
        email: this.session.email() || '',
        roles: this.session.roles() || ['CUSTOMER'],
        createdAt: new Date().toISOString()
      });
    }

    if (!this.shop.user() && !this.session.authenticated()) {
      this.router.navigate(['/auth/login'], { queryParams: { redirect: '/account' } });
    } else {
      this.accountSvc.loadAddresses().subscribe();
    }
  }

  logout() {
    this.shop.signOut().subscribe(() => this.router.navigate(['/']));
  }

  initialsOf(name?: string | null): string {
    const n = name || this.session.fullName();
    if (!n) return 'U';
    return n.split(' ').map(s => s[0]).join('').substring(0, 2).toUpperCase();
  }

  avatarBg(): string {
    const name = this.shop.user()?.fullName || this.session.fullName() || '';
    const colors = ['#10B981', '#2563EB', '#7C3AED', '#DB2777', '#D97706'];
    let h = 0;
    for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
    return colors[h % colors.length];
  }
}
