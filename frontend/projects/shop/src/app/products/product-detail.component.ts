import { WishlistButtonComponent,StockStatusComponent } from 'shared-ui';
import {toObservable, takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {switchMap, map, catchError, of} from 'rxjs';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  LucideArrowLeft, LucideChevronLeft, LucideChevronRight,
  LucideZoomIn, LucideZoomOut, LucideRotateCcw, LucideStar,
  LucideFileText, LucideShieldCheck
} from '@lucide/angular';
import { CatalogService, ShopService, SessionService, ReviewService, QtyStepperComponent, relativeDate } from 'shared-ui';
import { ProductCardComponent } from './product-card.component';

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [WishlistButtonComponent,StockStatusComponent,
    CommonModule, RouterLink, LucideArrowLeft, LucideChevronLeft,
    LucideChevronRight, LucideZoomIn, LucideZoomOut, LucideRotateCcw,
    LucideStar, LucideFileText,
    QtyStepperComponent, ProductCardComponent, LucideShieldCheck
  ],
  template: `
    <div class="min-h-screen bg-background" *ngIf="product()">
      <section class="container-page pt-10">
        <button (click)="goBack()" title="Back to products" aria-label="Back to products" class="inline-flex items-center gap-2 text-sm font-semibold text-ink hover:text-brand transition-colors">
          <svg lucideArrowLeft class="h-4 w-4"></svg> Back
        </button>
      </section>

      <section class="container-page pb-16 pt-6">
        <div class="grid gap-10 md:grid-cols-2">
          
          <!-- Gallery -->
          <div>
            <div class="relative rounded-2xl bg-surface-alt overflow-hidden aspect-square border border-hairline">
              <div class="absolute inset-0 flex items-center justify-center overflow-hidden">
                <img
                  [src]="gallery()[index()]"
                  [alt]="product()?.name"
                  class="max-h-full max-w-full object-contain transition-all duration-300"
                  [ngClass]="fading() ? 'opacity-0 translate-x-3' : 'opacity-100 translate-x-0'"
                  [style.transform]="'scale(' + zoom() + ')'"
                />
              </div>

              <button
                (click)="go(index() - 1)"
                title="Previous image"
                aria-label="Previous image"
                class="absolute left-3 top-1/2 -translate-y-1/2 grid h-10 w-10 place-items-center rounded-full bg-white/95 backdrop-blur border border-hairline hover:bg-brand hover:text-white transition-colors"
              >
                <svg lucideChevronLeft class="h-5 w-5"></svg>
              </button>
              <button
                (click)="go(index() + 1)"
                title="Next image"
                aria-label="Next image"
                class="absolute right-3 top-1/2 -translate-y-1/2 grid h-10 w-10 place-items-center rounded-full bg-white/95 backdrop-blur border border-hairline hover:bg-brand hover:text-white transition-colors"
              >
                <svg lucideChevronRight class="h-5 w-5"></svg>
              </button>

              <span class="absolute top-3 left-3 rounded-full bg-white/95 backdrop-blur border border-hairline px-3 py-1 text-[11px] font-semibold tabular-nums">
                {{ index() + 1 }} / {{ gallery().length }}
              </span>

              <div class="absolute bottom-3 left-1/2 -translate-x-1/2 flex items-center gap-2 rounded-full bg-white/95 backdrop-blur border border-hairline shadow-lg px-2 py-1.5">
                <button (click)="setZoom(zoom() - 0.25)" title="Zoom out" aria-label="Zoom out" class="grid h-8 w-8 place-items-center rounded-full hover:bg-brand hover:text-white transition-colors">
                  <svg lucideZoomOut class="h-4 w-4"></svg>
                </button>
                <span class="min-w-[3rem] text-center text-xs font-semibold tabular-nums">{{ math.round(zoom() * 100) }}%</span>
                <button (click)="setZoom(zoom() + 0.25)" title="Zoom in" aria-label="Zoom in" class="grid h-8 w-8 place-items-center rounded-full hover:bg-brand hover:text-white transition-colors">
                  <svg lucideZoomIn class="h-4 w-4"></svg>
                </button>
                <span class="h-5 w-px bg-hairline mx-1"></span>
                <button (click)="setZoom(1)" title="Reset zoom" aria-label="Reset zoom" class="grid h-8 w-8 place-items-center rounded-full hover:bg-brand hover:text-white transition-colors">
                  <svg lucideRotateCcw class="h-4 w-4"></svg>
                </button>
              </div>
            </div>

            <!-- Thumbnails -->
            <div class="mt-4 flex gap-3 overflow-x-auto pb-1">
              <button
                *ngFor="let g of gallery(); let i = index"
                (click)="go(i)"
                [ngClass]="i === index() ? 'border-brand' : 'border-hairline hover:border-brand/50'"
                class="shrink-0 h-20 w-20 rounded-xl overflow-hidden border-2 transition-colors"
              >
                <img [src]="g" alt="" class="h-full w-full object-cover" />
              </button>
            </div>
          </div>

          <!-- Info -->
          <div>
            <p class="text-[10px] font-semibold uppercase tracking-[0.22em] text-brand">{{ product()?.categoryName }}</p>
            <h1 class="mt-3 text-3xl md:text-4xl font-extrabold leading-tight tracking-[-0.02em]">{{ product()?.name }}</h1>
            <p class="mt-2 text-xs uppercase tracking-widest text-muted-ink">SKU • {{ product()?.sku }}</p>

            <button class="mt-4 inline-flex items-center gap-3 rounded-full border border-hairline px-4 py-2 hover:border-brand transition-colors" (click)="scrollToReviews()">
              <svg lucideStar class="h-4 w-4 fill-brand text-brand"></svg>
              <span class="text-sm font-bold text-brand">{{ liveAverage() }} / 5</span>
              <span class="text-sm text-muted-foreground">{{ totalReviews() }} Reviews</span>
            </button>

            <div class="mt-6">
              <p class="text-[11px] uppercase tracking-widest text-muted-foreground">Starting From</p>
              <p class="mt-1 text-3xl md:text-4xl font-bold text-[#D62828] leading-tight">{{ commercePriceMinor() !== null ? catalog.formatPrice(displayPrice()) : 'Contact for price' }}</p>
              <p *ngIf="commercePriceMinor() !== null && purchaseMinimum() > 1" class="mt-2 text-sm font-semibold">Minimum order: {{ purchaseMinimum() }} units<span *ngIf="purchasePack() > 1">; multiples of {{ purchasePack() }}</span>.</p>
              <p class="mt-2 text-sm text-muted-foreground">{{ product()?.priceSubtitle || 'Wholesale pricing available for distributors.' }}</p>
            </div>

            <p class="mt-6 text-base text-ink/80 leading-relaxed">{{ product()?.description }}</p>

            <ul class="mt-6 space-y-2 text-sm text-ink/80">
              <li class="flex gap-3"><span class="text-brand">✓</span> Wholesale & retail pricing</li>
              <li class="flex gap-3"><span class="text-brand">✓</span> Nationwide delivery from Lagos hub</li>
              <li class="flex gap-3"><span class="text-brand">✓</span> 12-month standard warranty</li>
              <li class="flex gap-3"><span class="text-brand">✓</span> Bulk discounts for distributors</li>
              <li *ngFor="let feat of featuresList()" class="flex gap-3">
                <span class="text-brand">✓</span> {{ feat }}
              </li>
            </ul>

            <div class="mt-8 flex flex-wrap gap-3">
              <ui-wishlist-button [productId]="product()!.id" [skuId]="product()!.skuId"></ui-wishlist-button>
              <!-- Qty Stepper / Cart Control -->
              <ui-stock-status [status]="product()?.stockStatus"></ui-stock-status>
              <ui-qty-stepper *ngIf="productWithPrice()" [product]="productWithPrice()" [image]="gallery()[0]"></ui-qty-stepper>

              <a [routerLink]="['/quote']" title="Request a quote" aria-label="Request a quote" class="inline-flex items-center gap-2 rounded-full border border-ink text-ink px-6 py-3 text-sm font-semibold hover:bg-ink hover:text-white transition-colors">
                <svg lucideFileText class="h-4 w-4"></svg> Request quote
              </a>
            </div>
          </div>
        </div>

        <!-- Horizontal Tab Navigation Section -->
        <div class="mt-16 border-t border-hairline pt-10">
          <div class="border-b border-hairline">
            <nav class="flex gap-8 md:gap-12 overflow-x-auto" aria-label="Product detail tabs">
              <button
                *ngFor="let tab of tabs"
                (click)="activeTab.set(tab)"
                [ngClass]="activeTab() === tab ? 'text-brand' : 'text-muted-ink hover:text-ink'"
                class="relative pb-4 text-base md:text-lg font-bold transition-colors capitalize whitespace-nowrap"
              >
                {{ tab }}
                <span *ngIf="activeTab() === tab" class="absolute bottom-0 left-0 right-0 h-[3px] bg-brand rounded-full"></span>
              </button>
            </nav>
          </div>

          <!-- Tab Panels -->
          <div class="py-8">
            <div *ngIf="activeTab() === 'description'" class="space-y-4 max-w-4xl animate-fade-in text-ink/80 leading-relaxed">
              <p class="text-lg font-semibold text-ink">{{ product()?.name }} Overview</p>
              <p *ngIf="product()?.description">{{ product()?.description }}</p>
              <p *ngIf="product()?.overview">{{ product()?.overview }}</p>
              <p *ngIf="!product()?.overview && !product()?.description">
                Engineered to meet the highest standards of durability and performance across Nigerian markets.
                Suitable for both wholesale commercial resale and direct consumer use.
              </p>
              <div class="mt-6 rounded-2xl bg-surface-alt p-6 border border-hairline">
                <p class="font-bold text-ink mb-3">Key Highlights:</p>
                <ul class="grid sm:grid-cols-2 gap-2 text-sm text-ink/80">
                  <li *ngFor="let h of highlightsList()" class="flex items-center gap-2">
                    <span class="text-brand font-bold">✓</span> {{ h }}
                  </li>
                </ul>
              </div>
            </div>

            <div *ngIf="activeTab() === 'specifications'" class="max-w-3xl animate-fade-in">
              <div class="rounded-2xl border border-hairline overflow-hidden bg-white">
                <table class="w-full text-left text-sm">
                  <tbody class="divide-y divide-hairline">
                    <tr class="bg-surface-alt/50">
                      <td class="px-6 py-4 font-bold text-ink w-1/3">SKU Model</td>
                      <td class="px-6 py-4 text-ink/80 font-mono">{{ product()?.sku }}</td>
                    </tr>
                    <tr>
                      <td class="px-6 py-4 font-bold text-ink">Category</td>
                      <td class="px-6 py-4 text-ink/80">{{ product()?.categoryName }}</td>
                    </tr>
                    <tr class="bg-surface-alt/50" *ngIf="specValue('warranty')">
                      <td class="px-6 py-4 font-bold text-ink">Warranty</td>
                      <td class="px-6 py-4 text-ink/80">{{ specValue('warranty') }}</td>
                    </tr>
                    <tr *ngIf="specValue('build_material') || specValue('buildMaterial')">
                      <td class="px-6 py-4 font-bold text-ink">Build Material</td>
                      <td class="px-6 py-4 text-ink/80">{{ specValue('build_material') || specValue('buildMaterial') }}</td>
                    </tr>
                    <tr class="bg-surface-alt/50" *ngIf="specValue('connectors')">
                      <td class="px-6 py-4 font-bold text-ink">Connectors</td>
                      <td class="px-6 py-4 text-ink/80">{{ specValue('connectors') }}</td>
                    </tr>
                    <tr *ngIf="specValue('power_rating') || specValue('powerRating')">
                      <td class="px-6 py-4 font-bold text-ink">Power Rating</td>
                      <td class="px-6 py-4 text-ink/80">{{ specValue('power_rating') || specValue('powerRating') }}</td>
                    </tr>
                    <tr class="bg-surface-alt/50" *ngIf="specValue('compatibility')">
                      <td class="px-6 py-4 font-bold text-ink">Compatibility</td>
                      <td class="px-6 py-4 text-ink/80">{{ specValue('compatibility') }}</td>
                    </tr>
                    <tr *ngIf="specValue('certification')">
                      <td class="px-6 py-4 font-bold text-ink">Certification</td>
                      <td class="px-6 py-4 text-ink/80">{{ specValue('certification') }}</td>
                    </tr>
                    <tr class="bg-surface-alt/50" *ngIf="specValue('importer')">
                      <td class="px-6 py-4 font-bold text-ink">Importer & Distributor</td>
                      <td class="px-6 py-4 text-ink/80">{{ specValue('importer') }}</td>
                    </tr>
                    <tr *ngFor="let s of extraSpecs(); let even = even" [class.bg-surface-alt]="even">
                      <td class="px-6 py-4 font-bold text-ink">{{ s.key }}</td>
                      <td class="px-6 py-4 text-ink/80">{{ s.value }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <div *ngIf="activeTab() === 'packaging'" class="max-w-3xl animate-fade-in space-y-4 text-ink/80">
              <p class="text-base font-semibold text-ink">Packaging & Shipping Details</p>
              <div class="grid sm:grid-cols-2 gap-4">
                <div class="rounded-2xl border border-hairline p-5 bg-white">
                  <p class="text-xs uppercase font-bold text-brand tracking-widest">Retail Box Contents</p>
                  <ul class="mt-3 space-y-1.5 text-sm">
                    <li *ngFor="let item of packagingContentsList()">• {{ item }}</li>
                  </ul>
                </div>
                <div class="rounded-2xl border border-hairline p-5 bg-white">
                  <p class="text-xs uppercase font-bold text-brand tracking-widest">Master Carton Specs</p>
                  <ul class="mt-3 space-y-1.5 text-sm">
                    <li *ngFor="let spec of cartonSpecsList()">• {{ spec }}</li>
                  </ul>
                </div>
              </div>
            </div>

            <div *ngIf="activeTab() === 'availability'" class="max-w-3xl animate-fade-in space-y-4 text-ink/80">
              <p class="text-base font-semibold text-ink">Stock & Distribution Availability</p>
              <div class="rounded-2xl border border-hairline p-6 bg-white space-y-3">
                <div class="flex items-center justify-between border-b border-hairline pb-3">
                  <span class="text-sm font-bold text-ink">Central Lagos Warehouse Status</span>
                  <span class="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 text-emerald-700 px-3 py-1 text-xs font-bold">
                    <span class="h-2 w-2 rounded-full bg-emerald-600 animate-pulse"></span>
                    {{ product()?.warehouseStatus || 'In Stock & Ready to Ship' }}
                  </span>
                </div>
                <div class="flex items-center justify-between border-b border-hairline pb-3">
                  <span class="text-sm font-bold text-ink">Dispatch Turnaround</span>
                  <span class="text-sm text-ink/80 font-medium">{{ product()?.dispatchTurnaround || 'Dispatched within 24 Hours' }}</span>
                </div>
                <div class="flex items-center justify-between border-b border-hairline pb-3">
                  <span class="text-sm font-bold text-ink">Nationwide Shipping</span>
                  <span class="text-sm text-ink/80 font-medium">{{ product()?.nationwideShipping || '2 - 4 Business Days across Nigeria' }}</span>
                </div>
                <div class="flex items-center justify-between pt-1">
                  <span class="text-sm font-bold text-ink">Minimum Wholesale Order</span>
                  <span class="text-sm text-brand font-bold">{{ product()?.minWholesaleOrder || '10 Units' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Recommended products -->
      <section class="border-t border-hairline bg-surface-alt/40 py-14">
        <div class="container-page">
          <div class="flex flex-wrap items-end justify-between gap-4">
            <h2 class="text-2xl font-extrabold tracking-[-0.02em]">Recommended Products</h2>
            <a routerLink="/products" class="text-sm font-semibold text-ink hover:text-brand transition-colors">
              View all
            </a>
          </div>
          <div class="mt-8 grid gap-6 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
            <app-product-card *ngFor="let r of recommended()" [p]="r" [compact]="true"></app-product-card>
          </div>
        </div>
      </section>

      <!-- Reviews -->
      <section #reviewsRef id="reviews" class="border-t border-hairline bg-white py-14">
        <div class="container-page">
          <div class="flex flex-wrap items-end justify-between gap-4">
            <h2 class="text-2xl font-extrabold tracking-[-0.02em]">Reviews</h2>
            <div class="inline-flex items-center gap-2 rounded-full border border-hairline bg-surface-alt px-4 py-2 text-xs font-semibold text-muted-ink">
              <svg lucideShieldCheck class="h-4 w-4 text-brand"></svg>
              Verified Buyer Reviews
            </div>
          </div>

          <div class="mt-6 grid gap-8 md:grid-cols-[220px_1fr]">
            <div class="rounded-2xl border border-hairline bg-white p-6 text-center h-fit">
              <p class="text-4xl font-extrabold text-[#D62828]">{{ liveAverage() }}</p>
              <p class="text-xs text-muted-foreground">out of 5</p>
              <div class="mt-2 flex justify-center">
                <ng-container *ngFor="let i of [1,2,3,4,5]">
                  <svg lucideStar class="h-4 w-4 transition-colors" [ngClass]="i <= liveAverage() ? 'fill-brand text-brand' : 'text-hairline'"></svg>
                </ng-container>
              </div>
              <p class="mt-2 text-sm font-semibold">{{ totalReviews() }} Reviews</p>
            </div>

            <div class="space-y-2">
              <ng-container *ngFor="let s of [5, 4, 3, 2, 1]">
                <div class="flex items-center gap-3">
                  <span class="w-10 text-xs font-semibold tabular-nums">{{ s }} ★</span>
                  <div class="h-2 flex-1 rounded-full bg-hairline/60 overflow-hidden">
                    <div class="h-full rounded-full bg-brand transition-all duration-700" [style.width]="$any(rating()?.breakdown)?.[s] + '%'"></div>
                  </div>
                  <span class="w-10 text-right text-xs text-muted-foreground tabular-nums">{{ $any(rating()?.breakdown)?.[s] }}%</span>
                </div>
              </ng-container>
            </div>
          </div>

          <ul class="mt-8 space-y-4">
            <li *ngFor="let r of reviews()" class="rounded-2xl border border-hairline bg-white p-5">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <div class="flex items-center gap-3">
                  <span class="grid h-9 w-9 place-items-center rounded-full bg-brand/10 text-brand text-xs font-extrabold">
                    {{ (r.displayName || r.name || 'U').slice(0, 1).toUpperCase() }}
                  </span>
                  <div>
                    <p class="text-sm font-bold">{{ r.displayName || r.name || 'Verified Customer' }}</p>
                    <div class="flex gap-0.5">
                      <ng-container *ngFor="let i of [1,2,3,4,5]">
                        <svg lucideStar class="h-3 w-3" [ngClass]="i <= r.rating ? 'fill-brand text-brand' : 'text-hairline'"></svg>
                      </ng-container>
                    </div>
                  </div>
                </div>
                <span class="text-xs text-muted-foreground">{{ relativeDate(r.publishedAt || r.date) }}</span>
              </div>
              <p class="mt-3 text-sm text-ink/80 leading-relaxed">{{ r.body || r.text }}</p>
            </li>
          </ul>
        </div>
      </section>


    </div>
    
    <div class="min-h-[50vh] flex items-center justify-center" *ngIf="!product()">
      <p>Product not found.</p>
    </div>
  `
})
export class ProductDetailComponent implements OnInit {
  route = inject(ActivatedRoute);
  location = inject(Location);
  catalog = inject(CatalogService);
  shop = inject(ShopService);
  reviewSvc = inject(ReviewService);

