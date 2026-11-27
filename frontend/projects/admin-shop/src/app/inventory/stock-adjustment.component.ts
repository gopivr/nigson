import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AdminService, AdminHeadingComponent, BadgeComponent } from 'shared-ui';
import { LucideSearch, LucideX, LucideChevronDown, LucideCheck } from '@lucide/angular';

@Component({
  selector: 'app-stock-adjustment',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule, 
    AdminHeadingComponent, 
    BadgeComponent,
    LucideSearch,
    LucideX,
    LucideChevronDown,
    LucideCheck
  ],
  template: `
    <ng-container *ngIf="isStockIn">
      <lib-admin-heading title="Stock In / Receive Stock" subtitle="Record new stock arriving into the warehouse"></lib-admin-heading>

      <!-- Feedback alerts -->
      <div *ngIf="successMessage()" class="mb-6 rounded-[16px] border border-emerald-200 bg-emerald-50 px-5 py-4 text-sm font-semibold text-emerald-800">
        {{ successMessage() }}
      </div>
      <div *ngIf="errorMessage()" class="mb-6 rounded-[16px] border border-rose-200 bg-rose-50 px-5 py-4 text-sm font-semibold text-rose-800">
        {{ errorMessage() }}
      </div>
      
      <div class="rounded-[20px] border border-gray-100 bg-white p-8 shadow-sm mb-8">
        <label class="mb-4 block text-sm font-semibold">Warehouse
          <select [(ngModel)]="locationId" name="warehouse" class="mt-1 w-full rounded-lg border p-2">
            <option value="">Choose warehouse</option>
            <option *ngFor="let location of admin.stockLocations()" [value]="location.id">{{location.name}} ({{location.code}})</option>
          </select>
        </label>
        
        <form (ngSubmit)="saveStockIn()" class="grid gap-6 sm:grid-cols-2">
          <!-- Searchable Product Selection Field -->
          <div class="relative">
            <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Product *</label>
            <div class="relative">
              <div class="relative flex items-center">
                <svg lucideSearch class="absolute left-4 h-4 w-4 text-gray-400 pointer-events-none z-10"></svg>
                <input
                  type="text"
                  [(ngModel)]="productDisplay"
                  (ngModelChange)="onProductSearchInput($event)"
                  (focus)="openProductDropdown()"
                  (click)="openProductDropdown()"
                  name="productSearchStockIn"
                  placeholder="Search product by name or SKU..."
                  autocomplete="off"
                  class="w-full rounded-full border border-gray-200 bg-white pl-11 pr-12 py-3 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20 font-medium cursor-pointer"
                  [class.pr-10]="!sku"
                />
                
                <button
                  *ngIf="sku"
                  type="button"
                  (click)="clearSelectedProduct($event)"
                  title="Clear product selection"
                  class="absolute right-3.5 z-10 grid h-6 w-6 place-items-center rounded-full text-gray-400 hover:bg-gray-100 hover:text-ink transition-colors"
                >
                  <svg lucideX class="h-3.5 w-3.5"></svg>
                </button>
                <button
                  *ngIf="!sku"
                  type="button"
                  (click)="toggleProductDropdown($event)"
                  class="absolute right-3.5 z-10 grid h-6 w-6 place-items-center text-gray-400"
                >
                  <svg lucideChevronDown class="h-4 w-4"></svg>
                </button>
              </div>

              <!-- Invisible backdrop to close dropdown on outside click -->
              <div
                *ngIf="productDropdownOpen()"
                (click)="closeProductDropdown()"
                class="fixed inset-0 z-20"
              ></div>

              <!-- Search Results Dropdown List -->
              <div
                *ngIf="productDropdownOpen()"
                class="absolute left-0 right-0 top-full z-30 mt-1.5 max-h-64 overflow-y-auto rounded-2xl border border-gray-200 bg-white p-1.5 shadow-xl transition-all"
              >
                <div *ngIf="productSearchQuery()" class="px-3 py-1.5 text-[11px] font-semibold text-muted-ink border-b border-gray-100 mb-1 flex items-center justify-between">
                  <span>Searching: "{{ productSearchQuery() }}"</span>
                  <span>{{ filteredProducts().length }} match{{ filteredProducts().length === 1 ? '' : 'es' }}</span>
                </div>

                <div *ngIf="filteredProducts().length === 0" class="py-8 px-4 text-center text-xs text-muted-ink">
                  <p class="font-medium text-ink mb-1">No products found</p>
                  <p>Try searching with another product name or SKU.</p>
                </div>

                <button
                  type="button"
                  *ngFor="let p of filteredProducts()"
                  (click)="selectProduct(p)"
                  class="flex w-full items-center justify-between gap-3 rounded-xl px-3.5 py-2.5 text-left text-sm transition-colors hover:bg-gray-50 focus:bg-gray-50 focus:outline-none group"
                  [ngClass]="p.sku === sku ? 'bg-brand/10 font-bold text-brand' : 'text-ink'"
                >
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2">
                      <span class="truncate font-medium text-ink group-hover:text-brand transition-colors">{{ p.name }}</span>
                      <span class="rounded bg-gray-100 px-1.5 py-0.5 text-[11px] font-mono font-normal text-muted-ink shrink-0">{{ p.sku }}</span>
                    </div>
                    <span class="text-[11px] text-muted-ink">{{ p.category || 'Product' }}</span>
                  </div>
                  <div class="shrink-0 flex items-center gap-2">
                    <span
                      class="rounded-full px-2 py-0.5 text-[11px] font-semibold"
                      [ngClass]="(p.stock || 0) > 0 ? 'bg-emerald-50 text-emerald-700' : 'bg-gray-100 text-gray-500'"
                    >
                      {{ p.stock || 0 }} on hand
                    </span>
                    <svg *ngIf="p.sku === sku" lucideCheck class="h-4 w-4 text-brand"></svg>
                  </div>
                </button>
              </div>
            </div>
          </div>

          <div>
            <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Quantity received *</label>
            <input type="number" [(ngModel)]="qty" name="qty" required min="1" class="w-full rounded-full border border-gray-200 bg-white px-5 py-3 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
          </div>

          <!-- Supplier Dropdown Field -->
          <div>
            <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Supplier</label>
            <select [(ngModel)]="supplier" (ngModelChange)="onSupplierChange($event)" name="supplier" class="w-full rounded-full border border-gray-200 bg-white px-5 py-3 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              <option *ngFor="let s of suppliers" [value]="s">{{ s }}</option>
            </select>
          </div>

          <!-- Custom / Other Supplier Field (Appears when "Other" is selected) -->
          <div *ngIf="supplier === 'Other'" class="sm:col-span-2 rounded-2xl border border-dashed border-brand/40 bg-brand/5 p-4 transition-all">
            <div class="flex items-center justify-between mb-1.5">
              <label class="block text-xs font-bold uppercase tracking-widest text-brand">
                New Supplier Name *
              </label>
              <span class="text-[11px] text-muted-ink">Type new supplier name — will be stored for future stock entries</span>
            </div>
            <div class="flex flex-col sm:flex-row gap-2">
              <input 
                type="text" 
                [(ngModel)]="customSupplier" 
                name="customSupplier" 
                placeholder="Enter supplier name (e.g. Lagos Wholesale Hub, Shenzhen Direct)..." 
                class="flex-1 rounded-full border border-gray-200 bg-white px-5 py-2.5 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20"
                (keydown.enter)="$event.preventDefault(); saveCustomSupplierDirectly()"
              />
              <button 
                type="button" 
                (click)="saveCustomSupplierDirectly()" 
                [disabled]="!customSupplier.trim()"
                class="rounded-full bg-brand px-5 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-brand-600 disabled:opacity-40 transition-colors shrink-0"
              >
                Save & Select Supplier
              </button>
            </div>
            <p *ngIf="supplierSaveSuccess()" class="mt-2 text-xs font-semibold text-emerald-700 flex items-center gap-1.5">
              <svg lucideCheck class="h-4 w-4"></svg>
              <span>Supplier "{{ lastSavedSupplier() }}" stored and selected!</span>
            </p>
          </div>

          <div>
            <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Invoice / reference number</label>
            <input type="text" [(ngModel)]="ref" name="ref" placeholder="e.g. INV-20451" class="w-full rounded-full border border-gray-200 bg-white px-5 py-3 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
          </div>
          <div>
            <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Date received</label>
            <input type="date" [(ngModel)]="date" name="date" class="w-full rounded-full border border-gray-200 bg-white px-5 py-3 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
          </div>
          <div>
            <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Notes</label>
            <input type="text" [(ngModel)]="notes" name="notes" placeholder="Optional notes..." class="w-full rounded-full border border-gray-200 bg-white px-5 py-3 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
          </div>
          <div class="sm:col-span-2 flex justify-end mt-2">
            <button type="submit" [disabled]="!sku || !qty || isSubmitting()" class="rounded-full bg-brand px-6 py-3 text-sm font-bold text-white hover:bg-brand-600 disabled:opacity-50 shadow-sm transition-colors">
              {{ isSubmitting() ? 'Receiving stock...' : 'Receive stock' }}
            </button>
          </div>
        </form>
      </div>

      <h2 class="mb-4 text-lg font-extrabold text-ink">Recent stock received</h2>
      <div class="rounded-[20px] border border-gray-100 bg-white shadow-sm overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full min-w-[720px] text-left text-sm text-ink">
            <thead class="border-b border-gray-100 bg-white text-[10px] font-bold uppercase tracking-widest text-muted-ink">
              <tr>
                <th class="px-6 py-5">Date</th>
                <th class="px-6 py-5">Product</th>
                <th class="px-6 py-5">Supplier</th>
                <th class="px-6 py-5">Reference</th>
                <th class="px-6 py-5 text-right">Quantity</th>
              </tr>
            </thead>
            <tbody class="divide-y border-gray-100">
              <tr *ngFor="let m of receivedLogs()" class="transition-colors hover:bg-gray-50/50">
                <td class="px-6 py-4 whitespace-nowrap text-muted-ink">{{ m.date | date:'mediumDate' }}</td>
                <td class="px-6 py-4 font-semibold">{{ m.product }}<br><span class="text-xs font-normal text-muted-ink">{{ m.sku }}</span></td>
                <td class="px-6 py-4">{{ m.supplier || '—' }}</td>
                <td class="px-6 py-4 text-muted-ink">{{ m.ref || '—' }}</td>
                <td class="px-6 py-4 text-right">
                  <lib-badge tone="success">+{{ m.qty || m.change }}</lib-badge>
                </td>
              </tr>
              <tr *ngIf="receivedLogs().length === 0">
                <td colspan="5" class="p-12 text-center text-muted-ink">No stock has been received yet.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </ng-container>

    <ng-container *ngIf="!isStockIn">
      <lib-admin-heading title="Stock Adjustment" subtitle="Correct stock after physical counts, damages or losses"></lib-admin-heading>

      <!-- Feedback alerts -->
      <div *ngIf="successMessage()" class="mb-6 rounded-[16px] border border-emerald-200 bg-emerald-50 px-5 py-4 text-sm font-semibold text-emerald-800">
        {{ successMessage() }}
      </div>
      <div *ngIf="errorMessage()" class="mb-6 rounded-[16px] border border-rose-200 bg-rose-50 px-5 py-4 text-sm font-semibold text-rose-800">
        {{ errorMessage() }}
      </div>
      
      <div class="rounded-[20px] border border-gray-100 bg-white p-8 shadow-sm mb-8">
        <label class="mb-4 block text-sm font-semibold">Warehouse
          <select [(ngModel)]="locationId" name="warehouse" class="mt-1 w-full rounded-lg border p-2">
            <option value="">Choose warehouse</option>
            <option *ngFor="let location of admin.stockLocations()" [value]="location.id">{{location.name}} ({{location.code}})</option>
          </select>
        </label>
        
        <form (ngSubmit)="saveAdjustment()" class="grid gap-6 sm:grid-cols-2">
          <!-- Searchable Product Selection Field -->
          <div class="relative">
            <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Product *</label>
            <div class="relative">
              <div class="relative flex items-center">
                <svg lucideSearch class="absolute left-4 h-4 w-4 text-gray-400 pointer-events-none z-10"></svg>
                <input
                  type="text"
                  [(ngModel)]="productDisplay"
                  (ngModelChange)="onProductSearchInput($event)"
                  (focus)="openProductDropdown()"
                  (click)="openProductDropdown()"
                  name="productSearchAdjustment"
                  placeholder="Search product by name or SKU..."
                  autocomplete="off"
                  class="w-full rounded-full border border-gray-200 bg-white pl-11 pr-12 py-3 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20 font-medium cursor-pointer"
                  [class.pr-10]="!sku"
                />
                
                <button
                  *ngIf="sku"
                  type="button"
                  (click)="clearSelectedProduct($event)"
                  title="Clear product selection"
                  class="absolute right-3.5 z-10 grid h-6 w-6 place-items-center rounded-full text-gray-400 hover:bg-gray-100 hover:text-ink transition-colors"
                >
                  <svg lucideX class="h-3.5 w-3.5"></svg>
                </button>
                <button
                  *ngIf="!sku"
                  type="button"
                  (click)="toggleProductDropdown($event)"
                  class="absolute right-3.5 z-10 grid h-6 w-6 place-items-center text-gray-400"
                >
                  <svg lucideChevronDown class="h-4 w-4"></svg>
                </button>
              </div>

              <!-- Invisible backdrop to close dropdown on outside click -->
              <div
                *ngIf="productDropdownOpen()"
                (click)="closeProductDropdown()"
                class="fixed inset-0 z-20"
              ></div>

              <!-- Search Results Dropdown List -->
              <div
                *ngIf="productDropdownOpen()"
                class="absolute left-0 right-0 top-full z-30 mt-1.5 max-h-64 overflow-y-auto rounded-2xl border border-gray-200 bg-white p-1.5 shadow-xl transition-all"
              >
                <div *ngIf="productSearchQuery()" class="px-3 py-1.5 text-[11px] font-semibold text-muted-ink border-b border-gray-100 mb-1 flex items-center justify-between">
                  <span>Searching: "{{ productSearchQuery() }}"</span>
                  <span>{{ filteredProducts().length }} match{{ filteredProducts().length === 1 ? '' : 'es' }}</span>
                </div>

                <div *ngIf="filteredProducts().length === 0" class="py-8 px-4 text-center text-xs text-muted-ink">
                  <p class="font-medium text-ink mb-1">No products found</p>
                  <p>Try searching with another product name or SKU.</p>
                </div>

                <button
                  type="button"
                  *ngFor="let p of filteredProducts()"
                  (click)="selectProduct(p)"
                  class="flex w-full items-center justify-between gap-3 rounded-xl px-3.5 py-2.5 text-left text-sm transition-colors hover:bg-gray-50 focus:bg-gray-50 focus:outline-none group"
                  [ngClass]="p.sku === sku ? 'bg-brand/10 font-bold text-brand' : 'text-ink'"
                >
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2">
                      <span class="truncate font-medium text-ink group-hover:text-brand transition-colors">{{ p.name }}</span>
                      <span class="rounded bg-gray-100 px-1.5 py-0.5 text-[11px] font-mono font-normal text-muted-ink shrink-0">{{ p.sku }}</span>
                    </div>
                    <span class="text-[11px] text-muted-ink">{{ p.category || 'Product' }}</span>
                  </div>
                  <div class="shrink-0 flex items-center gap-2">
                    <span
                      class="rounded-full px-2 py-0.5 text-[11px] font-semibold"
                      [ngClass]="(p.stock || 0) > 0 ? 'bg-emerald-50 text-emerald-700' : 'bg-gray-100 text-gray-500'"
                    >
                      {{ p.stock || 0 }} on hand
                    </span>
                    <svg *ngIf="p.sku === sku" lucideCheck class="h-4 w-4 text-brand"></svg>
                  </div>
                </button>
              </div>
            </div>
          </div>

          <div>
            <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Current stock</label>
            <input type="text" [value]="currentStock !== null ? currentStock : '—'" disabled class="w-full rounded-full border border-gray-200 bg-gray-50 px-5 py-3 text-sm text-gray-500 shadow-sm focus:outline-none">
          </div>
          <div>
            <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Adjustment type</label>
            <select [(ngModel)]="increase" name="increase" class="w-full rounded-full border border-gray-200 bg-white px-5 py-3 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              <option [ngValue]="false">Decrease</option>
              <option [ngValue]="true">Increase</option>
            </select>
          </div>
          <div>
            <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Quantity *</label>
            <input type="number" [(ngModel)]="qty" name="qty" required min="1" class="w-full rounded-full border border-gray-200 bg-white px-5 py-3 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
          </div>
          <div>
            <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Reason *</label>
            <select [(ngModel)]="reason" name="reason" class="w-full rounded-full border border-gray-200 bg-white px-5 py-3 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              <option *ngFor="let r of reasons" [value]="r">{{ r }}</option>
            </select>
          </div>
          <div>
            <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Notes</label>
            <input type="text" [(ngModel)]="notes" name="notes" placeholder="Optional notes..." class="w-full rounded-full border border-gray-200 bg-white px-5 py-3 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
          </div>
          <div class="sm:col-span-2">
            <p *ngIf="currentStock !== null" class="mt-2 text-xs font-semibold text-muted-ink">
              New stock after adjustment: {{ getProjectedStock() }}
            </p>
            <div class="flex justify-end mt-2">
              <button type="submit" [disabled]="!sku || !qty || isSubmitting()" class="rounded-full bg-brand px-6 py-3 text-sm font-bold text-white hover:bg-brand-600 disabled:opacity-50 shadow-sm transition-colors">
                {{ isSubmitting() ? 'Applying adjustment...' : 'Apply adjustment' }}
              </button>
            </div>
          </div>
        </form>
      </div>

      <h2 class="mb-4 text-lg font-extrabold text-ink">Recent adjustments</h2>
      <div class="rounded-[20px] border border-gray-100 bg-white shadow-sm overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full min-w-[720px] text-left text-sm text-ink">
            <thead class="border-b border-gray-100 bg-white text-[10px] font-bold uppercase tracking-widest text-muted-ink">
              <tr>
                <th class="px-6 py-5">Date</th>
                <th class="px-6 py-5">Product</th>
                <th class="px-6 py-5">Reason</th>
                <th class="px-6 py-5">Notes</th>
                <th class="px-6 py-5">By</th>
                <th class="px-6 py-5 text-right">Quantity</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              <tr *ngFor="let m of adjustmentLogs()" class="transition-colors hover:bg-gray-50/50">
                <td class="px-6 py-4 whitespace-nowrap text-muted-ink">{{ m.date | date:'mediumDate' }}</td>
                <td class="px-6 py-4 font-semibold">{{ m.product }}<br><span class="text-xs font-normal text-muted-ink">{{ m.sku }}</span></td>
                <td class="px-6 py-4">{{ m.reason || '—' }}</td>
                <td class="px-6 py-4 text-muted-ink">{{ m.notes || '—' }}</td>
                <td class="px-6 py-4 text-muted-ink">{{ m.by || 'Administrator' }}</td>
                <td class="px-6 py-4 text-right">
                  <lib-badge [tone]="(m.qty || m.change) >= 0 ? 'success' : 'danger'">
                    {{ (m.qty || m.change) > 0 ? '+' : '' }}{{ m.qty || m.change }}
                  </lib-badge>
                </td>
              </tr>
              <tr *ngIf="adjustmentLogs().length === 0">
                <td colspan="6" class="p-12 text-center text-muted-ink">No stock adjustments recorded yet.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </ng-container>
  `
})
export class StockAdjustmentComponent implements OnInit {
  admin = inject(AdminService);
  route = inject(ActivatedRoute);

