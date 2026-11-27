import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterOutlet, RouterLinkActive } from '@angular/router';
import { ShopService, AdminService, SessionService, SectionKey, staffPathSection } from 'shared-ui';
import { 
  LucideLayoutDashboard, LucideFileText, LucidePackage, LucideStar, LucideBuilding2, 
  LucideClipboardList, LucideMail, LucideBoxes, LucideBarChart3, 
  LucideNewspaper, LucideShieldCheck, LucideUserCog, LucideLogOut, LucideMenu,
  LucidePackagePlus, LucideSlidersHorizontal, LucideArrowLeftRight, LucideAlertTriangle, LucideXCircle, LucideWallet
} from '@lucide/angular';

type Item = { to: string; label: string; icon: any; key: SectionKey; exact?: boolean };

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [
    CommonModule, RouterLink, RouterOutlet, RouterLinkActive,
    LucideLayoutDashboard, LucideFileText, LucidePackage, LucideStar, LucideBuilding2, 
    LucideClipboardList, LucideMail, LucideBoxes, LucideBarChart3, 
    LucideNewspaper, LucideShieldCheck, LucideUserCog, LucideLogOut, LucideMenu,
    LucidePackagePlus, LucideSlidersHorizontal, LucideArrowLeftRight, LucideAlertTriangle, LucideXCircle, LucideWallet
  ],
  template: `
    <div *ngIf="!hydrated() || !user() || !isStaff()" class="grid min-h-screen place-items-center bg-surface-alt text-sm text-muted-ink">
      Checking staff access...
    </div>

    <div *ngIf="hydrated() && user() && isStaff()" class="flex h-screen overflow-hidden bg-surface-alt font-sans text-ink">
      <!-- Mobile Sidebar Backdrop -->
      <div 
        *ngIf="sidebarOpen()" 
        class="fixed inset-0 z-40 bg-ink/20 backdrop-blur-sm lg:hidden"
        (click)="sidebarOpen.set(false)"
      ></div>

      <!-- Sidebar -->
      <aside 
        [class]="'fixed inset-y-0 left-0 z-50 w-[260px] flex-col border-r border-hairline bg-white shadow-sm transition-transform lg:static lg:flex lg:translate-x-0 ' + 
                 (sidebarOpen() ? 'flex translate-x-0' : '-translate-x-full')"
      >
        <div class="flex items-center border-b border-hairline px-6 py-4">
          <img src="/assets/logo.png" alt="Nigson" class="w-28 xl:w-32 h-auto object-contain -ml-2" />
          <span class="ml-1 rounded-full bg-brand/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-widest text-brand">{{ session.isAdmin() ? 'Admin' : 'Staff Portal' }}</span>
        </div>

        <nav class="flex-1 overflow-y-auto px-3 py-4">
          <ng-container *ngFor="let g of permittedGroups()">
            <div class="mb-4">
              <p *ngIf="g.title" class="px-3 pb-2 text-[10px] font-bold uppercase tracking-[0.18em] text-muted-ink">{{ g.title }}</p>
              <div class="space-y-1">
                <a 
                  *ngFor="let i of g.items"
                  [routerLink]="i.to"
                  (click)="sidebarOpen.set(false)"
                  routerLinkActive="!bg-brand !text-white"
                  [routerLinkActiveOptions]="{exact: i.exact || false}"
                  class="flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold transition-colors text-muted-ink hover:bg-surface-alt hover:text-ink"
                >
                  <!-- We use a switch or mapping for icons since dynamic component rendering is tricky in simple templates -->
                  <svg *ngIf="i.label === 'Dashboard'" lucideLayoutDashboard class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Pages'" lucideFileText class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Reviews'" lucideStar class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Blog Posts'" lucideNewspaper class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Inventory Overview'" lucideBoxes class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Products'" lucidePackage class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.key === 'categories'" lucideBoxes aria-hidden="true" class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Stock In / Receive'" lucidePackagePlus class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Stock Adjustment'" lucideSlidersHorizontal class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Stock Movements'" lucideArrowLeftRight class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Low Stock'" lucideAlertTriangle class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Out of Stock'" lucideXCircle class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Orders'" lucideClipboardList class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.key === 'affiliatePayouts'" lucideWallet aria-hidden="true" class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Customers'" lucideUserCog class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Distributor Applications'" lucideBuilding2 class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Quote Requests'" lucideClipboardList class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Contact Inquiries'" lucideMail class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Business Analytics'" lucideBarChart3 class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Admin Users'" lucideUserCog class="h-4 w-4 shrink-0"></svg>
                  <svg *ngIf="i.label === 'Roles & Permissions'" lucideShieldCheck class="h-4 w-4 shrink-0"></svg>
                  
                  <span class="truncate">{{ i.label }}</span>
                </a>
              </div>
            </div>
          </ng-container>
        </nav>

        <div class="border-t border-hairline p-3">
          <div class="mb-2 flex items-center gap-3 rounded-xl bg-surface-alt px-3 py-2.5">
            <span class="grid h-9 w-9 place-items-center rounded-full bg-ink text-xs font-bold text-white">
              {{ initialsOf(user()?.fullName) }}
            </span>
            <div class="min-w-0">
              <p class="truncate text-sm font-semibold text-ink">{{ user()?.fullName }}</p>
              <p class="truncate text-[11px] text-muted-ink">{{ currentRole()?.name ?? "Staff" }}</p>
            </div>
          </div>
          <button (click)="handleLogout()" title="Log out" aria-label="Log out" class="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold text-muted-ink transition-colors hover:bg-rose-50 hover:text-rose-600">
            <svg lucideLogOut class="h-4 w-4 shrink-0"></svg> Log out
          </button>
        </div>
      </aside>

      <!-- Main content area -->
      <div class="flex flex-1 flex-col overflow-hidden">
        <header class="flex h-16 items-center justify-between border-b border-hairline bg-white px-4 lg:hidden">
          <div class="flex items-center gap-2">
            <span class="text-lg font-extrabold tracking-tight text-ink">NIG<span class="text-brand">SON</span></span>
          </div>
          <button (click)="sidebarOpen.set(true)" title="Open navigation menu" aria-label="Open navigation menu" class="grid h-10 w-10 place-items-center rounded-full border border-hairline text-ink">
            <svg lucideMenu class="h-5 w-5"></svg>
          </button>
        </header>

        <main class="flex-1 overflow-y-auto p-4 md:p-8">
          <div class="mx-auto max-w-[1200px]">
            <div *ngFor="let failure of pageErrors()" role="alert" class="mb-4 rounded-xl border border-red-300 bg-red-50 p-4 text-sm text-red-800"><strong>{{ failure[0] }}:</strong> {{ failure[1] }}</div>
            <router-outlet></router-outlet>
          </div>
        </main>
      </div>
    </div>
  `
})
export class AdminLayoutComponent implements OnInit {
  shop = inject(ShopService);
  admin = inject(AdminService);
  session = inject(SessionService);
  router = inject(Router);