  math = Math;

  /** Current product loaded from GET /api/v1/catalogue/products/:id (TASK-21) */
  readonly product = signal<import('shared-ui').Product | null>(null);
  readonly productLoading = signal(true);

  /** Recommendations from GET /api/v1/catalogue/products/:id/recommendations (TASK-22) */
  readonly recommended = signal<import('shared-ui').Product[]>([]);

  primaryImg = computed((): string => {
    const p = this.product();
    return p ? this.catalog.primaryImage(p) : '';
  });

  gallery = computed((): string[] => {
    const p = this.product();
    if (!p) return [];
    const imgs = (p.images ?? []).filter(img => !!img && img.trim().length > 0);
    if (imgs.length > 0) return imgs;
    const fallback = this.catalog.imageFor(p.categoryName ?? '');
    return [fallback];
  });

  reviews = computed(() => this.reviewSvc.reviews() as any[]);

  rating = computed(() => {
    return this.reviewSvc.computeRating(this.reviews());
  });

  readonly commercePriceMinor = signal<number | null>(null);
  displayPrice = computed(() => this.commercePriceMinor() ?? 0);

  featuresList = computed((): string[] => {
    const p = this.product();
    if (!p) return [];
    if (Array.isArray(p.features)) return p.features.filter(f => !!f);
    if (typeof p.features === 'string' && p.features.trim()) {
      return p.features.split('\n').map(s => s.replace(/^[•\-\*]\s*/, '').trim()).filter(Boolean);
    }
    return [
      'Wholesale & retail pricing',
      'Nationwide delivery from Lagos hub',
      '12-month standard warranty',
      'Bulk discounts for distributors'
    ];
  });

