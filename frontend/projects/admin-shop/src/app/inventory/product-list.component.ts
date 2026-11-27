import { finalize } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, AdminHeadingComponent } from 'shared-ui';
import { LucidePlus, LucidePencil, LucideTrash2, LucideSearch, LucideEye } from '@lucide/angular';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AdminHeadingComponent, LucidePlus, LucidePencil, LucideTrash2, LucideSearch, LucideEye],
  template: `
    <lib-admin-heading title="Products" [subtitle]="admin.products().length + ' products tracked in inventory'">
      <a *ngIf="admin.session.hasRole('ADMIN')" routerLink="/admin/inventory/products/import" class="rounded-full border border-hairline px-4 py-2 text-sm font-semibold">Import CSV</a>
      <button type="button" (click)="refresh()" [disabled]="refreshing()" class="rounded-full border border-hairline px-4 py-2 text-sm font-semibold disabled:opacity-50">{{refreshing() ? 'Refreshing...' : 'Refresh'}}</button>
      <a routerLink="new" class="flex h-10 items-center gap-2 rounded-full bg-brand px-5 text-sm font-bold text-white transition-colors hover:bg-brand-600 shadow-sm">
        <svg lucidePlus class="h-4 w-4"></svg> Add product
      </a>
    </lib-admin-heading>
    
    <div class="mb-6 flex flex-wrap items-center gap-4">
      <div class="relative w-[300px]">
        <svg lucideSearch class="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"></svg>
        <input type="text" [(ngModel)]="searchQuery" placeholder="Search product or SKU..." class="w-full rounded-full border border-gray-200 bg-white py-2 pl-10 pr-4 text-sm text-ink shadow-sm outline-none transition-all focus:border-gray-300 focus:ring-4 focus:ring-gray-100">
      </div>
      
      <select [(ngModel)]="selectedCategory" class="rounded-full border border-gray-200 bg-white px-4 py-2 text-sm text-ink shadow-sm outline-none focus:border-gray-300 focus:ring-4 focus:ring-gray-100">
        <option *ngFor="let c of categories()" [value]="c">{{ c }}</option>
      </select>
      
      <select class="rounded-full border border-gray-200 bg-white px-4 py-2 text-sm text-ink shadow-sm outline-none focus:border-gray-300 focus:ring-4 focus:ring-gray-100">
        <option>All brands</option>
      </select>
      
      <select class="rounded-full border border-gray-200 bg-white px-4 py-2 text-sm text-ink shadow-sm outline-none focus:border-gray-300 focus:ring-4 focus:ring-gray-100">
        <option>All</option>
      </select>
    </div>
    
    <div class="rounded-[20px] border border-gray-100 bg-white shadow-sm overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[720px] text-left text-sm text-ink">
          <thead class="border-b border-gray-100 bg-white text-[10px] font-bold uppercase tracking-widest text-muted-ink">
            <tr>
              <th class="px-6 py-5">Product</th>
              <th class="px-6 py-5">SKU</th>
              <th class="px-6 py-5">Category</th>
              <th class="px-6 py-5">Price</th>
              <th *ngIf="admin.session.hasRole('OPERATIONS')" class="px-6 py-5">Available to sell</th>
              <th class="px-6 py-5">Status</th>
              <th class="px-6 py-5 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr *ngFor="let p of filteredProducts()" class="transition-colors hover:bg-gray-50/50 group">
              <td class="px-6 py-4">
                <div class="flex items-center gap-4">
                  <div class="h-10 w-10 shrink-0 overflow-hidden rounded-lg bg-gray-50 flex items-center justify-center border border-gray-100">
                    <img *ngIf="p.images?.length" [src]="p.images[0]" [alt]="p.name" class="h-full w-full object-cover">
                  </div>
                  <p class="font-bold text-ink">{{ p.name }}</p>
                </div>
              </td>
              <td class="px-6 py-4 text-ink font-medium">{{ p.sku }}</td>
              <td class="px-6 py-4 text-muted-ink">{{ p.category }}</td>
              <td class="px-6 py-4 font-bold text-ink">
                {{ p.price > 0 ? (p.price | currency:'NGN':'symbol-narrow':'1.0-0') : 'Not configured' }}
                <p *ngIf="p.priceSubtitle" class="mt-1 text-xs font-normal text-muted-ink">{{ p.priceSubtitle }}</p>
              </td>
              <td *ngIf="admin.session.hasRole('OPERATIONS')" class="px-6 py-4 font-bold text-ink">{{ (p.available ?? 0) | number }}<p class="mt-1 text-xs font-normal text-muted-ink">On hand: {{p.onHand ?? p.stock | number}} &middot; Reserved: {{p.reserved | number}} &middot; Allocated: {{p.allocated ?? 0 | number}}</p></td>
              <td class="px-6 py-4">
                <span 
                  [class]="!admin.session.hasRole('OPERATIONS') ? 'bg-gray-50 text-gray-600' : (p.available ?? 0) > 0 ? 'bg-emerald-50 text-emerald-600' : 'bg-red-50 text-red-600'"
                  class="inline-flex items-center rounded-full px-2.5 py-1 text-[11px] font-bold transition-colors"
                >
                  {{ admin.session.hasRole('OPERATIONS') ? ((p.available ?? 0) > 0 ? 'In Stock' : 'Out of Stock') : p.status }}
                </span>
              </td>
              <td class="px-6 py-4 text-right">
                <div class="flex items-center justify-end gap-1.5 transition-opacity">
                  <a [routerLink]="['/products', p.id || p.sku]" target="_blank" class="grid h-8 w-8 place-items-center rounded-full border border-gray-200 bg-white text-gray-500 hover:bg-gray-50 hover:text-gray-900 transition-all shadow-sm" title="View on store">
                    <svg lucideEye class="h-3.5 w-3.5"></svg>
                  </a>
                  <a [routerLink]="[p.id || p.sku, 'edit']" class="grid h-8 w-8 place-items-center rounded-full border border-gray-200 bg-white text-gray-500 hover:bg-gray-50 hover:text-gray-900 transition-all shadow-sm" title="Edit">
                    <svg lucidePencil class="h-3.5 w-3.5"></svg>
                  </a>
                  <button *ngIf="admin.session.hasRole('CONTENT_PUBLISHER')" (click)="deleteProduct(p)" class="grid h-8 w-8 place-items-center rounded-full border border-red-100 bg-red-50 text-red-500 hover:bg-red-100 hover:text-red-600 transition-all shadow-sm" title="Delete">
                    <svg lucideTrash2 class="h-3.5 w-3.5"></svg>
                  </button>
                </div>
              </td>
            </tr>
            <tr *ngIf="filteredProducts().length === 0">
              <td colspan="7" class="p-12 text-center text-muted-ink">
                <div class="flex flex-col items-center justify-center gap-3">
                  <div class="h-12 w-12 rounded-full bg-gray-50 flex items-center justify-center">
                    <svg lucideSearch class="h-5 w-5 text-gray-400"></svg>
                  </div>
                  <p class="font-medium">No products found.</p>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class ProductListComponent implements OnInit {
  ngOnInit() {
    this.refresh();
  }
  admin = inject(AdminService);
  private http = inject(HttpClient);
  readonly categoryNames = signal<string[]>([]);
  readonly refreshing = signal(false);
  refresh() {
    if (this.refreshing()) return;
    this.refreshing.set(true);
    this.http.get<{name:string}[]>('/api/v1/staff/categories').subscribe({next:rows=>this.categoryNames.set(rows.map(row=>row.name)),error:()=>this.categoryNames.set([])});
    this.admin.loadStaffProducts().pipe(finalize(() => this.refreshing.set(false))).subscribe({error: () => {}});
  }
  searchQuery = signal('');
  selectedCategory = signal('All categories');

  categories = computed(() => {
    const cats = new Set([...this.categoryNames(), ...this.admin.products().map(p => p.category).filter(Boolean)]);
    return ['All categories', ...Array.from(cats)];
  });

  filteredProducts = computed(() => {
    let list = this.admin.products().filter(p => p.status !== 'ARCHIVED');
    const q = this.searchQuery().toLowerCase();
    if (q) {
      list = list.filter(p => p.name.toLowerCase().includes(q) || p.sku.toLowerCase().includes(q));
    }
    const cat = this.selectedCategory();
    if (cat && cat !== 'All categories') {
      list = list.filter(p => p.category === cat);
    }
    return list;
  });

  deleteProduct(p: any) {
    if (!confirm(`Are you sure you want to delete "${p.name}"?`)) return;
    const target = p.id || p.sku;
    this.admin.deleteProduct(target).subscribe({
      next: () => {
        this.admin.loadStaffProducts().subscribe();
      }
    });
  }
}

