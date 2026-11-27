import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, AdminHeadingComponent, StatCardComponent, BadgeComponent, statusTone, AdminCustomer, AdminOrder } from 'shared-ui';
import { LucideSearch, LucideEye, LucideX } from '@lucide/angular';

@Component({
  selector: 'app-admin-customers',
  standalone: true,
  imports: [CommonModule, FormsModule, AdminHeadingComponent, StatCardComponent, BadgeComponent, LucideSearch, LucideEye, LucideX],
  template: `
    <lib-admin-heading title="Customers" subtitle="Manage registered retail and wholesale clients"></lib-admin-heading>

    <!-- Top KPI Summary Cards -->
    <div class="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <lib-stat-card label="Total Customers" [value]="totalCustomers()"></lib-stat-card>
      <lib-stat-card label="Active Buyers" [value]="activeBuyers()"></lib-stat-card>
      <lib-stat-card label="Total Customer Spend" [value]="totalSpend() | currency:'NGN':'symbol-narrow':'1.0-0'"></lib-stat-card>
      <lib-stat-card label="Avg. Spend / Buyer" [value]="avgSpend() | currency:'NGN':'symbol-narrow':'1.0-0'"></lib-stat-card>
    </div>

    <!-- Search & Filters -->
    <div class="mb-5 flex flex-wrap items-center gap-3">
      <div class="relative min-w-[260px] flex-1">
        <svg lucideSearch class="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"></svg>
        <input
          type="text"
          [(ngModel)]="searchQuery"
          placeholder="Search by name, email, phone, location..."
          class="w-full rounded-xl border border-hairline bg-white pl-10 pr-4 py-2 text-sm focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20 shadow-sm"
        >
      </div>

      <select
        [(ngModel)]="segmentFilter"
        class="rounded-xl border border-hairline bg-white px-4 py-2 text-sm font-medium focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20 shadow-sm"
      >
        <option value="All">All Segments</option>
        <option value="Buyers">Active Buyers (≥ 1 orders)</option>
        <option value="Wholesale">Wholesale / High Value (> ₦200k)</option>
        <option value="Prospects">Prospects (0 orders)</option>
      </select>

      <select
        [(ngModel)]="statusFilter"
        class="rounded-xl border border-hairline bg-white px-4 py-2 text-sm font-medium focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20 shadow-sm"
      >
        <option value="All">All Statuses</option>
        <option value="Active">Active</option>
        <option value="Suspended">Suspended</option>
      </select>
    </div>

    <!-- Customers Table -->
    <div class="rounded-2xl border border-hairline bg-white shadow-sm overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[760px] text-left text-sm text-ink">
          <thead class="border-b border-hairline bg-surface-alt/50 text-[10px] font-bold uppercase tracking-widest text-muted-ink">
            <tr>
              <th class="px-6 py-4">Customer</th>
              <th class="px-6 py-4">Location</th>
              <th class="px-6 py-4">Orders</th>
              <th class="px-6 py-4">Total Spend</th>
              <th class="px-6 py-4">Status</th>
              <th class="px-6 py-4">Joined</th>
              <th class="px-6 py-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-hairline">
            <tr *ngFor="let c of filteredCustomers()" class="transition-colors hover:bg-surface-alt/30">
              <td class="px-6 py-4">
                <div class="flex items-center gap-3">
                  <div class="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-brand/10 font-bold text-xs text-brand uppercase">
                    {{ getInitials(c.name) }}
                  </div>
                  <div>
                    <p class="font-bold text-ink">{{ c.name }}</p>
                    <p class="text-xs text-muted-ink">{{ c.email }}</p>
                    <p *ngIf="c.phone" class="text-xs text-muted-ink">{{ c.phone }}</p>
                  </div>
                </div>
              </td>
              <td class="px-6 py-4 text-muted-ink">
                <span class="text-xs font-medium">{{ c.location || '—' }}</span>
              </td>
              <td class="px-6 py-4 font-semibold">
                <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold"
                  [ngClass]="c.orders > 0 ? 'bg-emerald-50 text-emerald-700 border border-emerald-100' : 'bg-gray-100 text-gray-600'">
                  {{ c.orders }} {{ c.orders === 1 ? 'order' : 'orders' }}
                </span>
              </td>
              <td class="px-6 py-4 font-bold text-brand">
                {{ c.spend | currency:'NGN':'symbol-narrow':'1.0-0' }}
              </td>
              <td class="px-6 py-4">
                <lib-badge [tone]="getTone(c.status)">{{ c.status }}</lib-badge>
              </td>
              <td class="px-6 py-4 whitespace-nowrap text-xs text-muted-ink">
                {{ c.joined | date:'mediumDate' }}
              </td>
              <td class="px-6 py-4 text-right">
                <div class="flex items-center justify-end gap-1.5">
                  <button
                    (click)="selectedCustomer.set(c)"
                    class="grid h-8 w-8 place-items-center rounded-lg border border-hairline bg-white text-muted-ink transition-all hover:bg-brand/10 hover:text-brand hover:border-brand/30 shadow-xs"
                    title="View Customer Details"
                  >
                    <svg lucideEye class="h-4 w-4"></svg>
                  </button>
                </div>
              </td>
            </tr>
            <tr *ngIf="filteredCustomers().length === 0">
              <td colspan="7" class="p-12 text-center text-muted-ink">
                <div class="flex flex-col items-center justify-center gap-2">
                  <p class="text-base font-semibold text-ink">No customers found</p>
                  <p class="text-xs text-muted-ink">Try adjusting your search or segment filter.</p>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Customer Details Modal -->
    <div *ngIf="selectedCustomer()" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
      <div class="w-full max-w-2xl rounded-[24px] border border-gray-100 bg-white p-6 sm:p-8 shadow-2xl animate-in fade-in zoom-in-95 duration-200 max-h-[90vh] overflow-y-auto">
        <!-- Modal Header -->
        <div class="mb-6 flex items-center justify-between border-b border-hairline pb-4">
          <div class="flex items-center gap-3">
            <div class="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-brand/10 text-base font-extrabold text-brand uppercase">
              {{ getInitials(selectedCustomer()!.name) }}
            </div>
            <div>
              <h2 class="text-lg font-extrabold text-ink">{{ selectedCustomer()!.name }}</h2>
              <p class="text-xs text-muted-ink">Customer Profile & Order History</p>
            </div>
          </div>
          <button (click)="selectedCustomer.set(null)" class="grid h-8 w-8 place-items-center rounded-full bg-gray-100 text-gray-500 hover:bg-gray-200 transition-colors">
            <svg lucideX class="h-4 w-4"></svg>
          </button>
        </div>

        <!-- Details Grid -->
        <div class="mb-6 grid gap-3 sm:grid-cols-2 text-sm">
          <div class="rounded-xl border border-hairline bg-surface/40 p-3.5">
            <p class="text-[10px] font-bold uppercase tracking-wider text-muted-ink">Email Address</p>
            <p class="mt-1 font-semibold text-ink break-all">{{ selectedCustomer()!.email }}</p>
          </div>
          <div class="rounded-xl border border-hairline bg-surface/40 p-3.5">
            <p class="text-[10px] font-bold uppercase tracking-wider text-muted-ink">Phone Number</p>
            <p class="mt-1 font-semibold text-ink">{{ selectedCustomer()!.phone || '—' }}</p>
          </div>
          <div class="rounded-xl border border-hairline bg-surface/40 p-3.5">
            <p class="text-[10px] font-bold uppercase tracking-wider text-muted-ink">Primary Shipping Location</p>
            <p class="mt-1 font-semibold text-ink">{{ selectedCustomer()!.location || '—' }}</p>
          </div>
          <div class="rounded-xl border border-hairline bg-surface/40 p-3.5">
            <p class="text-[10px] font-bold uppercase tracking-wider text-muted-ink">Member Since</p>
            <p class="mt-1 font-semibold text-ink">{{ selectedCustomer()!.joined | date:'mediumDate' }}</p>
          </div>
        </div>

        <!-- Metrics Overview Cards -->
        <div class="mb-6 grid grid-cols-3 gap-3 text-center">
          <div class="rounded-xl bg-gray-50 p-3 border border-gray-100">
            <p class="text-[10px] font-bold uppercase tracking-wider text-muted-ink">Total Orders</p>
            <p class="mt-1 text-lg font-extrabold text-ink">{{ selectedCustomer()!.orders }}</p>
          </div>
          <div class="rounded-xl bg-gray-50 p-3 border border-gray-100">
            <p class="text-[10px] font-bold uppercase tracking-wider text-muted-ink">Total Spend</p>
            <p class="mt-1 text-lg font-extrabold text-brand">{{ selectedCustomer()!.spend | currency:'NGN':'symbol-narrow':'1.0-0' }}</p>
          </div>
          <div class="rounded-xl bg-gray-50 p-3 border border-gray-100">
            <p class="text-[10px] font-bold uppercase tracking-wider text-muted-ink">Avg Order Value</p>
            <p class="mt-1 text-lg font-extrabold text-ink">
              {{ (selectedCustomer()!.orders > 0 ? (selectedCustomer()!.spend / selectedCustomer()!.orders) : 0) | currency:'NGN':'symbol-narrow':'1.0-0' }}
            </p>
          </div>
        </div>

        <!-- Status Management Action -->
        <div class="mb-6 flex items-center justify-between rounded-xl border border-hairline bg-surface-alt/40 p-4">
          <div>
            <p class="text-xs font-bold text-ink">Account Status</p>
            <div class="mt-1 flex items-center gap-2">
              <lib-badge [tone]="getTone(selectedCustomer()!.status)">{{ selectedCustomer()!.status }}</lib-badge>
              <span class="text-xs text-muted-ink">
                {{ selectedCustomer()!.status === 'Active' ? 'Account has active purchasing privileges' : 'Account is temporarily suspended' }}
              </span>
            </div>
          </div>
          <p class="text-xs text-muted-ink">Customer profile changes are managed by the account owner.</p>
        </div>

        <!-- Associated Order History -->
        <div class="border-t border-hairline pt-4">
          <h3 class="mb-3 text-sm font-bold text-ink">Recent Purchase History</h3>
          <div *ngIf="customerOrders().length > 0" class="overflow-hidden rounded-xl border border-hairline">
            <table class="w-full text-left text-xs">
              <thead class="bg-gray-50 text-[10px] font-bold uppercase tracking-wider text-muted-ink">
                <tr>
                  <th class="px-3 py-2">Order #</th>
                  <th class="px-3 py-2">Date</th>
                  <th class="px-3 py-2">Items</th>
                  <th class="px-3 py-2">Total</th>
                  <th class="px-3 py-2">Status</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-hairline">
                <tr *ngFor="let ord of customerOrders()" class="hover:bg-gray-50/50">
                  <td class="px-3 py-2 font-semibold text-ink">{{ ord.number }}</td>
                  <td class="px-3 py-2 text-muted-ink">{{ ord.date | date:'shortDate' }}</td>
                  <td class="px-3 py-2 text-muted-ink">{{ ord.items.length }} items</td>
                  <td class="px-3 py-2 font-bold text-brand">{{ ord.total | currency:'NGN':'symbol-narrow':'1.0-0' }}</td>
                  <td class="px-3 py-2">
                    <lib-badge [tone]="getTone(ord.status)">{{ ord.status }}</lib-badge>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div *ngIf="customerOrders().length === 0" class="rounded-xl border border-dashed border-hairline p-6 text-center text-xs text-muted-ink">
            No recent online order records found for this customer.
          </div>
        </div>

        <!-- Close Button -->
        <div class="mt-6 flex justify-end">
          <button
            (click)="selectedCustomer.set(null)"
            class="rounded-xl bg-gray-100 px-5 py-2 text-xs font-bold text-gray-700 hover:bg-gray-200 transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  `
})
export class AdminCustomersComponent implements OnInit {
  admin = inject(AdminService);
  searchQuery = signal('');
  segmentFilter = signal<'All' | 'Buyers' | 'Wholesale' | 'Prospects'>('All');
  statusFilter = signal<'All' | 'Active' | 'Suspended'>('All');
  selectedCustomer = signal<AdminCustomer | null>(null);