  highlightsList = computed((): string[] => {
    const p = this.product();
    if (!p) return [];
    if (Array.isArray(p.highlights)) return p.highlights.filter(h => !!h);
    if (typeof p.highlights === 'string' && p.highlights.trim()) {
      return p.highlights.split('\n').map(s => s.replace(/^[•\-\*]\s*/, '').trim()).filter(Boolean);
    }
    return [
      'Wholesale & retail pricing tier',
      'Fast dispatch from central Lagos hub',
      '12-month standard Nigson warranty',
      'Bulk order distributor volume discounts'
    ];
  });

  packagingContentsList = computed((): string[] => {
    const p = this.product();
    if (!p) return [];
    if (Array.isArray(p.packaging)) return p.packaging.filter(c => !!c);
    if (typeof p.packaging === 'string' && p.packaging.trim()) {
      return p.packaging.split('\n').map(s => s.replace(/^[•\-\*]\s*/, '').trim()).filter(Boolean);
    }
    return [
      `1x ${p.name || 'Product'} unit`,
      '1x User Manual & Setup Guide',
      '1x Nigson Warranty Card'
    ];
  });

  cartonSpecsList = computed((): string[] => {
    const p = this.product();
    if (!p) return [];
    if (Array.isArray(p.cartonSpecs)) return p.cartonSpecs.filter(c => !!c);
    if (typeof p.cartonSpecs === 'string' && p.cartonSpecs.trim()) {
      return p.cartonSpecs.split('\n').map(s => s.replace(/^[•\-\*]\s*/, '').trim()).filter(Boolean);
    }
    return [
      'Carton Quantity: 40 Units / Box',
      'Gross Weight: ~ 0.45 kg per unit box',
      'Barcode: Individually Labelled for Resale'
    ];
  });

