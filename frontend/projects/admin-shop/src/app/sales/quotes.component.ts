import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, AdminHeadingComponent, BadgeComponent, statusTone, StatCardComponent } from 'shared-ui';
import { LucideSearch, LucideEye, LucideX } from '@lucide/angular';

@Component({
  selector: 'app-admin-quotes',
  standalone: true,
  imports: [CommonModule, FormsModule, AdminHeadingComponent, BadgeComponent, StatCardComponent, LucideSearch, LucideEye, LucideX],
  template: `
    <lib-admin-heading title="Quote Requests" subtitle="Wholesale pricing requests from customers and retailers"></lib-admin-heading>

    <div *ngIf="admin.quoteLoadError()" class="mb-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 flex items-center justify-between">
      <span>{{ admin.quoteLoadError() }}</span>
      <button (click)="admin.loadStaffQuotes().subscribe()" class="font-semibold text-red-600 hover:text-red-800">Try again</button>
    </div>
    
    <div class="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <lib-stat-card label="Total requests" [value]="admin.quotes().length.toString()"></lib-stat-card>
      <lib-stat-card label="Pending" [value]="getStat('Pending').toString()"></lib-stat-card>
      <lib-stat-card label="Quoted" [value]="getStat('Quoted').toString()"></lib-stat-card>
      <lib-stat-card label="Closed" [value]="getStat('Closed').toString()"></lib-stat-card>
    </div>

    <div class="mb-5 flex flex-wrap gap-3">
      <div class="relative w-[320px]">
        <svg lucideSearch class="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"></svg>
        <input type="text" [(ngModel)]="searchQuery" placeholder="Search quote requests..." class="w-full rounded-full border border-gray-200 bg-white py-2 pl-10 pr-4 text-sm text-ink shadow-sm outline-none transition-all focus:border-gray-300 focus:ring-4 focus:ring-gray-100">
      </div>
      <select [(ngModel)]="statusFilter" class="rounded-full border border-gray-200 bg-white px-5 py-2 text-sm text-ink shadow-sm outline-none focus:border-gray-300 focus:ring-4 focus:ring-gray-100">
        <option *ngFor="let s of statuses" [value]="s">{{ s }}</option>
      </select>
    </div>

    <div class="rounded-[20px] border border-gray-100 bg-white shadow-sm overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[720px] text-left text-sm text-ink">
          <thead class="border-b border-gray-100 bg-white text-[10px] font-bold uppercase tracking-widest text-muted-ink">
            <tr>
              <th class="px-6 py-5">Request</th>
              <th class="px-6 py-5">Customer</th>
              <th class="px-6 py-5">Product</th>
              <th class="px-6 py-5">Qty</th>
              <th class="px-6 py-5">Date</th>
              <th class="px-6 py-5">Status</th>
              <th class="px-6 py-5 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr *ngFor="let q of filteredList()" class="transition-colors hover:bg-gray-50/50">
              <td class="px-6 py-4 whitespace-nowrap font-semibold">{{ q.number }}</td>
              <td class="px-6 py-4">{{ q.customer }}<br><span class="text-xs text-muted-ink">{{ q.email }}</span></td>
              <td class="px-6 py-4">{{ q.product }}<br><span class="text-xs text-muted-ink">{{ q.sku }}</span></td>
              <td class="px-6 py-4 font-semibold">{{ q.quantity || '—' }}</td>
              <td class="px-6 py-4 whitespace-nowrap text-muted-ink">{{ q.date | date:'mediumDate' }}</td>
              <td class="px-6 py-4"><lib-badge [tone]="getTone(q.status)">{{ q.status }}</lib-badge></td>
              <td class="px-6 py-4 text-right">
                <div class="flex justify-end">
                  <button (click)="viewDetail(q)" class="grid h-8 w-8 place-items-center rounded-full border border-gray-200 bg-white text-gray-500 hover:bg-gray-50 hover:text-gray-900 transition-all shadow-sm" title="View"><svg lucideEye class="h-3.5 w-3.5"></svg></button>
                </div>
              </td>
            </tr>
            <tr *ngIf="filteredList().length === 0">
              <td colspan="7" class="p-12 text-center text-muted-ink">No quote requests match your filters.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Modal Overlay -->
    <div *ngIf="view()" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 backdrop-blur-sm p-4">
      <div class="w-full max-w-2xl rounded-[24px] border border-gray-100 bg-white p-8 shadow-2xl animate-in fade-in zoom-in-95 duration-200">
        <div class="mb-6 flex items-center justify-between">
          <h2 class="text-lg font-extrabold text-ink">Quote {{ view().number }}</h2>
          <button (click)="view.set(null)" class="grid h-8 w-8 place-items-center rounded-full bg-gray-50 text-gray-500 hover:bg-gray-100 transition-colors"><svg lucideX class="h-4 w-4"></svg></button>
        </div>
        
        <div class="space-y-4 text-sm">
          <div class="grid gap-3 sm:grid-cols-2">
            <div class="rounded-xl border border-gray-100 px-4 py-3">
              <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink">Customer</p>
              <p class="mt-1 font-semibold text-ink">{{ view().customer }}</p>
            </div>
            <div class="rounded-xl border border-gray-100 px-4 py-3">
              <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink">Email</p>
              <p class="mt-1 font-semibold text-ink">{{ view().email }}</p>
            </div>
            <div class="rounded-xl border border-gray-100 px-4 py-3">
              <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink">Phone</p>
              <p class="mt-1 font-semibold text-ink">{{ view().phone || '—' }}</p>
            </div>
            <div class="rounded-xl border border-gray-100 px-4 py-3">
              <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink">Product</p>
              <p class="mt-1 font-semibold text-ink">{{ view().product }}</p>
            </div>
            <div class="rounded-xl border border-gray-100 px-4 py-3">
              <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink">SKU</p>
              <p class="mt-1 font-semibold text-ink">{{ view().sku || '—' }}</p>
            </div>
            <div class="rounded-xl border border-gray-100 px-4 py-3">
              <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink">Quantity</p>
              <p class="mt-1 font-semibold text-ink">{{ view().quantity || '—' }}</p>
            </div>
            <div class="rounded-xl border border-gray-100 px-4 py-3">
              <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink">Submitted</p>
              <p class="mt-1 font-semibold text-ink">{{ view().date | date:'mediumDate' }}</p>
            </div>
            <div class="rounded-xl border border-gray-100 px-4 py-3">
              <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink">Status</p>
              <p class="mt-1 font-semibold text-ink">{{ view().status }}</p>
            </div>
          </div>
          
          <div class="rounded-xl border border-gray-100 px-4 py-3">
            <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-ink">Customer message</p>
            <p class="mt-1 text-sm text-ink">{{ view().message }}</p>
          </div>
          
          <div>
            <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Quote response</label>
            <textarea rows="3" [(ngModel)]="reply" placeholder="Enter the pricing and lead time you want to send..." class="w-full rounded-[20px] border border-gray-200 bg-white px-5 py-4 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20"></textarea>
          </div>
          
          <div class="flex flex-wrap items-center justify-between gap-3 pt-1">
            <select [(ngModel)]="view().status" (change)="setStatus(view().id, view().status)" class="rounded-full border border-gray-200 bg-white px-5 py-2.5 text-sm font-semibold text-ink shadow-sm outline-none focus:border-gray-300 focus:ring-4 focus:ring-gray-100">
              <option *ngFor="let s of rawStatuses" [value]="s">{{ s }}</option>
            </select>
            <button (click)="sendQuote(view().id)" [disabled]="!reply" class="rounded-full bg-brand px-6 py-2.5 text-sm font-bold text-white hover:bg-brand-600 shadow-sm transition-colors disabled:opacity-50">Send quote</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminQuotesComponent implements OnInit {
  ngOnInit() {
    this.admin.loadStaffQuotes().subscribe();
  }
  admin = inject(AdminService);
  
  searchQuery = signal('');
  statusFilter = signal('All statuses');
  view = signal<any>(null);
  reply = '';

  rawStatuses = ["Pending", "Under Review", "Quoted", "Closed"];
  statuses = ["All statuses", ...this.rawStatuses];

  getTone(status: string) { return statusTone(status); }

  getStat(status: string) {
    return this.admin.quotes().filter(q => q.status === status).length;
  }

  filteredList = computed(() => {
    let list = this.admin.quotes();
    const q = this.searchQuery().toLowerCase();
    const s = this.statusFilter();

    if (q) {
      list = list.filter(x => 
        (x.customer + x.email + x.product + (x.sku || '') + x.number).toLowerCase().includes(q)
      );
    }
    if (s !== 'All statuses') {
      list = list.filter(x => x.status === s);
    }
    return list;
  });

  viewDetail(quote: any) {
    this.view.set({ ...quote });
    this.reply = '';
  }

  setStatus(id: string, s: any) {
    this.admin.setQuoteStatus(id, s);
    if (this.view() && this.view().id === id) {
      this.view.update((v: any) => ({ ...v, status: s }));
    }
  }

  sendQuote(id: string) {
    if (!this.reply.trim()) return;
    this.setStatus(id, "Quoted");
    this.view.set(null);
  }
}
