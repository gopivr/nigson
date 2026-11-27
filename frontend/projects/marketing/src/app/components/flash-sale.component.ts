import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LucideShoppingCart, LucideStar, LucideMinus, LucidePlus } from '@lucide/angular';
import { CatalogService, Product } from 'shared-ui';
import { ShopService } from 'shared-ui';

@Component({
  selector: 'app-flash-sale',
  standalone: true,
  imports: [CommonModule, RouterLink, LucideShoppingCart, LucideStar, LucideMinus, LucidePlus],
  template: `
    <section class="bg-ink py-14 md:py-16">
      <div class="container-page">
        <div class="reveal-on-scroll">
          <span class="inline-block border-l-2 border-brand bg-brand/15 px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.18em] text-white">
            Ends Soon
          </span>
          <h2 class="mt-4 text-3xl md:text-[40px] font-extrabold uppercase leading-none tracking-[-0.02em] text-white">
            Flash Sale
          </h2>
          <p class="mt-2 text-sm text-white/60">Limited-time deals. Don't miss out.</p>
        </div>

        <!-- Horizontal scroll on mobile, grid on desktop -->
        <div class="mt-8 flex overflow-x-auto snap-x snap-mandatory gap-5 pb-6 sm:grid sm:grid-cols-2 lg:grid-cols-4 sm:pb-0" style="scrollbar-width: none;">
          <div
            *ngFor="let p of flashProducts(); let i = index"
            class="reveal-on-scroll flex flex-col rounded-2xl bg-white p-4 transition-all hover:-translate-y-1 hover:shadow-md shrink-0 w-[260px] sm:w-auto snap-start"
            [style.transitionDelay]="i * 60 + 'ms'"
          >
            <a [routerLink]="['/products', p.sku]" class="block">
              <div class="relative aspect-square overflow-hidden rounded-xl bg-[#f1f1f1]">
                <img
                  [src]="catalog.imageFor(p.categoryName)"
                  [alt]="p.name"
                  loading="lazy"
                  class="absolute inset-0 h-full w-full object-cover"
                />
                <span class="absolute left-2 top-2 rounded-md bg-brand px-2 py-1 text-[11px] font-bold text-white shadow-sm">
                  -{{ discountFor(p.sku) }}%
                </span>
                <span *ngIf="i % 2 === 0" class="absolute bottom-2 left-2 rounded-md bg-ink px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-white shadow-sm">
                  Best Seller
                </span>
              </div>

              <div class="mt-4 flex items-center gap-1.5 text-sm">
                <svg lucideStar class="h-4 w-4 fill-[#F5A623] text-[#F5A623]"></svg>
                <span class="font-bold text-ink">{{ 4.5 }}</span>
                <span class="text-muted-foreground font-medium">({{ 0 }})</span>
              </div>

              <h3 class="mt-2 text-[15px] font-bold leading-snug text-ink line-clamp-2">{{ p.name }}</h3>

              <div class="mt-3 flex items-baseline gap-2">
                <span class="text-xl font-extrabold text-brand">{{ catalog.formatPrice(p.priceMinorUnits ?? 0) }}</span>
                <span class="text-sm font-medium text-muted-foreground line-through">{{ catalog.formatPrice(originalPrice(p)) }}</span>
              </div>
            </a>

            <div class="mt-5 mt-auto">
              <button
                *ngIf="qtyFor(p.sku)() === 0"
                type="button"
                (click)="addToCart(p)"
                class="w-full inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-ink text-white text-sm font-bold hover:bg-brand transition-colors"
              >
                <svg lucideShoppingCart class="h-4 w-4"></svg> Add to Cart
              </button>

              <div *ngIf="qtyFor(p.sku)() > 0" class="w-full inline-flex h-11 items-center justify-between rounded-xl bg-brand text-white px-2">
                <button
                  type="button"
                  aria-label="Decrease quantity"
                  (click)="shop.updateQty(p.sku, qtyFor(p.sku)() - 1)"
                  class="grid h-8 w-8 place-items-center rounded-lg hover:bg-brand-deep transition-colors"
                >
                  <svg lucideMinus class="h-4 w-4"></svg>
                </button>
                <span class="text-sm font-bold tabular-nums">{{ qtyFor(p.sku)() }}</span>
                <button
                  type="button"
                  aria-label="Increase quantity"
                  (click)="shop.updateQty(p.sku, qtyFor(p.sku)() + 1)"
                  class="grid h-8 w-8 place-items-center rounded-lg hover:bg-brand-deep transition-colors"
                >
                  <svg lucidePlus class="h-4 w-4"></svg>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  `
})
export class FlashSaleComponent {
  catalog = inject(CatalogService);
  shop = inject(ShopService);

  flashProducts = computed(() => {
    return this.catalog.products().filter(p => this.catalog.isDeal(p)).slice(0, 4);
  });

  discountFor(sku: string): number {
    let h = 0;
    for (let i = 0; i < sku.length; i++) h = (h * 31 + sku.charCodeAt(i)) % 997;
    return 20 + (h % 16);
  }

  originalPrice(p: Product): number {
    const off = this.discountFor(p.sku);
    return Math.round(((p.priceMinorUnits ?? 0) / 100 / (1 - off / 100)) / 100) * 100;
  }

  qtyFor(sku: string) {
    return computed(() => {
      return this.shop.cart().find(c => c.sku === sku)?.qty ?? 0;
    });
  }

  addToCart(p: Product) {
    this.shop.addToCart({
      sku: p.sku,
      name: p.name,
      category: p.categoryName ?? "",
      price: (p.priceMinorUnits ?? 0) / 100,
      image: this.catalog.imageFor(p.categoryName)
    });
  }
}