  specValue(key: string): string {
    const p = this.product();
    if (!p) return '';
    const rawVal = p.specifications ? p.specifications[key] : undefined;
    const val = rawVal !== undefined ? rawVal : (p as any)[key];
    if (val == null) return '';
    if (Array.isArray(val)) return val.join(', ');
    return String(val);
  }

  extraSpecs = computed((): Array<{ key: string, value: string }> => {
    const p = this.product();
    if (!p?.specifications) return [];
    const known = new Set([
      'warranty', 'connectors', 'power_rating', 'powerRating',
      'compatibility', 'build_material', 'buildMaterial',
      'certification', 'importer', 'packaging', 'carton_specs', 'cartonSpecs',
      'warehouse_status', 'warehouseStatus', 'dispatch_turnaround', 'dispatchTurnaround',
      'nationwide_shipping', 'nationwideShipping', 'min_wholesale_order', 'minWholesaleOrder',
      'features', 'overview', 'highlights', 'price_subtitle', 'priceSubtitle',
      'starting_price', 'wholesale_rate', 'distributor_rate'
    ]);
    const list: Array<{ key: string, value: string }> = [];
    for (const [k, v] of Object.entries(p.specifications)) {
      if (!known.has(k) && v != null && String(v).trim()) {
        const title = k.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
        const displayVal = Array.isArray(v) ? v.join(', ') : String(v);
        list.push({ key: title, value: displayVal });
      }
    }
    return list;
  });

