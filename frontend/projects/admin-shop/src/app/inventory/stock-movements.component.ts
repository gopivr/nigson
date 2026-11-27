
import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, AdminHeadingComponent, BadgeComponent } from 'shared-ui';
import { LucideSearch, LucideEye, LucideX } from '@lucide/angular';

@Component({
  selector: 'app-stock-movements',
  standalone: true,
  imports: [CommonModule, FormsModule, AdminHeadingComponent, BadgeComponent, LucideSearch, LucideEye, LucideX],
  template: `
    <lib-admin-heading title="Stock Movements" subtitle="Every stock in and stock out record"></lib-admin-heading>
    
    <div class="mb-6 flex flex-wrap gap-4">
      <div class="relative w-[320px]">
        <svg lucideSearch class="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"></svg>
        <input type="text" [(ngModel)]="searchQuery" placeholder="Search product, reference or user..." class="w-full rounded-full border border-gray-200 bg-white py-2 pl-10 pr-4 text-sm text-ink shadow-sm outline-none transition-all focus:border-gray-300 focus:ring-4 focus:ring-gray-100">
      </div>
      <select [(ngModel)]="typeFilter" class="rounded-full border border-gray-200 bg-white px-5 py-2 text-sm text-ink shadow-sm outline-none focus:border-gray-300 focus:ring-4 focus:ring-gray-100">
        <option *ngFor="let t of TYPES" [value]="t">{{ t }}</option>
      </select>
    </div>

    <div class="rounded-[20px] border border-gray-100 bg-white shadow-sm overflow-hidden relative">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[720px] text-left text-sm text-ink">
          <thead class="border-b border-gray-100 bg-white text-[10px] font-bold uppercase tracking-widest text-muted-ink">
            <tr>
              <th class="px-6 py-5">Date</th>
              <th class="px-6 py-5">Product</th>
              <th class="px-6 py-5">Reason / Type</th>
              <th class="px-6 py-5">Operator</th>
              <th class="px-6 py-5">Quantity</th>
              <th class="px-6 py-5 text-right">Details</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr *ngFor="let l of filteredLogs()" class="transition-colors hover:bg-gray-50/50">
              <td class="px-6 py-4 whitespace-nowrap text-muted-ink">{{ l.date | date:'medium' }}</td>
              <td class="px-6 py-4 font-semibold">{{ l.product }}<br><span class="text-xs font-normal text-muted-ink">{{ l.sku }}</span></td>
              <td class="px-6 py-4">
                <lib-badge [tone]="l.reason === 'Order' ? 'info' : l.reason === 'Stock Received' ? 'success' : 'neutral'">{{ l.reason }}</lib-badge>
              </td>
              <td class="px-6 py-4 text-muted-ink">{{ l.by || 'Administrator' }}</td>
              <td class="px-6 py-4">
                <lib-badge [tone]="l.change >= 0 ? 'success' : 'danger'">
                  {{ l.change > 0 ? '+' : '' }}{{ l.change }}
                </lib-badge>
              </td>
              <td class="px-6 py-4 text-right">
                <div class="flex justify-end">
                  <button (click)="viewDetail(l)" class="grid h-8 w-8 place-items-center rounded-full border border-gray-200 bg-white text-gray-500 hover:bg-gray-50 hover:text-gray-900 transition-all shadow-sm" title="View Details">
                    <svg lucideEye class="h-3.5 w-3.5"></svg>
                  </button>
                </div>
              </td>
            </tr>
            <tr *ngIf="filteredLogs().length === 0">
              <td colspan="6" class="p-12 text-center text-muted-ink">
                <div class="flex flex-col items-center justify-center gap-3">
                  <div class="h-12 w-12 rounded-full bg-gray-50 flex items-center justify-center">
                    <svg lucideSearch class="h-5 w-5 text-gray-400"></svg>
                  </div>
                  <p class="font-medium">No stock movements match your filters.</p>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Modal Overlay -->
    <div *ngIf="detail()" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 backdrop-blur-sm p-4">
      <div class="w-full max-w-md rounded-[24px] border border-gray-100 bg-white p-8 shadow-2xl animate-in fade-in zoom-in-95 duration-200">
        <div class="mb-6 flex items-center justify-between">
          <h2 class="text-lg font-extrabold text-ink">Movement details</h2>
          <button (click)="detail.set(null)" class="grid h-8 w-8 place-items-center rounded-full bg-gray-50 text-gray-500 hover:bg-gray-100 transition-colors">
            <svg lucideX class="h-4 w-4"></svg>
          </button>
        </div>
        
        <div class="space-y-4 text-sm">
          <div class="flex justify-between gap-6 border-b border-gray-100 pb-3">
            <span class="text-muted-ink">Date</span>
            <span class="text-right font-semibold text-ink">{{ detail().date | date:'medium' }}</span>
          </div>
          <div class="flex justify-between gap-6 border-b border-gray-100 pb-3">
            <span class="text-muted-ink">Product</span>
            <span class="text-right font-semibold text-ink">{{ detail().product }} ({{ detail().sku }})</span>
          </div>
          <div class="flex justify-between gap-6 border-b border-gray-100 pb-3">
            <span class="text-muted-ink">Type/Reason</span>
            <span class="text-right font-semibold text-ink">{{ detail().reason }}</span>
          </div>
          <div class="flex justify-between gap-6 border-b border-gray-100 pb-3">
            <span class="text-muted-ink">Quantity</span>
            <span class="text-right font-semibold" [ngClass]="detail().change >= 0 ? 'text-emerald-600' : 'text-rose-600'">
              {{ detail().change >= 0 ? '+' : '' }}{{ detail().change }}
            </span>
          </div>
          <div *ngIf="detail().supplier" class="flex justify-between gap-6 border-b border-gray-100 pb-3">
            <span class="text-muted-ink">Supplier</span>
            <span class="text-right font-semibold text-ink">{{ detail().supplier }}</span>
          </div>
          <div *ngIf="detail().ref" class="flex justify-between gap-6 border-b border-gray-100 pb-3">
            <span class="text-muted-ink">Invoice / Reference</span>
            <span class="text-right font-semibold text-ink">{{ detail().ref }}</span>
          </div>
          <div *ngIf="detail().notes" class="flex justify-between gap-6 border-b border-gray-100 pb-3">
            <span class="text-muted-ink">Notes</span>
            <span class="text-right font-semibold text-ink">{{ detail().notes }}</span>
          </div>
          <div class="flex justify-between gap-6 border-b border-gray-100 pb-3">
            <span class="text-muted-ink">Recorded by</span>
            <span class="text-right font-semibold text-ink">{{ detail().by || 'Administrator' }}</span>
          </div>
        </div>
        
        <div class="mt-8 flex justify-end">
          <button (click)="detail.set(null)" class="rounded-full bg-brand px-6 py-2.5 text-sm font-bold text-white hover:bg-brand-600 shadow-sm transition-colors">Close</button>
        </div>
      </div>
    </div>
  `
})

