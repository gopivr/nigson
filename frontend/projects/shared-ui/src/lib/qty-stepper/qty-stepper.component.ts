import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ShopService } from '../shop.service';
import { LucideMinus, LucidePlus, LucideShoppingCart } from '@lucide/angular';

@Component({
  selector: 'ui-qty-stepper',
  standalone: true,
  imports: [CommonModule, LucideMinus, LucidePlus, LucideShoppingCart],
  template: `
    <ng-container *ngIf="stock <= 0; else inStock">
      <span
        [ngClass]="compact ? 'px-3 py-2 text-xs' : 'px-6 py-3 text-sm'"
        class="inline-flex items-center justify-center gap-2 rounded-full bg-surface-alt text-muted-ink font-semibold border border-hairline cursor-not-allowed w-full"
      >
        {{product?.stockStatus==='UNKNOWN'?'Availability unavailable':'Out of Stock'}}
      </span>
    </ng-container>

    <ng-template #inStock>
      <button
        *ngIf="qty() <= 0"
        type="button"
        (click)="add($event)"
        [disabled]="product?.priceMinorUnits == null && product?.price == null || initialQuantity() > stock"
        title="Add to cart"
        aria-label="Add to cart"
        [ngClass]="compact ? 'px-3 py-2 text-xs' : 'px-4 sm:px-6 py-2.5 sm:py-3 text-xs sm:text-sm'"
        class="inline-flex w-full shrink-0 whitespace-nowrap items-center justify-center gap-1.5 rounded-full bg-brand text-white font-semibold hover:bg-brand-deep active:scale-95 transition-all shadow-sm"
      >
        <svg lucideShoppingCart [ngClass]="compact ? 'h-3.5 w-3.5' : 'h-4 w-4'"></svg> {{initialQuantity()>stock?'Insufficient stock':'Add to Cart'}}
      </button>

      <div
        *ngIf="qty() > 0"
        [ngClass]="compact ? 'px-1.5 py-1' : 'px-2 py-1.5'"
        class="flex w-full items-center justify-between gap-1 rounded-full bg-brand text-white shadow-sm transition-all"
      >
        <button
          type="button"
          aria-label="Decrease quantity"
          title="Decrease quantity"
          (click)="decrease($event)"
          [ngClass]="compact ? 'h-6 w-6' : 'h-8 w-8'"
          class="grid place-items-center rounded-full hover:bg-brand-deep transition-colors shrink-0"
        >
          <svg lucideMinus [ngClass]="compact ? 'h-3 w-3' : 'h-4 w-4'"></svg>
        </button>

        <span
          [ngClass]="compact ? 'min-w-[1.5rem] text-xs' : 'min-w-[2.5rem] text-sm'"
          class="text-center font-bold tabular-nums"
        >{{ qty() }}</span>

        <button
          type="button"
          aria-label="Increase quantity"
          title="Increase quantity"
          [disabled]="atMax()"
          (click)="update(nextQuantity(), $event)"
          [ngClass]="compact ? 'h-6 w-6' : 'h-8 w-8'"
          class="grid place-items-center rounded-full hover:bg-brand-deep transition-colors disabled:opacity-40 disabled:hover:bg-transparent shrink-0"
        >
          <svg lucidePlus [ngClass]="compact ? 'h-3 w-3' : 'h-4 w-4'"></svg>
        </button>
      </div>
    </ng-template>
  `
})
export class QtyStepperComponent {
  shop = inject(ShopService);

  @Input({ required: true }) product!: any;
  @Input() image?: string;
  @Input() compact = false;

  get stock(): number {
    if(this.product?.stockStatus==='UNKNOWN'||this.product?.stockStatus==='OUT_OF_STOCK')return 0;
    return this.product?.stock ?? Number.POSITIVE_INFINITY;
  }

  private isGenericToken(val?: string): boolean {
    if (!val) return true;
    const clean = val.trim().toUpperCase();
    return (
      clean.length < 3 ||
      clean === 'DEMO' ||
      clean === 'GENERAL' ||
      clean === 'NG-SKU' ||
      clean === 'PRODUCT' ||
      clean === 'SKU' ||
      clean === 'UNDEFINED' ||
      clean === 'NULL'
    );
  }

  findCartItem(): any {
    const p = this.product;
    if (!p) return null;
    const items = this.shop.cart();
    if (!items || items.length === 0) return null;

    return items.find((c: any) => {
      // 1. Line ID match
      if (p.lineId && (c.lineId === p.lineId || c.skuId === p.lineId)) return true;

      // 2. Exact match on skuId (UUID)
      if (p.skuId && (c.skuId === p.skuId || (c.sku === p.skuId && !this.isGenericToken(c.sku)))) return true;

      // 3. Exact match on product UUID
      if (p.id) {
        if (c.productId && c.productId === p.id) return true;
        if (c.skuId && c.skuId === p.id) return true;
      }

      // 4. Exact match on non-generic SKU code (e.g. DEMO-EL-01-03 or NS03)
      if (!this.isGenericToken(p.sku) && !this.isGenericToken(c.sku)) {
        if (p.sku.trim().toLowerCase() === c.sku.trim().toLowerCase()) return true;
      }

      return false;
    });
  }

  qty(): number {
    const item = this.findCartItem();
    return item ? item.qty : 0;
  }

  minimum(): number { return Math.max(this.product?.minimumQuantity || 1, this.pack()); }
  pack(): number { return this.product?.packMultiple || 1; }
  initialQuantity(): number { return Math.ceil(this.minimum() / this.pack()) * this.pack(); }
  nextQuantity(): number { return Math.max(this.initialQuantity(), Math.round((this.qty() + this.pack()) * 1000) / 1000); }
  decrease(e?: Event) { const next = Math.min(Math.round((this.qty() - this.pack()) * 1000) / 1000,Math.floor(this.stock/this.pack())*this.pack()); this.update(next < this.minimum() ? 0 : next, e); }
  atMax(): boolean { return this.nextQuantity() > this.stock; }

  add(e?: Event) {
    e?.stopPropagation();
    e?.preventDefault();
    const p = this.product;
    if (!p || p.priceMinorUnits == null && p.price == null || this.initialQuantity() > this.stock) return;
    const priceMinor = p.priceMinorUnits ?? (p.price != null ? Math.round(p.price * 100) : 0);
    this.shop.addToCart({
      sku: p.sku || p.id,
      skuId: p.skuId || p.id,
      productId: p.id,
      name: p.name || 'Product',
      category: p.categoryName ?? p.category ?? 'General',
      price: p.price ?? (priceMinor / 100),
      priceMinorUnits: priceMinor,
      stock: this.stock,
      stockStatus: p.stockStatus,
      image: this.image || p.imageUrl || (p.images ? p.images[0] : undefined),
    } as any, this.initialQuantity());
  }

  update(newQty: number, e?: Event) {
    e?.stopPropagation();
    e?.preventDefault();
    const p = this.product;
    if (!p) return;
    if (newQty > this.stock && newQty > this.qty()) return;
    const item = this.findCartItem();
    const targetKey = item?.skuId || item?.lineId || p.skuId || (item && !this.isGenericToken(item.sku) ? item.sku : null) || p.id;
    if (!targetKey) return;
    this.shop.updateQty(targetKey, newQty);
  }
}