  user = computed(() => {
    const u = this.shop.user();
    if (u) return u;
    if (this.session.authenticated()) {
      return {
        id: this.session.userId() || '',
        fullName: this.session.fullName() || 'Staff member',
        email: this.session.email() || '',
        roles: this.session.roles(),
        createdAt: new Date().toISOString()
      };
    }
    return null;
  });
  isStaff = computed(() => {
    return this.session.isStaff();
  });
  hydrated = computed(() => this.session.sessionLoaded() || this.shop.hydrated());
  sidebarOpen = signal(false);

  pageErrors():Array<[string,string]> {
    const section=staffPathSection(this.router.url);
    const scopes:Record<string,SectionKey[]>={
      'Staff permissions':['admins'],'Staff users':['admins','roles'],'Assign staff roles':['admins'],
      Products:['products'],'Save product':['products'],'Product publication':['products'],
      Inventory:['inventory'],Warehouses:['inventory'],'Stock movements':['inventory'],'Stock adjustment':['inventory'],
      Orders:['orders'],'Order processing':['orders'],Customers:['customers'],
      'Quote requests':['quotes'],'Quote request update':['quotes'],'Inquiry update':['inquiries'],
      Reviews:['reviews'],'Review moderation':['reviews'],'Commerce report':['analytics'],'Engagement report':['analytics']
    };
    return Object.entries(this.admin.errors()).filter(([key])=>section!==undefined&&scopes[key]?.includes(section));
  }
  currentRole = this.admin.currentRole;

