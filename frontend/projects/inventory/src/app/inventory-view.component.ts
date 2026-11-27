import { Component, signal, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from 'shared-ui';

interface InventoryItem {
  id?: string;
  sku: string;
  name: string;
  warehouse: string;
  stock: number;
  reserved: number;
  reorderPoint: number;
  status: 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK';
}

@Component({
  selector: 'app-inventory-view',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="max-w-7xl mx-auto px-4 py-8">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <h1 class="text-2xl font-bold text-gray-900 tracking-tight">Warehouse Inventory Management</h1>
          <p class="text-sm text-gray-600 mt-1">Live stock levels across Lagos, Abuja, and Port Harcourt distribution hubs.</p>
        </div>
        <button
          (click)="openAdjustModal()"
          class="px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold shadow-sm transition">
          + New Stock Adjustment
        </button>
      </div>

      <div *ngIf="loading()" class="py-16 text-center text-gray-500">
        <div class="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-3"></div>
        Loading inventory data...
      </div>

      <div *ngIf="!loading()" class="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
        <div class="p-4 border-b border-gray-200 bg-gray-50 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <input
            type="text"
            [(ngModel)]="searchQuery"
            placeholder="Search by SKU or Product Name..."
            class="px-4 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none w-full sm:w-80" />
          <div class="text-xs text-gray-500 font-medium">
            Showing {{ filteredItems().length }} of {{ items().length }} items
          </div>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm text-gray-700">
            <thead class="bg-gray-100 text-xs font-semibold uppercase text-gray-600">
              <tr>
                <th class="px-6 py-3">SKU</th>
                <th class="px-6 py-3">Product Name</th>
                <th class="px-6 py-3">Warehouse Hub</th>
                <th class="px-6 py-3 text-right">In Stock</th>
                <th class="px-6 py-3 text-right">Reserved</th>
                <th class="px-6 py-3 text-right">Available</th>
                <th class="px-6 py-3 text-center">Status</th>
                <th class="px-6 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr *ngFor="let item of filteredItems()" class="hover:bg-gray-50 transition">
                <td class="px-6 py-4 font-mono font-medium text-blue-600">{{ item.sku }}</td>
                <td class="px-6 py-4 font-medium text-gray-900">{{ item.name }}</td>
                <td class="px-6 py-4 text-gray-600">{{ item.warehouse }}</td>
                <td class="px-6 py-4 text-right font-semibold text-gray-900">{{ item.stock }}</td>
                <td class="px-6 py-4 text-right text-gray-500">{{ item.reserved }}</td>
                <td class="px-6 py-4 text-right font-semibold text-emerald-600">{{ item.stock - item.reserved }}</td>
                <td class="px-6 py-4 text-center">
                  <span [class]="getStatusClass(item)" class="px-2.5 py-1 rounded-full text-xs font-bold uppercase tracking-wider">
                    {{ item.status }}
                  </span>
                </td>
                <td class="px-6 py-4 text-right">
                  <button
                    (click)="openAdjustForSku(item.sku)"
                    class="text-xs font-semibold text-blue-600 hover:text-blue-800">
                    Adjust
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Stock Adjustment Modal -->
      <div *ngIf="showModal()" class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
        <div class="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl">
          <div class="flex items-center justify-between pb-4 border-b border-gray-100">
            <h3 class="text-lg font-bold text-gray-900">Post Stock Adjustment</h3>
            <button (click)="showModal.set(false)" class="text-gray-400 hover:text-gray-600">&times;</button>
          </div>

          <form (ngSubmit)="submitAdjustment()" class="space-y-4 mt-4">
            <div>
              <label class="block text-xs font-semibold text-gray-700 uppercase mb-1">Product SKU</label>
              <input
                type="text"
                [(ngModel)]="adjustSku"
                name="adjustSku"
                required
                placeholder="e.g. SKU-1001"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" />
            </div>

            <div>
              <label class="block text-xs font-semibold text-gray-700 uppercase mb-1">Adjustment Quantity (Positive to add, Negative to deduct)</label>
              <input
                type="number"
                [(ngModel)]="adjustQty"
                name="adjustQty"
                required
                placeholder="e.g. 50 or -10"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" />
            </div>

            <div>
              <label class="block text-xs font-semibold text-gray-700 uppercase mb-1">Reason for Adjustment</label>
              <select
                [(ngModel)]="adjustReason"
                name="adjustReason"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none">
                <option value="RESTOCK">Supplier Restock / Inward Shipment</option>
                <option value="CYCLE_COUNT">Physical Inventory Cycle Count Correction</option>
                <option value="DAMAGED">Damaged / Expired Goods Written Off</option>
                <option value="RETURN">Customer Return Restocked</option>
              </select>
            </div>

            <div *ngIf="actionError()" class="text-xs text-red-600 bg-red-50 p-2.5 rounded-lg border border-red-200">
              {{ actionError() }}
            </div>

            <div class="pt-4 border-t border-gray-100 flex items-center justify-end gap-3">
              <button
                type="button"
                (click)="showModal.set(false)"
                class="px-4 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-gray-50">
                Cancel
              </button>
              <button
                type="submit"
                [disabled]="isAdjusting() || !adjustSku || !adjustQty"
                class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-semibold hover:bg-blue-700 disabled:opacity-50">
                {{ isAdjusting() ? 'Posting...' : 'Save Adjustment' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `
})
export class InventoryViewComponent implements OnInit {
  private adminService = inject(AdminService);

  loading = signal(true);
  items = signal<InventoryItem[]>([]);
  searchQuery = '';

  showModal = signal(false);
  adjustSku = '';
  adjustQty = 0;
  adjustReason = 'RESTOCK';
  isAdjusting = signal(false);
  actionError = signal<string | null>(null);

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.loading.set(true);
    this.adminService.loadInventory().subscribe({
      next: (data: any[]) => {
        this.loading.set(false);
        if (data && data.length > 0) {
          this.items.set(data.map(d => ({
            id: d.id,
            sku: d.sku || d.skuId,
            name: d.name || d.productName || ('Product ' + (d.sku || d.skuId)),
            warehouse: d.warehouse || d.location || 'Lagos Hub (Ikeja)',
            stock: d.quantity ?? d.stock ?? 0,
            reserved: d.reserved ?? 0,
            reorderPoint: d.reorderPoint ?? 10,
            status: (d.quantity ?? d.stock ?? 0) <= 0 ? 'OUT_OF_STOCK' : ((d.quantity ?? d.stock ?? 0) < 15 ? 'LOW_STOCK' : 'IN_STOCK')
          })));
        } else {
          this.items.set([]);
        }
      },
      error: (err: any) => {
        console.error('[InventoryView] loadData error:', err);
        this.loading.set(false);
        this.items.set([]);
      }
    });
  }

  filteredItems(): InventoryItem[] {
    if (!this.searchQuery.trim()) return this.items();
    const q = this.searchQuery.toLowerCase();
    return this.items().filter(i => i.sku.toLowerCase().includes(q) || i.name.toLowerCase().includes(q));
  }

  getStatusClass(item: InventoryItem): string {
    switch (item.status) {
      case 'IN_STOCK': return 'bg-emerald-100 text-emerald-800';
      case 'LOW_STOCK': return 'bg-amber-100 text-amber-800';
      case 'OUT_OF_STOCK': return 'bg-rose-100 text-rose-800';
    }
  }

  openAdjustModal() {
    this.adjustSku = '';
    this.adjustQty = 0;
    this.adjustReason = 'RESTOCK';
    this.actionError.set(null);
    this.showModal.set(true);
  }

  openAdjustForSku(sku: string) {
    this.adjustSku = sku;
    this.adjustQty = 0;
    this.adjustReason = 'CYCLE_COUNT';
    this.actionError.set(null);
    this.showModal.set(true);
  }

  submitAdjustment() {
    if (!this.adjustSku || !this.adjustQty) return;
    this.isAdjusting.set(true);
    this.actionError.set(null);

    this.adminService.postStockAdjustment(this.adjustSku, this.adjustQty, this.adjustReason).subscribe({
      next: () => {
        this.isAdjusting.set(false);
        this.showModal.set(false);
        this.loadData();
      },
      error: (err: any) => {
        this.isAdjusting.set(false);
        this.actionError.set(err?.error?.message || 'Failed to post stock adjustment.');
      }
    });
  }
}