  isStockIn = false;
  isSubmitting = signal(false);
  successMessage = signal('');
  errorMessage = signal('');

  // Form Fields
  sku = '';
  locationId = '';
  qty = 1;
  notes = '';

  // Product Search & Selection State
  productDisplay = '';
  selectedProduct: any = null;
  productSearchQuery = signal('');
  productDropdownOpen = signal(false);

  // Stock In specific
  suppliers: string[] = ['Nigson HQ', 'Shenzhen Factory', 'Local Distributor', 'Other'];
  supplier = 'Nigson HQ';
  customSupplier = '';
  supplierSaveSuccess = signal(false);
  lastSavedSupplier = signal('');
  ref = '';
  date = new Date().toISOString().slice(0, 10);

  // Adjustment specific
  reasons = ['Physical Count', 'Damaged Goods', 'Lost/Stolen', 'Promotional Giveaway', 'Other'];
  reason = this.reasons[0];
  increase = false;
  currentStock: number | null = null;

  filteredProducts = computed(() => {
    const q = this.productSearchQuery().toLowerCase().trim();
    const list = this.admin.products();
    if (!q) return list;
    return list.filter(p =>
      (p.name && p.name.toLowerCase().includes(q)) ||
      (p.sku && p.sku.toLowerCase().includes(q)) ||
      (p.category && p.category.toLowerCase().includes(q)) ||
      (p.brand && p.brand.toLowerCase().includes(q))
    );
  });

