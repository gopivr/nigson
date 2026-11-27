import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminService, AdminHeadingComponent, StatCardComponent } from 'shared-ui';
import { LucideTrendingUp, LucidePackage, LucideUsers, LucideShoppingCart, LucideClipboardList, LucideBuilding2, LucideBoxes, LucideMail } from '@lucide/angular';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-admin-analytics',
  standalone: true,
  imports: [
    CommonModule, AdminHeadingComponent, StatCardComponent,
    LucideTrendingUp, LucidePackage, LucideUsers, LucideShoppingCart,
    LucideClipboardList, LucideBuilding2, LucideBoxes, LucideMail
  ],
  template: `
    <lib-admin-heading title="Business Analytics" subtitle="Performance across catalogue, sales and lead generation"></lib-admin-heading>

    <p *ngIf="reportError()" role="alert" class="mb-4 rounded-xl border border-red-200 bg-red-50 p-4">{{ reportError() }}</p>
    <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <lib-stat-card label="Total sales" [value]="reportSales() === null ? 'Unavailable' : (reportSales() | currency:'NGN':'symbol-narrow':'1.0-0')" [hint]="reportOrders() === null ? '' : reportOrders() + ' orders in report'" [icon]="true">
        <svg lucideTrendingUp class="h-5 w-5"></svg>
      </lib-stat-card>
      <lib-stat-card label="Average Order Value" [value]="reportSales() === null ? 'Unavailable' : (aov() | currency:'NGN':'symbol-narrow':'1.0-0')" [icon]="true">
        <svg lucideShoppingCart class="h-5 w-5"></svg>
      </lib-stat-card>
      <lib-stat-card label="Total Customers" [value]="reportCustomers() ?? 'Unavailable'" [icon]="true">
        <svg lucideUsers class="h-5 w-5"></svg>
      </lib-stat-card>
      <lib-stat-card label="Products in report" [value]="reportProducts() ?? 'Unavailable'" [icon]="true">
        <svg lucidePackage class="h-5 w-5"></svg>
      </lib-stat-card>

      <lib-stat-card *ngIf="admin.can('quotes')" label="Quote Requests" [value]="admin.quotes().length" [icon]="true">
        <svg lucideClipboardList class="h-5 w-5"></svg>
      </lib-stat-card>
      <lib-stat-card *ngIf="admin.can('distributors')" label="Distributor Apps" [value]="admin.applications().length" [icon]="true">
        <svg lucideBuilding2 class="h-5 w-5"></svg>
      </lib-stat-card>
      <lib-stat-card *ngIf="admin.can('inquiries')" label="Contact Inquiries" [value]="admin.inquiries().length" [icon]="true">
        <svg lucideMail class="h-5 w-5"></svg>
      </lib-stat-card>
      <lib-stat-card label="Total Units in Stock" [value]="reportStock() ?? 'Unavailable'" [icon]="true">
        <svg lucideBoxes class="h-5 w-5"></svg>
      </lib-stat-card>
    </div>

    <!-- Charts Section -->
    <div *ngIf="admin.session.hasRole('OPERATIONS') && admin.session.hasRole('CONTENT_EDITOR')" class="mt-8 grid gap-6 lg:grid-cols-2">
      <!-- Sales Trend (Last 7 Days) -->
      <div class="rounded-2xl border border-hairline bg-white p-6 shadow-sm">
        <h3 class="mb-6 font-semibold">Sales Trend (Last 7 Days)</h3>
        <div class="flex h-48 items-end justify-between gap-2">
          <div *ngFor="let day of weeklySalesTrend()"
               [title]="day.label + ': ₦' + (day.total | number:'1.0-0') + ' (' + day.count + ' orders)'"
               class="group relative flex h-full w-full flex-col items-center justify-end">
            <div class="w-full rounded-t-md bg-brand transition-all hover:opacity-80"
                 [style.height.%]="day.pct"></div>
          </div>
        </div>
        <div class="mt-2 flex justify-between text-xs text-muted-ink">
          <span *ngFor="let day of weeklySalesTrend()">{{ day.label }}</span>
        </div>
        <p *ngIf="!hasWeeklySales()" class="mt-3 text-xs text-muted-ink">
          No completed order sales recorded in the last 7 days.
        </p>
      </div>

      <!-- Inventory Status -->
      <div class="rounded-2xl border border-hairline bg-white p-6 shadow-sm">
        <h3 class="mb-6 font-semibold">Top Products by Stock</h3>
        <div class="space-y-4">
          <div *ngFor="let p of admin.products().slice(0, 5)" class="flex items-center justify-between gap-4">
            <div class="flex-1 truncate text-sm font-medium">{{ p.name }}</div>
            <div class="flex w-1/3 items-center gap-2">
              <div class="h-2 flex-1 overflow-hidden rounded-full bg-surface-alt">
                <div class="h-full rounded-full bg-brand" [style.width.%]="Math.min((p.stock / 200) * 100, 100)"></div>
              </div>
              <span class="w-8 text-right text-xs text-muted-ink">{{ p.stock }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AnalyticsComponent implements OnInit {
  admin = inject(AdminService);
  Math = Math;

  reportError = signal('');
  reportProducts = signal<number | null>(null);
  reportStock = signal<number | null>(null);
  reportSales = signal<number | null>(null);
  reportOrders = signal<number | null>(null);
  reportCustomers = signal<number | null>(null);

  ngOnInit() {
    if(this.admin.session.hasRole('OPERATIONS')) this.admin.loadStaffOrders().subscribe();
    if(this.admin.session.hasRole('CONTENT_EDITOR')) this.admin.loadStaffProducts().subscribe();
    this.admin.loadCommerceReport().subscribe({next:rep => {
      if (rep) {
        this.reportProducts.set(rep.products?.length ?? 0);
        this.reportStock.set((rep.stock ?? []).reduce((sum:number,row:any)=>sum+Number(row.onHand),0));
        if (rep.sales?.amount) this.reportSales.set(Number(rep.sales.amount));
        if (rep.orders !== undefined) this.reportOrders.set(rep.orders);
        if (rep.customers !== undefined) this.reportCustomers.set(rep.customers);
      }
    },error:error=>{this.reportError.set(this.admin.message(error));}});
  }

  readonly weeklySalesTrend = computed(() => {
    const orders = this.liveOrders();
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const buckets: { key: string; label: string; total: number; count: number; pct: number }[] = [];
    for (let i = 6; i >= 0; i--) {
      const d = new Date(today);
      d.setDate(today.getDate() - i);
      const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      buckets.push({
        key,
        label: d.toLocaleDateString('en-US', { weekday: 'short' }),
        total: 0,
        count: 0,
        pct: 0,
      });
    }
    const byKey = new Map(buckets.map((b) => [b.key, b]));
    for (const o of orders) {
      if (!o.date) continue;
      const parsed = new Date(o.date);
      if (Number.isNaN(parsed.getTime())) continue;
      const key = `${parsed.getFullYear()}-${String(parsed.getMonth() + 1).padStart(2, '0')}-${String(parsed.getDate()).padStart(2, '0')}`;
      const bucket = byKey.get(key);
      if (bucket) {
        bucket.total += o.total || 0;
        bucket.count += 1;
      }
    }
    const maxTotal = Math.max(...buckets.map((b) => b.total), 0);
    for (const bucket of buckets) {
      bucket.pct = maxTotal > 0 ? Math.round((bucket.total / maxTotal) * 100) : 0;
    }
    return buckets;
  });

  readonly hasWeeklySales = computed(() => this.weeklySalesTrend().some((b) => b.total > 0));

  get liveOrders() {
    return () => this.admin.orders().filter((o) => o.status !== "Cancelled");
  }

  get sales() {
    return () => this.reportSales() !== null ? this.reportSales()! : this.liveOrders().reduce((n, o) => n + o.total, 0);
  }

  get aov() {
    return () => {
      const l = this.liveOrders();
      const count = this.reportOrders() !== null ? this.reportOrders()! : l.length;
      return count ? Math.round(this.sales() / count) : 0;
    };
  }

  get inventory() {
    return () => this.admin.products().reduce((n, p) => n + p.stock, 0);
  }
}
