import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { 
  LucideTrash2, LucideShoppingBag, 
  LucideShieldCheck, LucideTruck, LucideArrowRight, LucideLock 
} from '@lucide/angular';
import { CartService, CatalogService, ShopService, SessionService, QtyStepperComponent } from 'shared-ui';

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [
    CommonModule, RouterLink, LucideTrash2, 
    LucideShoppingBag, LucideShieldCheck, LucideTruck, LucideArrowRight, LucideLock, QtyStepperComponent
  ],
  template: `
    <div class="min-h-[70vh] bg-background">
      <section class="pt-6 pb-16 md:pt-8 md:pb-20">
        <div class="mx-auto max-w-7xl px-6">
          <div class="mb-6 md:mb-8">
            <p class="text-[11px] font-semibold uppercase tracking-[0.25em] text-brand">Shopping</p>
            <h1 class="mt-2 text-4xl md:text-5xl font-extrabold tracking-[-0.02em]">Your Cart</h1>
          </div>

          <div *ngIf="!shop.hydrated()" class="rounded-2xl border border-hairline p-10 animate-pulse h-64 bg-surface-alt"></div>
          
          <div *ngIf="cart.pricingError()" role="alert" class="mb-4 rounded-xl border border-red-300 p-4">{{ cart.pricingError() }}</div>
          <div *ngIf="cart.reviewRequired()" role="alert" class="mb-4 rounded-xl border border-amber-300 p-4">Prices or minimum quantities have changed. Review the updated items before checkout. <button (click)="cart.acknowledgePrices()" class="ml-2 underline">I have reviewed these prices</button></div>
          <ng-container *ngIf="shop.hydrated()">
            <div *ngIf="cart.items().length === 0" class="rounded-3xl border border-hairline bg-surface-alt p-10 md:p-16 text-center">
              <div class="mx-auto grid h-16 w-16 place-items-center rounded-full bg-white shadow-elegant">
                <svg lucideShoppingBag class="h-8 w-8 text-brand"></svg>
              </div>
              <h2 class="mt-6 text-2xl md:text-3xl font-extrabold">Your cart is empty</h2>
              <p class="mt-3 text-sm md:text-base text-muted-ink max-w-md mx-auto">
                Browse our premium accessories and FMCG range and add products to build your order.
              </p>
              <a
                routerLink="/products"
                title="Explore products"
                aria-label="Explore products"
                class="mt-8 inline-flex items-center gap-2 rounded-full bg-brand text-white px-6 py-3 text-sm font-semibold hover:bg-brand-deep transition-colors"
              >
                Explore products <svg lucideArrowRight class="h-4 w-4"></svg>
              </a>
            </div>

            <div *ngIf="cart.items().length > 0" class="grid gap-10 lg:grid-cols-[1fr_400px]">
              <div class="space-y-4">
                <!-- Selection Header Bar -->
                <div class="flex items-center justify-between p-3.5 rounded-2xl bg-white border border-hairline shadow-sm">
                  <label class="flex items-center gap-2.5 cursor-pointer text-sm font-semibold text-ink select-none">
                    <input
                      type="checkbox"
                      [checked]="cart.isAllSelected()"
                      (change)="toggleSelectAll($event)"
                      class="h-4 w-4 rounded border-hairline text-brand focus:ring-brand accent-[#D62828] cursor-pointer"
                    />
                    <span>Select All ({{ cart.items().length }} items)</span>
                  </label>
                  <span class="text-xs font-semibold px-2.5 py-1 rounded-full bg-surface-alt text-muted-ink border border-hairline">
                    <span class="text-brand font-bold">{{ cart.selectedItems().length }}</span> of {{ cart.items().length }} selected
                  </span>
                </div>

                <article
                  *ngFor="let item of cart.items()"
                  class="flex items-start gap-3 md:gap-4 rounded-2xl border border-hairline bg-white p-4 md:p-5 transition-all shadow-sm"
                >
                  <!-- Selection Checkbox -->
                  <div class="pt-2">
                    <input
                      type="checkbox"
                      [checked]="cart.isSelected(item.lineId || item.skuId)"
                      (change)="cart.toggleSelected(item.lineId || item.skuId)"
                      class="h-4 w-4 rounded border-hairline text-brand focus:ring-brand accent-[#D62828] cursor-pointer"
                      [attr.aria-label]="'Select ' + item.name + ' for checkout'"
                    />
                  </div>

                  <!-- Product Thumbnail (Click to view details) -->
                  <a
                    [routerLink]="['/products', item.productId || item.sku || item.skuId]"
                    [attr.title]="'View ' + item.name + ' details'"
                    class="group/thumb h-24 w-24 md:h-28 md:w-28 shrink-0 rounded-xl overflow-hidden bg-surface-alt border border-hairline block cursor-pointer transition-opacity hover:opacity-90"
                  >
                    <img *ngIf="item.imageUrl" [src]="item.imageUrl" [alt]="item.name" class="h-full w-full object-cover transition-transform duration-300 group-hover/thumb:scale-105" />
                    <div *ngIf="!item.imageUrl" class="h-full w-full grid place-items-center text-muted-ink">
                      <svg lucideShoppingBag class="h-8 w-8"></svg>
                    </div>
                  </a>
                  <div class="flex-1 min-w-0 flex flex-col self-stretch">
                    <div class="flex flex-wrap items-start justify-between gap-2">
                      <div class="min-w-0">
                        <a
                          [routerLink]="['/products', item.productId || item.sku || item.skuId]"
                          class="text-[10px] font-semibold uppercase tracking-[0.2em] text-muted-ink hover:text-brand transition-colors block"
                        >
                          {{ item.sku }} • {{ item.categoryName }}
                        </a>
                        <a
                          [routerLink]="['/products', item.productId || item.sku || item.skuId]"
                          [attr.title]="'View ' + item.name + ' details'"
                          class="mt-1 block text-base md:text-lg font-semibold truncate text-ink hover:text-brand transition-colors cursor-pointer"
                        >
                          {{ item.name }}
                        </a>
                        <p class="mt-1 text-sm text-muted-ink">Unit price {{ item.unitPriceMinorUnits > 0 ? catalog.formatPrice(item.unitPriceMinorUnits) : 'Price from Commerce' }}</p>
                      </div>
                      <p class="text-lg font-bold text-[#D62828] whitespace-nowrap">
                        {{ item.unitPriceMinorUnits > 0 ? catalog.formatPrice(item.unitPriceMinorUnits * item.quantity) : 'Quote required' }}
                      </p>
                    </div>
                    <div class="mt-auto pt-4 flex items-center justify-between">
                      <p *ngIf="item.purchaseError" role="alert" class="mb-2 text-sm text-red-600">{{ item.purchaseError }}</p>
                      <ui-qty-stepper [product]="item" [image]="item.imageUrl"></ui-qty-stepper>
                      <button
                        (click)="removeItem(item.lineId)"
                        title="Remove item"
                        aria-label="Remove item"
                        class="inline-flex items-center gap-1.5 text-xs font-semibold text-muted-ink hover:text-brand transition-colors"
                      >
                        <svg lucideTrash2 class="h-3.5 w-3.5"></svg> Remove
                      </button>
                    </div>
                  </div>
                </article>
              </div>

              <aside class="lg:sticky lg:top-28 h-fit rounded-2xl border border-hairline bg-white p-6 md:p-7">
                <h2 class="text-lg font-bold">Order summary</h2>
                <dl class="mt-5 space-y-3 text-sm">
                  <div class="flex items-center justify-between">
                    <dt class="text-muted-ink">Selected Items</dt>
                    <dd class="font-semibold">{{ cart.selectedItems().length }} of {{ cart.items().length }}</dd>
                  </div>
                  <div class="flex items-center justify-between">
                    <dt class="text-muted-ink">Subtotal</dt>
                    <dd class="font-semibold">{{ cart.selectedSubtotalDisplay() }}</dd>
                  </div>
                  <div class="flex items-center justify-between">
                    <dt class="text-muted-ink">Shipping</dt>
                    <dd class="font-semibold">At checkout</dd>
                  </div>
                  <div class="flex items-center justify-between">
                    <dt class="text-muted-ink">Tax</dt>
                    <dd class="font-semibold">At checkout</dd>
                  </div>
                  
                  <div class="h-px bg-hairline my-2"></div>
                  
                  <div class="flex items-center justify-between">
                    <dt class="text-base font-bold">Estimated total</dt>
                    <dd class="text-lg font-extrabold text-[#D62828]">{{ cart.selectedSubtotalDisplay() }}</dd>
                  </div>
                </dl>
                <p class="mt-3 text-xs text-muted-ink">Shipping and tax will be added at checkout.</p>

                <div *ngIf="cart.selectedItems().length === 0" class="mt-4 rounded-xl border border-amber-200 bg-amber-50/80 p-3 text-xs text-amber-800">
                  Please select at least one item above to proceed to checkout.
                </div>

                <div *ngIf="!session.authenticated()" class="mt-4 rounded-xl border border-amber-200 bg-amber-50/80 p-3 text-xs text-amber-800 flex items-center gap-2">
                  <svg lucideLock class="h-4 w-4 text-amber-700 shrink-0"></svg>
                  <span>Sign in is required at checkout to bind warranty and complete your order.</span>
                </div>

                <button
                  (click)="proceedToCheckout()"
                  [disabled]="cart.selectedItems().length === 0 || !cart.readyForCheckout()"
                  title="Proceed to Checkout"
                  aria-label="Proceed to Checkout"
                  class="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-full bg-brand text-white py-3.5 text-sm font-semibold hover:bg-brand-deep disabled:opacity-50 disabled:cursor-not-allowed transition-colors cursor-pointer"
                >
                  <ng-container *ngIf="session.authenticated()">Proceed to Checkout ({{ cart.selectedItems().length }}) <svg lucideArrowRight class="h-4 w-4"></svg></ng-container>
                  <ng-container *ngIf="!session.authenticated()"><svg lucideLock class="h-4 w-4"></svg> Sign in to Checkout</ng-container>
                </button>
                
                <div class="mt-5 grid grid-cols-2 gap-3 text-[11px] text-muted-ink">
                  <div class="flex items-center gap-1.5"><svg lucideShieldCheck class="h-3.5 w-3.5 text-emerald-600"></svg> Secure checkout</div>
                  <div class="flex items-center gap-1.5"><svg lucideTruck class="h-3.5 w-3.5 text-emerald-600"></svg> Nationwide delivery</div>
                </div>
                
                <a routerLink="/products" title="Continue shopping" class="mt-5 block text-center text-xs font-semibold text-brand hover:underline">
                  Continue shopping →
                </a>
              </aside>
            </div>
          </ng-container>
        </div>
      </section>
    </div>
  `
})
export class CartComponent implements OnInit {
  ngOnInit(): void { this.cart.loadCart().subscribe(); }
  shop = inject(ShopService);
  cart = inject(CartService);
  catalog = inject(CatalogService);
  router = inject(Router);
  session = inject(SessionService);

  toggleSelectAll(e: Event) {
    const checked = (e.target as HTMLInputElement).checked;
    this.cart.selectAll(checked);
  }

  updateQty(lineId: string, qty: number) {
    this.cart.updateQty(lineId, qty).subscribe();
  }

  removeItem(lineId: string) {
    this.cart.removeItem(lineId).subscribe();
  }

  saveForLater(lineId: string) {
    this.cart.saveForLater(lineId).subscribe();
  }

  proceedToCheckout() {
    if(!this.cart.readyForCheckout())return;
    this.router.navigate(["/checkout"]);
  }
}