  constructor() {
    this.route.data.subscribe(data => {
      this.isStockIn = data['type'] === 'stock-in';
    });
  }

  ngOnInit() {
    this.suppliers = this.loadSavedSuppliers();
    if (!this.suppliers.includes(this.supplier)) {
      this.supplier = this.suppliers[0];
    }
    this.admin.loadStockLocations().subscribe();
    this.admin.loadStaffProducts().subscribe(prods => {
      if (this.sku && (!this.selectedProduct || !this.productDisplay)) {
        const found = (prods || []).find(p => p.sku === this.sku);
        if (found) {
          this.selectedProduct = found;
          this.productDisplay = `${found.name} (${found.sku}) · ${found.stock ?? 0} on hand`;
          this.onSkuSelect();
        }
      }
    });
    this.admin.loadStockMovements().subscribe();
    this.admin.loadStaffInventory().subscribe();
  }

  onProductSearchInput(query: string) {
    this.productDisplay = query;
    this.productSearchQuery.set(query);
    this.productDropdownOpen.set(true);

    const clean = query.trim().toLowerCase();
    const exact = this.admin.products().find(p => p.sku.toLowerCase() === clean);
    if (exact) {
      this.sku = exact.sku;
      this.selectedProduct = exact;
      this.onSkuSelect();
    } else if (!clean) {
      this.sku = '';
      this.selectedProduct = null;
      this.onSkuSelect();
    }
  }

