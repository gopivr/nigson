import { WishlistButtonComponent,StockStatusComponent } from 'shared-ui';
import { Component, Input, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CatalogService, Product, ShopService, QtyStepperComponent } from 'shared-ui';

/**
 * ProductCardComponent (TASK-19, TASK-20, TASK-21)
 *
 * - Route uses product UUID (/products/:id)
 * - Uses shared ui-qty-stepper component for Add to Cart & live quantity counting
 * - Count stays synchronized reactively across every page (cards, details, cart, checkout)
 */
@Component({
  selector: 'app-product-card',
  standalone: true,
  imports: [CommonModule, RouterLink, QtyStepperComponent, WishlistButtonComponent,StockStatusComponent],
  template: `
    <div class="group flex flex-col text-left rounded-2xl border border-border bg-background overflow-hidden hover:shadow-elegant hover:-translate-y-1 transition-all h-full">
      <!-- Route uses product UUID (id) -->
      <a [routerLink]="['/products', p.id]" class="text-left flex-1" [attr.title]="p.name">
        <div class="aspect-[4/3] bg-surface-alt relative overflow-hidden">
          <img
            [src]="primaryImage()"
            [alt]="p.name"
            loading="lazy"
            class="absolute inset-0 h-full w-full object-cover transition-transform duration-[1200ms] group-hover:scale-105"
          />
          <span class="absolute top-3 left-3 rounded-full bg-background/90 backdrop-blur px-3 py-1 text-[10px] font-semibold uppercase tracking-widest text-brand">
            {{ p.categoryName ?? 'Product' }}
          </span>
        </div>
        <div class="px-5 pt-5">
          <p class="text-[11px] uppercase tracking-widest text-muted-foreground">{{ p.sku }}</p>
          <h3 class="mt-1 text-lg font-semibold group-hover:text-brand transition-colors">{{ p.name }}</h3>
          <div class="mt-2"><ui-stock-status [status]="p.stockStatus"></ui-stock-status></div>
          <p class="mt-3 text-[11px] uppercase tracking-widest text-muted-foreground">Starting from</p>
          <ng-container *ngIf="!priceLoading()">
            <p class="mt-1 text-[22px] font-bold text-[#D62828] leading-tight">
              {{ displayPrice() }}
            </p>
          </ng-container>
          <p *ngIf="priceLoading()" class="mt-1 h-7 w-28 animate-pulse rounded-lg bg-surface-alt"></p>
          <p *ngIf="priceMinorUnits() !== null && minimumQuantity() > 1" class="mt-2 text-xs font-semibold">Minimum {{ minimumQuantity() }} units<span *ngIf="packMultiple() > 1">; multiples of {{ packMultiple() }}</span>.</p>
          <p *ngIf="!compact" class="mt-3 text-sm text-muted-foreground leading-relaxed line-clamp-2">{{ p.description }}</p>
        </div>
      </a>

      <div class="px-5 pt-3"><ui-wishlist-button [productId]="p.id" [skuId]="resolvedSkuId() || p.skuId"></ui-wishlist-button></div>
      <!-- Action Area: View Details + ui-qty-stepper Add to Cart component -->
      <div class="mt-auto px-5 pb-5 pt-4 flex items-center gap-2">
        <a
          [routerLink]="['/products', p.id]"
          title="View product details"
          aria-label="View details"
          class="shrink-0 rounded-full border border-hairline px-3.5 py-2 text-xs font-semibold text-ink hover:border-brand hover:text-brand transition-colors text-center"
        >
          View Details
        </a>
        <div class="flex-1 min-w-0">
          <ui-qty-stepper
            *ngIf="priceMinorUnits() !== null"
            [product]="productForStepper()"
            [image]="primaryImage()"
            [compact]="true"
          ></ui-qty-stepper>
          <p *ngIf="priceMinorUnits()===null && p.stockStatus==='OUT_OF_STOCK'" class="text-sm font-semibold text-muted-ink">Out of stock</p>
        </div>
      </div>
    </div>
  `
})
export class ProductCardComponent implements OnInit {
  @Input({ required: true }) p!: Product;
  @Input() compact = false;

  catalog = inject(CatalogService);
  shop = inject(ShopService);

  readonly priceLoading = signal(true);
  readonly displayPrice = signal('Contact for price');
  readonly priceMinorUnits = signal<number | null>(null);
  readonly minimumQuantity = signal(1);
  readonly packMultiple = signal(1);
  readonly resolvedSkuId = signal<string | null>(null);

  productForStepper = computed(() => {
    const skuId = this.resolvedSkuId() || (this.p.skuId && this.p.skuId !== this.p.id ? this.p.skuId : null) || this.p.id;
    const minor = this.priceMinorUnits() ?? 0;
    return {
      ...this.p,
      skuId: skuId,
      sku: this.p.sku,
      id: this.p.id,
      name: this.p.name,
      categoryName: this.p.categoryName || 'General',
      imageUrl: this.primaryImage(),
      minimumQuantity: this.minimumQuantity(),
      packMultiple: this.packMultiple(),
      priceMinorUnits: minor,
      price: minor / 100
    };
  });

  ngOnInit() {
    const loadPrice = (skuId: string | undefined) => {
      if (!skuId || skuId === this.p.id) {
        if (!this.p.priceMinorUnits && !this.p.price) {
          this.priceLoading.set(false);
        }
        return;
      }
      this.resolvedSkuId.set(skuId);
      this.catalog.getPrice(skuId).subscribe({
        next: price => {
          this.minimumQuantity.set(price.minimumQuantity);
          this.packMultiple.set(price.packMultiple);
          this.priceMinorUnits.set(price.priceMinorUnits);
          this.displayPrice.set(this.catalog.formatPrice(price.priceMinorUnits));
          this.priceLoading.set(false);
        },
        error: () => {
          this.priceMinorUnits.set(null);
          this.displayPrice.set('Contact for price');
          this.priceLoading.set(false);
        }
      });
    };

    if (this.p.skuId && this.p.skuId !== this.p.id && this.p.stockStatus) {
      loadPrice(this.p.skuId);
    } else {
      this.catalog.getProduct(this.p.id).subscribe({
        next: product => {
          this.p={...this.p,stock:product?.stock??0,stockStatus:product?.stockStatus??'UNKNOWN'};
          if (product?.skuId) {
            this.resolvedSkuId.set(product.skuId);
            loadPrice(product.skuId);
          } else {
            this.priceLoading.set(false);
          }
        },
        error: () => this.priceLoading.set(false)
      });
    }
  }

  primaryImage(): string {
    return this.catalog.primaryImage(this.p);
  }
}