export class StockMovementsComponent implements OnInit {
  admin = inject(AdminService);


  TYPES = ["All types", "Opening Stock", "Stock Received", "Order", "Return", "Physical Count", "Damaged Goods", "Lost/Stolen", "Promotional Giveaway", "Other"];

  searchQuery = signal('');
  typeFilter = signal('All types');
  detail = signal<any>(null);

  ngOnInit() {
    this.admin.loadStockMovements().subscribe();
    this.admin.loadStaffProducts().subscribe();
    this.admin.loadStaffInventory().subscribe();
  }

  filteredLogs = computed(() => {
    let logs = this.admin.stockLog();
    const q = this.searchQuery().toLowerCase();
    const t = this.typeFilter();

    if (q) {
      logs = logs.filter(l =>
        (l.product && l.product.toLowerCase().includes(q)) ||
        (l.sku && l.sku.toLowerCase().includes(q)) ||
        (l.by && l.by.toLowerCase().includes(q)) ||
        (l.reason && l.reason.toLowerCase().includes(q)) ||
        (l.supplier && l.supplier.toLowerCase().includes(q)) ||
        (l.ref && l.ref.toLowerCase().includes(q)) ||
        (l.notes && l.notes.toLowerCase().includes(q))
      );
    }
    if (t !== 'All types') {
      logs = logs.filter(l => l.reason === t);
    }

    return logs;
  });

  viewDetail(log: any) {
    this.detail.set(log);
  }
}