  openProductDropdown() {
    this.productDropdownOpen.set(true);
    this.productSearchQuery.set('');
  }

  closeProductDropdown() {
    this.productDropdownOpen.set(false);
    this.productSearchQuery.set('');
    if (this.selectedProduct) {
      this.productDisplay = `${this.selectedProduct.name} (${this.selectedProduct.sku}) · ${this.selectedProduct.stock ?? 0} on hand`;
    } else if (!this.sku) {
      this.productDisplay = '';
    }
  }

  toggleProductDropdown(event: Event) {
    event.stopPropagation();
    if (this.productDropdownOpen()) {
      this.closeProductDropdown();
    } else {
      this.openProductDropdown();
    }
  }

  selectProduct(p: any) {
    this.sku = p.sku;
    this.selectedProduct = p;
    this.productDisplay = `${p.name} (${p.sku}) · ${p.stock ?? 0} on hand`;
    this.productSearchQuery.set('');
    this.productDropdownOpen.set(false);
    this.onSkuSelect();
  }

  clearSelectedProduct(event: Event) {
    event.stopPropagation();
    this.sku = '';
    this.selectedProduct = null;
    this.productDisplay = '';
    this.productSearchQuery.set('');
    this.productDropdownOpen.set(true);
    this.onSkuSelect();
  }