  purchaseMinimum = signal(1);
  purchasePack = signal(1);
  productWithPrice = computed(() => {
    const p = this.product();
    if (!p || this.commercePriceMinor() === null) return null;
    const minor = this.displayPrice();
    return {
      ...p,
      minimumQuantity: this.purchaseMinimum(),
      packMultiple: this.purchasePack(),
      priceMinorUnits: minor,
      price: minor / 100
    };
  });

  liveAverage = computed((): number => {
    return this.rating().average;
  });

  totalReviews = computed((): number => {
    return this.rating().count;
  });

  activeTab = signal<'description' | 'specifications' | 'packaging' | 'availability'>('description');
  tabs = ['description', 'specifications', 'packaging', 'availability'] as const;


  relativeDate = relativeDate;

  qtyInCart = computed((): number => {
    const p = this.product();
    if (!p) return 0;
    return this.shop.cart().find(c => c.sku === p.sku || (c as any).skuId === p.skuId)?.qty ?? 0;
  });

  private readonly pricingSession = inject(SessionService);
  private readonly priceQuery = computed(() => ({ customer: this.pricingSession.userId(), authenticated: this.pricingSession.authenticated(), skuId: this.product()?.skuId, quantity: Math.max(1, this.qtyInCart()) }));
  constructor() {
    toObservable(this.priceQuery).pipe(
      switchMap(query => query.skuId ? this.catalog.getPrice(query.skuId, query.quantity).pipe(map(price => {this.purchaseMinimum.set(price.minimumQuantity);this.purchasePack.set(price.packMultiple);return price.priceMinorUnits;}), catchError(() => of(null))) : of(null)),
      takeUntilDestroyed()
    ).subscribe(price => this.commercePriceMinor.set(price));
  }