  ngOnInit() {
    this.admin.loadStaffCustomers().subscribe();
    if(this.admin.session.hasRole('OPERATIONS')) this.admin.loadStaffOrders().subscribe();
  }

  totalCustomers = computed(() => this.admin.customers().length);

  activeBuyers = computed(() => this.admin.customers().filter(c => (c.orders || 0) > 0).length);

  totalSpend = computed(() => this.admin.customers().reduce((sum, c) => sum + (c.spend || 0), 0));

  avgSpend = computed(() => {
    const buyers = this.activeBuyers();
    return buyers > 0 ? this.totalSpend() / buyers : 0;
  });

  filteredCustomers = computed(() => {
    let list = this.admin.customers();
    const q = this.searchQuery().toLowerCase().trim();
    if (q) {
      list = list.filter(c =>
        (c.name || '').toLowerCase().includes(q) ||
        (c.email || '').toLowerCase().includes(q) ||
        (c.phone || '').toLowerCase().includes(q) ||
        (c.location || '').toLowerCase().includes(q)
      );
    }

    const seg = this.segmentFilter();
    if (seg === 'Buyers') {
      list = list.filter(c => (c.orders || 0) > 0);
    } else if (seg === 'Wholesale') {
      list = list.filter(c => (c.spend || 0) >= 200000 || (c.orders || 0) >= 3);
    } else if (seg === 'Prospects') {
      list = list.filter(c => (c.orders || 0) === 0);
    }

    const st = this.statusFilter();
    if (st !== 'All') {
      list = list.filter(c => c.status === st);
    }

    return list;
  });

  customerOrders = computed(() => {
    const cust = this.selectedCustomer();
    if (!cust || !cust.email) return [];
    const email = cust.email.toLowerCase().trim();
    return this.admin.orders().filter(o => (o.customer?.email || '').toLowerCase().trim() === email);
  });

  getInitials(name: string): string {
    if (!name) return 'CU';
    const parts = name.trim().split(/\s+/);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return name.slice(0, 2).toUpperCase();
  }

  toggleStatus(cust: AdminCustomer) {
    const nextStatus = cust.status === 'Active' ? 'Suspended' : 'Active';
    this.admin.updateCustomerStatus(cust.id, nextStatus);
    if (this.selectedCustomer()?.id === cust.id) {
      this.selectedCustomer.set({ ...cust, status: nextStatus });
    }
  }

  getTone(status: string) {
    return statusTone(status);
  }
}