  groups = [
    { items: [{ to: "/admin", label: "Dashboard", icon: null, key: "dashboard" as SectionKey, exact: true }] },
    {
      title: "Content Management",
      items: [
        { to: "/admin/pages", label: "Pages", icon: null, key: "pages" as SectionKey },
        { to: "/admin/reviews", label: "Reviews", icon: null, key: "reviews" as SectionKey },
        { to: "/admin/blog", label: "Blog Posts", icon: null, key: "blog" as SectionKey },
      ],
    },
    {
      title: "Inventory Management",
      items: [
        { to: "/admin/inventory", label: "Inventory Overview", icon: null, key: "inventory" as SectionKey, exact: true },
        { to: "/admin/inventory/products", label: "Products", icon: null, key: "products" as SectionKey },
        { to: "/admin/categories", label: "Categories", icon: null, key: "categories" as SectionKey },
        { to: "/admin/inventory/stock-in", label: "Stock In / Receive", icon: null, key: "inventory" as SectionKey },
        { to: "/admin/inventory/adjustment", label: "Stock Adjustment", icon: null, key: "inventory" as SectionKey },
        { to: "/admin/inventory/movements", label: "Stock Movements", icon: null, key: "inventory" as SectionKey },
        { to: "/admin/inventory/low-stock", label: "Low Stock", icon: null, key: "inventory" as SectionKey },
        { to: "/admin/inventory/out-of-stock", label: "Out of Stock", icon: null, key: "inventory" as SectionKey },
      ],
    },
    {
      title: "Sales Management",
      items: [
        { to: "/admin/orders", label: "Orders", icon: null, key: "orders" as SectionKey },
        { to: "/admin/affiliate-payouts", label: "Affiliate Payouts", icon: null, key: "affiliatePayouts" as SectionKey },
        { to: "/admin/customers", label: "Customers", icon: null, key: "customers" as SectionKey },
      ],
    },
    {
      title: "Lead Management",
      items: [
        { to: "/admin/applications", label: "Distributor Applications", icon: null, key: "distributors" as SectionKey },
        { to: "/admin/quotes", label: "Quote Requests", icon: null, key: "quotes" as SectionKey },
        { to: "/admin/inquiries", label: "Contact Inquiries", icon: null, key: "inquiries" as SectionKey },
        { to: "/admin/analytics", label: "Business Analytics", icon: null, key: "analytics" as SectionKey },
      ],
    },
    {
      title: "User Management",
      items: [
        { to: "/admin/users", label: "Admin Users", icon: null, key: "admins" as SectionKey },
        { to: "/admin/roles", label: "Roles & Permissions", icon: null, key: "roles" as SectionKey },
      ],
    },
  ];

  ngOnInit() {
    const destination=this.session.consumeLoginReturn();
    if(destination && destination!==this.router.url){this.router.navigateByUrl(destination);return;}
    if (this.session.sessionLoaded()) {
      if (!this.session.authenticated()) {
        this.router.navigate(['/auth/login'], { queryParams: { redirect: '/admin' } });
      } else if (!this.isStaff()) {
        this.router.navigate(['/']);
      }
    }
  }

  permittedGroups = computed(() => {
    return this.groups.map(g => ({
      ...g,
      items: g.items.filter(i => this.admin.can(i.key))
    })).filter(g => g.items.length > 0);
  });

  handleLogout() {
    this.shop.signOut().subscribe(() => this.router.navigate(['/auth/login']));
  }

  initialsOf(name?: string) {
    if (!name) return 'NA';
    return name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
  }
}
