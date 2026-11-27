import { Component, computed, ElementRef, inject, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LucideShoppingCart, LucideStar, LucideMinus, LucidePlus, LucideChevronLeft, LucideChevronRight } from '@lucide/angular';
import { CatalogService, Product } from 'shared-ui';
import { ShopService } from 'shared-ui';

@Component({
  selector: 'app-best-sellers',
  standalone: true,
  imports: [CommonModule, RouterLink, LucideShoppingCart, LucideStar, LucideMinus, LucidePlus, LucideChevronLeft, LucideChevronRight],
  template: `
    <section class="bg-white py-14 md:py-16">
      <div class="container-page">
        <div class="mb-8 flex items-end justify-between gap-6 reveal-on-scroll">
          <div>
            <span class="inline-block border-l-2 border-brand bg-brand/10 px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.18em] text-brand">
              Customer Favourites
            </span>
            <h2 class="mt-4 text-3xl font-extrabold uppercase leading-none text-ink md:text-[40px]">
              Best Sellers
            </h2>
            <p class="mt-2 text-sm text-muted-foreground">The products everyone keeps coming back for.</p>
          </div>

          <div class="flex shrink-0 gap-2">
            <button
              type="button"
              aria-label="Previous best sellers"
              (click)="scroll(-1)"
              class="grid h-11 w-11 place-items-center rounded-full border border-hairline bg-white text-ink transition-colors hover:border-brand hover:bg-brand hover:text-white"
            >
              <svg lucideChevronLeft class="h-5 w-5"></svg>
            </button>
            <button
              type="button"
              aria-label="Next best sellers"
              (click)="scroll(1)"
              class="grid h-11 w-11 place-items-center rounded-full border border-hairline bg-white text-ink transition-colors hover:border-brand hover:bg-brand hover:text-white"
            >
              <svg lucideChevronRight class="h-5 w-5"></svg>
            </button>
          </div>
        </div>

        <div
          #carouselRef
          class="flex snap-x snap-mandatory gap-5 overflow-x-auto pb-3 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        >
          <article
            *ngFor="let p of bestSellers(); let i = index"
            class="reveal-on-scroll flex w-[82%] shrink-0 snap-start flex-col rounded-2xl border border-hairline bg-white p-4 transition-transform hover:-translate-y-1 sm:w-[47%] lg:w-[31%] xl:w-[calc((100%-5rem)/5)]"
            [style.transitionDelay]="(i < 4 ? i : 4) * 60 + 'ms'"
          >
            <a [routerLink]="['/products', p.sku]" class="block">
              <div class="relative aspect-square overflow-hidden rounded-xl bg-[#f1f1f1]">
                <img
                  [src]="catalog.imageFor(p.categoryName)"
                  [alt]="p.name"
                  loading="lazy"
                  class="absolute inset-0 h-full w-full object-cover"
                />
                <span class="absolute left-2 top-2 rounded-md bg-brand px-2 py-1 text-[11px] font-bold text-white">
                  -{{ discountFor(p.sku) }}%
                </span>
                <span class="absolute bottom-2 left-2 rounded-md bg-ink px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-white">
                  Best Seller
                </span>
              </div>

              <div class="mt-3 flex items-center gap-1.5 text-sm">
                <svg lucideStar class="h-4 w-4 fill-brand text-brand"></svg>
                <span class="font-semibold text-ink">{{ 4.5 }}</span>
                <span class="text-muted-foreground">({{ 0 }})</span>
              </div>

              <h3 class="mt-2 line-clamp-2 min-h-10 text-[15px] font-semibold leading-snug text-ink">
                {{ p.name }}
              </h3>

              <div class="mt-3 flex flex-wrap items-baseline gap-2">
                <span class="text-xl font-extrabold text-brand">{{ catalog.formatPrice(p.priceMinorUnits ?? 0) }}</span>
                <span class="text-sm text-muted-foreground line-through">{{ catalog.formatPrice(originalPrice(p)) }}</span>
              </div>
            </a>

            <div class="mt-auto pt-4">
              <button
                *ngIf="qtyFor(p.sku)() === 0"
                type="button"
                (click)="addToCart(p)"
                class="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-brand px-4 py-3 text-sm font-semibold text-white transition-colors hover:bg-brand-deep"
              >
                <svg lucideShoppingCart class="h-4 w-4"></svg> Add to Cart
              </button>

              <div *ngIf="qtyFor(p.sku)() > 0" class="inline-flex w-full items-center justify-between rounded-xl bg-brand px-2 py-1.5 text-white">
                <button
                  type="button"
                  aria-label="Decrease quantity"
                  (click)="shop.updateQty(p.sku, qtyFor(p.sku)() - 1)"
                  class="grid h-9 w-9 place-items-center rounded-lg transition-colors hover:bg-brand-deep"
                >
                  <svg lucideMinus class="h-4 w-4"></svg>
                </button>
                <span class="text-sm font-bold tabular-nums">{{ qtyFor(p.sku)() }}</span>
                <button
                  type="button"
                  aria-label="Increase quantity"
                  (click)="shop.updateQty(p.sku, qtyFor(p.sku)() + 1)"
                  class="grid h-9 w-9 place-items-center rounded-lg transition-colors hover:bg-brand-deep"
                >
                  <svg lucidePlus class="h-4 w-4"></svg>
                </button>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>
  `
})
export class BestSellersComponent {
  catalog = inject(CatalogService);
  shop = inject(ShopService);

  @ViewChild('carouselRef') carouselRef?: ElementRef<HTMLDivElement>;

  bestSellers = computed(() => {
    return [...this.catalog.products()]
      .sort((a, b) => {
        const aRating = { average: 4.5, count: 0 };
        const bRating = { average: 4.5, count: 0 };
        return bRating.count - aRating.count || bRating.average - aRating.average;
      })
      .slice(0, 10);
  });

  discountFor(sku: string): number {
    let hash = 0;
    for (let i = 0; i < sku.length; i++) hash = (hash * 31 + sku.charCodeAt(i)) % 997;
    return 20 + (hash % 16);
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

  scroll(direction: number) {
    const carousel = this.carouselRef?.nativeElement;
    if (!carousel) return;
    carousel.scrollBy({ left: direction * carousel.clientWidth * 0.8, behavior: 'smooth' });
  }
}