  onSkuSelect() {
    if (!this.sku) {
      this.currentStock = null;
      return;
    }
    const p = this.selectedProduct || this.admin.products().find(x => x.sku === this.sku);
    this.currentStock = p ? p.stock : null;
  }

  getProjectedStock() {
    if (this.currentStock === null) return 0;
    return Math.max(0, this.currentStock + (this.increase ? this.qty : -this.qty));
  }

  onSupplierChange(val: string) {
    if (val === 'Other') {
      this.customSupplier = '';
      this.supplierSaveSuccess.set(false);
    }
  }

  saveCustomSupplierDirectly() {
    const clean = this.customSupplier.trim();
    if (!clean) return;
    this.saveNewSupplier(clean);
  }

  saveNewSupplier(name: string): string {
    const clean = name.trim();
    if (!clean || clean.toLowerCase() === 'other') return '';

    const existing = this.suppliers.find(s => s.toLowerCase() === clean.toLowerCase());
    if (existing) {
      this.supplier = existing;
      this.customSupplier = '';
      return existing;
    }

    const otherIdx = this.suppliers.indexOf('Other');
    if (otherIdx >= 0) {
      this.suppliers.splice(otherIdx, 0, clean);
    } else {
      this.suppliers.push(clean);
    }

    this.persistSuppliers();
    this.supplier = clean;
    this.lastSavedSupplier.set(clean);
    this.customSupplier = '';
    this.supplierSaveSuccess.set(true);
    setTimeout(() => this.supplierSaveSuccess.set(false), 4000);
    return clean;
  }

