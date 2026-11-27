import { Component, inject, computed, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AdminService, AdminHeadingComponent, BadgeComponent } from 'shared-ui';

@Component({
  selector: 'app-stock-report',
  standalone: true,
  imports: [CommonModule, RouterLink, AdminHeadingComponent, BadgeComponent],
  template: `
    <lib-admin-heading [title]="isOutOfStock() ? 'Out of Stock' : 'Low Stock'" [subtitle]="isOutOfStock() ? 'Products that require immediate restocking.' : 'Products with 10 or fewer available units.'"></lib-admin-heading>

    <div class="rounded-2xl border border-hairline bg-white shadow-sm overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[720px] text-left text-sm text-ink">
          <thead class="border-b border-hairline bg-surface-alt/50 text-[10px] font-bold uppercase tracking-widest text-muted-ink">
            <tr>
              <th class="px-6 py-4">Product</th>
              <th class="px-6 py-4">Category</th>
              <th class="px-6 py-4">Price</th>
              <th class="px-6 py-4">Available to sell</th>
              <th class="px-6 py-4">Status</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-hairline">
            <tr *ngFor="let p of filteredProducts()" class="transition-colors hover:bg-surface-alt/30">
              <td class="px-6 py-4">
                <p class="font-bold"><a [routerLink]="admin.can('products') ? ['/admin/inventory/products', p.sku, 'edit'] : null" class="hover:text-brand hover:underline">{{ p.name }}</a></p>
                <p class="text-xs text-muted-ink">{{ p.sku }}</p>
              </td>
              <td class="px-6 py-4 text-muted-ink">{{ p.category }}</td>
              <td class="px-6 py-4 font-semibold">{{ p.price | currency:'NGN':'symbol-narrow':'1.0-0' }}</td>
              <td class="px-6 py-4 font-bold" [ngClass]="(p.available ?? 0) <= 0 ? 'text-rose-600' : 'text-amber-600'">{{ (p.available ?? 0) | number }}</td>
              <td class="px-6 py-4"><lib-badge [tone]="(p.available ?? 0) <= 0 ? 'danger' : 'warning'">{{ (p.available ?? 0) <= 0 ? 'Out of Stock' : 'Low Stock' }}</lib-badge></td>
            </tr>
            <tr *ngIf="filteredProducts().length <= 0">
              <td colspan="5" class="p-8 text-center text-muted-ink">No products found for this report.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class StockReportComponent implements OnInit {
  ngOnInit() { this.admin.loadStaffProducts().subscribe(); }
  admin = inject(AdminService);
  route = inject(ActivatedRoute);

  isOutOfStock = signal(false);

  constructor() {
    this.route.data.subscribe(data => {
      this.isOutOfStock.set(data['type'] === 'out-of-stock');
    });
  }

  filteredProducts = computed(() => {
    const list = this.admin.products();
    if (this.isOutOfStock()) {
      return list.filter(p => (p.available ?? 0) <= 0);
    }
    return list.filter(p => (p.available ?? 0) > 0 && (p.available ?? 0) <= 10);
  });
}