  zoom = signal(1);
  index = signal(0);
  fading = signal(false);

  ngOnInit() {
    this.route.paramMap.subscribe(params => {
      // TASK-21: route param is 'id' (UUID). Fall back to 'sku' for backward compat.
      const id = params.get('id') || params.get('sku') || '';
      this.index.set(0);
      this.zoom.set(1);
      window.scrollTo({ top: 0 });
      if (id) {
        this.productLoading.set(true);
        this.product.set(null);
        this.catalog.getProduct(id).subscribe({
          next: p => {
            this.product.set(p);
            this.commercePriceMinor.set(null);

            this.productLoading.set(false);
            if (p) this.reviewSvc.getProductReviews(p.id).subscribe();
          },
          error: () => this.productLoading.set(false)
        });
        // TASK-22: real recommendations
        this.catalog.getRecommended(id).subscribe(recs => this.recommended.set(recs));
      }
    });
  }

  goBack() { this.location.back(); }

  setZoom(val: number) { this.zoom.set(Math.max(0.5, Math.min(3, val))); }

  go(next: number) {
    this.fading.set(true);
    setTimeout(() => {
      const len = this.gallery().length;
      this.index.set((next + len) % len);
      this.zoom.set(1);
      this.fading.set(false);
    }, 140);
  }

  scrollToReviews() {
    document.getElementById('reviews')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  addToCart() {
    const p = this.product();
    const quantity=Math.ceil(this.purchaseMinimum()/this.purchasePack())*this.purchasePack();
    if (!p || !this.productWithPrice() || (p.stock??0)<quantity) return;
    this.shop.addToCart({
      sku: p.sku,
      skuId: p.skuId || p.id,
      stock:p.stock,
      stockStatus:p.stockStatus,
      name: p.name,
      category: p.categoryName ?? '',
      price: (p.priceMinorUnits ?? 0) / 100,
      image: this.primaryImg()
    } as any,quantity);
  }
}