  loadSavedSuppliers(): string[] {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        const raw = localStorage.getItem('nigson_inventory_suppliers');
        if (raw) {
          const parsed = JSON.parse(raw);
          if (Array.isArray(parsed) && parsed.length > 0) {
            const list = parsed.filter((s: any) => typeof s === 'string' && s.trim() && s !== 'Other');
            const unique = Array.from(new Set(list));
            return [...unique, 'Other'];
          }
        }
      }
    } catch { }
    return ['Nigson HQ', 'Shenzhen Factory', 'Local Distributor', 'Other'];
  }

  persistSuppliers() {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        localStorage.setItem('nigson_inventory_suppliers', JSON.stringify(this.suppliers));
      }
    } catch { }
  }

  saveStockIn() {
    if (!this.sku || !this.qty || this.isSubmitting()) return;

    let effectiveSupplier = this.supplier;
    if (this.supplier === 'Other') {
      if (!this.customSupplier.trim()) {
        this.errorMessage.set('Please enter a new supplier name or select an existing supplier.');
        setTimeout(() => this.errorMessage.set(''), 4000);
        return;
      }
      effectiveSupplier = this.saveNewSupplier(this.customSupplier.trim());
    }

    this.isSubmitting.set(true);
    this.successMessage.set('');
    this.errorMessage.set('');

    const targetSku = this.sku;
    const targetQty = Number(this.qty);

    this.admin.postStockAdjustment(targetSku, targetQty, 'Stock Received', {
      locationId: this.locationId,
      supplier: effectiveSupplier,
      ref: this.ref,
      notes: this.notes
    }).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.successMessage.set(`Successfully received ${targetQty} units for ${targetSku}.`);
        this.sku = '';
        this.selectedProduct = null;
        this.productDisplay = '';
        this.productSearchQuery.set('');
        this.productDropdownOpen.set(false);
        this.qty = 1;
        this.ref = '';
        this.notes = '';
        this.customSupplier = '';
        if (this.supplier === 'Other') {
          this.supplier = effectiveSupplier;
        }
        setTimeout(() => this.successMessage.set(''), 4500);
      },
      error: (err) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(err?.error?.detail || 'Failed to record stock in.');
        setTimeout(() => this.errorMessage.set(''), 5000);
      }
    });
  }

  saveAdjustment() {
    if (!this.sku || !this.qty || this.isSubmitting()) return;
    this.isSubmitting.set(true);
    this.successMessage.set('');
    this.errorMessage.set('');

    const targetSku = this.sku;
    const finalChange = this.increase ? Number(this.qty) : -Number(this.qty);

    this.admin.postStockAdjustment(targetSku, finalChange, this.reason, {
      locationId: this.locationId,
      notes: this.notes
    }).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.successMessage.set(`Successfully adjusted stock by ${finalChange > 0 ? '+' : ''}${finalChange} units.`);
        this.sku = '';
        this.selectedProduct = null;
        this.productDisplay = '';
        this.productSearchQuery.set('');
        this.productDropdownOpen.set(false);
        this.qty = 1;
        this.notes = '';
        this.currentStock = null;
        setTimeout(() => this.successMessage.set(''), 4500);
      },
      error: (err) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(err?.error?.detail || 'Failed to apply stock adjustment.');
        setTimeout(() => this.errorMessage.set(''), 5000);
      }
    });
  }

  receivedLogs() {
    return this.admin.stockLog().filter(l => l.reason === 'Stock Received' || (l.change > 0 && l.reason !== 'Physical Count'));
  }

  adjustmentLogs() {
    return this.admin.stockLog().filter(l => l.reason !== 'Stock Received');
  }
}
