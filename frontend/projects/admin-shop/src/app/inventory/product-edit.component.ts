import { Component, inject, signal, OnInit, ChangeDetectorRef } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ProductPricesService, CommercialPrices, AdminService, CatalogService, retailAmount, canEditRetailPrice } from 'shared-ui';

export interface CustomSpec {
  key: string;
  value: string;
}

@Component({
  selector: 'app-product-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="space-y-6 max-w-6xl pb-20">
      <!-- Top Action Bar -->
      <div class="flex flex-wrap items-center justify-between gap-4 border-b border-gray-100 pb-5">
        <div class="flex items-center gap-3">
          <a routerLink="/admin/inventory/products" class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-gray-200 bg-white text-ink hover:bg-gray-50 transition-colors shadow-sm" title="Back to products">
            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
          </a>
          <div>
            <div class="flex items-center gap-2">
              <h1 class="text-xl md:text-2xl font-black text-ink tracking-tight">
                {{ isNew() ? 'New Product' : (product.name || 'Edit Product') }}
              </h1>
              <span [class]="product.status === 'Active' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-amber-50 text-amber-700 border-amber-200'"
                    class="rounded-full border px-2.5 py-0.5 text-[11px] font-bold">
                {{ product.status || 'Active' }}
              </span>
            </div>
            <p class="text-xs text-muted-ink mt-0.5 font-mono">
              SKU: {{ product.sku || 'NG-NEW' }} • Category: {{ product.category }} • Retail Selling Price: ₦{{ (product.retail || product.price || 0) | number }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <!-- View live product on storefront -->
          <a *ngIf="!isNew()" 
             [routerLink]="['/products', product.id || product.sku]" 
             target="_blank"
             class="inline-flex items-center gap-1.5 rounded-full border border-gray-200 bg-white px-4 py-2 text-xs font-bold text-ink hover:border-brand hover:text-brand transition-colors shadow-sm">
            <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
            </svg>
            View on Store
          </a>

          <a routerLink="/admin/inventory/products" 
             class="rounded-full border border-gray-200 bg-white px-5 py-2 text-xs font-bold text-ink hover:bg-gray-50 transition-colors shadow-sm">
            Cancel
          </a>
          <button (click)="save()" 
                  type="button" 
                  [disabled]="isSaving()"
                  class="rounded-full bg-brand px-6 py-2 text-xs font-bold uppercase tracking-wider text-white shadow-md shadow-brand/20 hover:bg-brand-600 transition-colors disabled:opacity-50 flex items-center gap-2">
            <svg *ngIf="isSaving()" class="h-3.5 w-3.5 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <span>{{ isSaving() ? 'Saving...' : (isNew() ? 'Create Product' : 'Save Changes') }}</span>
          </button>
        </div>
      </div>

      <!-- Success Notification Banner -->
      <div *ngIf="uploadError()" role="alert" class="mb-4 rounded-xl border border-red-300 bg-red-50 p-4 text-sm text-red-800">{{uploadError()}}</div>
      <div *ngIf="saveSuccess()" class="flex items-center justify-between rounded-2xl border border-emerald-200 bg-emerald-50 px-5 py-3.5 text-xs font-bold text-emerald-800 animate-fade-in shadow-sm">
        <div class="flex items-center gap-2">
          <svg class="h-4 w-4 text-emerald-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
          Product details saved. Any retail price change was confirmed by the pricing workflow.
          <a *ngIf="product.sku" [routerLink]="['/products', product.id || product.sku]" target="_blank" class="ml-2 underline text-emerald-900 font-extrabold hover:text-brand">
            Open Live Product Page →
          </a>
        </div>
        <button (click)="saveSuccess.set(false)" class="text-emerald-700 hover:text-emerald-900 font-bold">✕</button>
      </div>

      <!-- SECTION 1: STOREFRONT HERO LAYOUT (Image Gallery on Left, Core Product Info & Pricing on Right) -->
      <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
        
        <!-- LEFT: 5-Image Gallery (S3 Storage) -->
        <div class="lg:col-span-5 rounded-[20px] border border-gray-100 bg-white p-6 shadow-sm space-y-5">
          <div class="flex items-center justify-between border-b border-gray-100 pb-3">
            <div>
              <h2 class="text-xs font-extrabold uppercase tracking-[0.14em] text-muted-ink">Product Images (5-Slot Gallery)</h2>
              <p class="text-[11px] text-muted-ink mt-0.5">S3 storage • Slot 1 is Primary/Hero Image</p>
            </div>
            <span class="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-0.5 text-[11px] font-bold text-emerald-700 border border-emerald-200">
              <span class="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
              {{ activeImageCount() }}/5 active
            </span>
          </div>

          <p *ngIf="!imageRequired()" class="rounded-xl border border-blue-200 bg-blue-50 p-3 text-xs text-blue-900">Local testing: images are optional for publishing. Products without an image use the storefront placeholder.</p>
          <div *ngIf="uploadError()" class="rounded-xl border border-red-200 bg-red-50 p-2.5 text-xs font-semibold text-red-700">
            {{ uploadError() }}
          </div>

          <!-- Slot 1: Primary Main Image (Large View) -->
          <div class="rounded-2xl border-2 border-brand/80 bg-brand/5 p-3.5 space-y-2">
            <div class="flex items-center justify-between">
              <span class="text-xs font-black text-ink flex items-center gap-1.5">
                <span class="h-2 w-2 rounded-full bg-brand"></span> Main Product Image
              </span>
              <span class="rounded-full bg-brand px-2 py-0.5 text-[10px] font-extrabold uppercase text-white tracking-wider">Primary</span>
            </div>
            
            <div class="relative aspect-square w-full overflow-hidden rounded-xl border border-gray-200 bg-white grid place-items-center">
              <div *ngIf="uploadingSlot() === 0" class="flex flex-col items-center justify-center gap-2 p-3">
                <svg class="h-6 w-6 animate-spin text-brand" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <span class="text-xs font-bold text-brand">Uploading to S3...</span>
              </div>
              <ng-container *ngIf="uploadingSlot() !== 0">
                <img *ngIf="product.images[0]" [src]="product.images[0]" class="h-full w-full object-contain p-2" alt="Primary product image">
                <span *ngIf="!product.images[0]" class="text-xs font-semibold text-muted-ink">No primary image selected</span>
              </ng-container>
            </div>

            <div class="flex items-center gap-2 pt-1">
              <label [class.pointer-events-none]="uploadingSlot() !== null"
                     class="flex-1 text-center cursor-pointer rounded-full border border-gray-200 bg-white px-3 py-1.5 text-xs font-bold text-ink hover:border-brand hover:text-brand transition-colors shadow-sm">
                {{ product.images[0] ? 'Replace Primary' : 'Upload Primary Image' }}
                <input type="file" accept="image/*" class="hidden" (change)="onFileChange($event, 0)" [disabled]="uploadingSlot() !== null">
              </label>
              <button *ngIf="product.images[0]" type="button" (click)="removeImage(0)" [disabled]="uploadingSlot() !== null" class="text-xs font-semibold text-red-600 hover:underline px-2">
                Remove
              </button>
            </div>
          </div>

          <!-- Slots 2 to 5: Additional Detail Gallery Slots -->
          <div class="space-y-2">
            <p class="text-[11px] font-bold uppercase tracking-wider text-muted-ink">Additional Gallery Images (Slots 2–5)</p>
            <div class="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
              <div *ngFor="let idx of [1, 2, 3, 4]" class="flex flex-col justify-between rounded-xl border border-gray-200 bg-white p-2">
                <div class="mb-1 flex items-center justify-between">
                  <span class="text-[10px] font-bold text-muted-ink">Slot {{ idx + 1 }}</span>
                </div>

                <div class="relative aspect-square w-full overflow-hidden rounded-lg border border-gray-100 bg-gray-50 mb-2 grid place-items-center">
                  <div *ngIf="uploadingSlot() === idx" class="flex flex-col items-center justify-center p-1">
                    <svg class="h-4 w-4 animate-spin text-brand" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                  </div>
                  <ng-container *ngIf="uploadingSlot() !== idx">
                    <img *ngIf="product.images[idx]" [src]="product.images[idx]" class="h-full w-full object-contain p-1" [alt]="'Slot ' + (idx + 1)">
                    <span *ngIf="!product.images[idx]" class="text-[10px] font-medium text-muted-ink">Empty</span>
                  </ng-container>
                </div>

                <div class="space-y-1">
                  <label [class.pointer-events-none]="uploadingSlot() !== null"
                         class="block w-full text-center cursor-pointer rounded-full border border-gray-200 bg-white px-1.5 py-1 text-[10px] font-bold text-ink hover:border-brand hover:text-brand transition-colors shadow-xs">
                    {{ product.images[idx] ? 'Replace' : 'Upload' }}
                    <input type="file" accept="image/*" class="hidden" (change)="onFileChange($event, idx)" [disabled]="uploadingSlot() !== null">
                  </label>
                  <button *ngIf="product.images[idx]" type="button" (click)="removeImage(idx)" [disabled]="uploadingSlot() !== null" class="w-full text-center text-[10px] font-semibold text-red-600 hover:underline">
                    Remove
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- RIGHT: Storefront Hero Details, Pricing & Feature Bullets -->
        <div class="lg:col-span-7 space-y-6">
          
          <!-- Basic Info Card -->
          <div class="rounded-[20px] border border-gray-100 bg-white p-6 shadow-sm space-y-4">
            <h2 class="text-xs font-extrabold uppercase tracking-[0.14em] text-muted-ink">Product Identification & Category</h2>
            
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="sm:col-span-2">
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Product Name *</label>
                <input type="text" [(ngModel)]="product.name" name="name" required placeholder="e.g. NS03 Wireless Earbuds (TWS)" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20 font-bold">
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">SKU Model Code *</label>
                <input type="text" [(ngModel)]="product.sku" name="sku" required placeholder="e.g. NS03" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-sm font-mono font-bold text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Category *</label>
                <select [(ngModel)]="product.category" name="category" required class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-sm font-medium text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
                  <option *ngFor="let c of categories" [value]="c">{{ c }}</option>
                </select>
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Brand</label>
                <input type="text" [(ngModel)]="product.brand" name="brand" placeholder="e.g. Nigson" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Catalog Status</label>
                <select [(ngModel)]="product.status" name="status" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-sm text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20 font-semibold">
                  <option [disabled]="!canPublishProduct" value="Active">Publish in Nigeria</option>
                  
                  <option value="Draft">Draft</option>
                  <option [disabled]="!canPublishProduct" value="Archived">Archived</option>
                </select>
              </div>
            </div>
          </div>

          <section class="rounded-2xl border border-hairline bg-white p-5">
            <h2 class="font-bold">Measured shipping package</h2>
            <p class="mb-3 text-xs text-muted-ink">Required when creating a SKU. Units per package must be a whole number (e.g. 1). Enter measured values.</p>
            <div class="grid grid-cols-2 gap-3">
              <label *ngFor="let field of packagingFields" class="text-xs font-semibold text-ink">
                {{field.label}}
                <input
                  type="number"
                  [min]="field.integer ? '1' : '0'"
                  [step]="field.integer ? '1' : '0.001'"
                  [placeholder]="field.placeholder"
                  [(ngModel)]="measurements[field.key]"
                  [name]="'measurement-'+field.key"
                  class="mt-1 w-full rounded-lg border p-2 text-xs text-ink"
                />
              </label>
            </div>
          </section>
          <!-- Pricing & Commercial Tiers Card -->
          <div class="rounded-[20px] border border-gray-100 bg-white p-6 shadow-sm space-y-4">
            <div class="flex items-center justify-between border-b border-gray-100 pb-3">
              <div>
                <h2 class="text-xs font-extrabold uppercase tracking-[0.14em] text-muted-ink">Commercial Pricing (₦)</h2>
                <p class="text-[11px] text-muted-ink mt-0.5">Set retail, wholesale and distributor prices. Leave a business price and minimum blank to keep that rate unavailable.</p>
              </div>
            </div>

            <p *ngIf="pricingNotice()" role="status" class="text-sm text-muted-ink">{{ pricingNotice() }}</p>
            <div class="grid gap-4 sm:grid-cols-3">
              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-brand">Starting Price (₦) *</label>
                <input type="text" inputmode="decimal" [(ngModel)]="product.retail" (ngModelChange)="onPriceChange($event)" name="retail" [disabled]="!canEditRetail" step="0.01" min="0" placeholder="e.g. 18500" class="w-full rounded-2xl border-2 border-brand/50 bg-brand/5 px-4 py-2.5 text-base font-black text-brand shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
                <p class="mt-1 text-[10px] text-muted-ink font-semibold">Applies through the authorized retail price-book workflow.</p>
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Wholesale Rate (₦)</label>
                <input type="text" inputmode="decimal" [(ngModel)]="product.wholesale" name="wholesale" [disabled]="!canEditRetail" class="w-full rounded-2xl border border-gray-200 p-3" placeholder="Price in naira">
                <label class="mt-2 block text-xs">Minimum quantity</label><input type="text" inputmode="decimal" [(ngModel)]="product.wholesaleMinimum" name="wholesaleMinimum" [disabled]="!canEditRetail" class="mt-1 w-full rounded-2xl border border-gray-200 p-3" placeholder="Minimum units">
                <p class="mt-1 text-[10px] text-muted-ink">Applies to approved wholesalers once automatic business pricing is enabled.</p>
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Distributor Rate (₦)</label>
                <input type="text" inputmode="decimal" [(ngModel)]="product.distributor" name="distributor" [disabled]="!canEditRetail" class="w-full rounded-2xl border border-gray-200 p-3" placeholder="Price in naira">
                <label class="mt-2 block text-xs">Minimum quantity</label><input type="text" inputmode="decimal" [(ngModel)]="product.distributorMinimum" name="distributorMinimum" [disabled]="!canEditRetail" class="mt-1 w-full rounded-2xl border border-gray-200 p-3" placeholder="Minimum units">
                <p class="mt-1 text-[10px] text-muted-ink">Applies to approved distributors once automatic business pricing is enabled.</p>
              </div>

              <div class="sm:col-span-3">
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Price Subtitle Text</label>
                <input type="text" [(ngModel)]="product.priceSubtitle" name="priceSubtitle" placeholder="e.g. Wholesale pricing available for verified distributors." class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-xs text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              </div>
            </div>
          </div>



          <!-- Short Description & Hero Feature Bullets Card -->
          <div class="rounded-[20px] border border-gray-100 bg-white p-6 shadow-sm space-y-4">
            <h2 class="text-xs font-extrabold uppercase tracking-[0.14em] text-muted-ink">Hero Presentation (Under Price)</h2>

            <div>
              <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Short Summary Description *</label>
              <textarea [(ngModel)]="product.desc" name="desc" rows="2" required placeholder="e.g. Nigson NS901C Type-C to Type-C Cable supporting up to 65W fast charging..." class="w-full rounded-2xl border border-gray-200 bg-white p-3.5 text-xs text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20 leading-relaxed"></textarea>
              <p class="mt-1 text-[10px] text-muted-ink">Renders immediately under the price and rating.</p>
            </div>

            <div>
              <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Key Feature Bullets (Red Checkmarks) — one per line</label>
              <textarea [(ngModel)]="product.features" name="features" rows="4" placeholder="e.g. Wholesale & retail pricing&#10;Nationwide delivery from Lagos hub&#10;12-month standard warranty&#10;Bulk discounts for distributors" class="w-full rounded-2xl border border-gray-200 bg-white p-3.5 text-xs font-mono text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20 leading-relaxed"></textarea>
              <p class="mt-1 text-[10px] text-muted-ink">Appears with red checkmarks beside the main image.</p>
            </div>
          </div>
        </div>
      </div>

      <!-- SECTION 2: STOREFRONT DETAIL TABS (Mirrors Product Page Tabs 1-to-1) -->
      <div class="rounded-[20px] border border-gray-100 bg-white shadow-sm overflow-hidden">
        <!-- Tabs Header Bar -->
        <div class="border-b border-gray-100 bg-gray-50/50 px-6 pt-2">
          <nav class="flex gap-4 sm:gap-8 overflow-x-auto" aria-label="Tabs">
            <button type="button"
                    *ngFor="let tab of tabs"
                    (click)="activeTab.set(tab.id)"
                    [ngClass]="activeTab() === tab.id ? 'border-brand text-brand font-black' : 'border-transparent text-muted-ink hover:text-ink font-bold'"
                    class="whitespace-nowrap border-b-2 py-4 px-2 text-xs md:text-sm transition-colors flex items-center gap-2">
              <span>{{ tab.label }}</span>
            </button>
          </nav>
        </div>

        <div class="p-7">
          <!-- TAB 1: Description Tab Panel -->
          <div *ngIf="activeTab() === 'description'" class="space-y-6 max-w-4xl">
            <div>
              <div class="flex items-center justify-between mb-2">
                <span class="text-xs font-bold uppercase tracking-widest text-muted-ink">Section Heading</span>
                <span class="text-[11px] text-muted-ink font-mono">Auto-generated: {{ product.name || 'Product' }} Overview</span>
              </div>
              <p class="text-xs text-muted-ink">The customer page renders a prominent overview header using the product name.</p>
            </div>

            <div>
              <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Extended Overview Paragraph</label>
              <textarea [(ngModel)]="product.overview" name="overview" rows="3" placeholder="e.g. Engineered to meet the highest standards of durability and performance across Nigerian markets. Suitable for both wholesale commercial resale and direct consumer use." class="w-full rounded-2xl border border-gray-200 bg-white p-4 text-xs text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20 leading-relaxed"></textarea>
              <p class="mt-1 text-[11px] text-muted-ink">Displayed under the product overview heading.</p>
            </div>

            <div class="border-t border-gray-100 pt-5">
              <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Key Highlights Box Bullets — one per line</label>
              <textarea [(ngModel)]="product.highlights" name="highlights" rows="4" placeholder="e.g. Wholesale & retail pricing tier&#10;Fast dispatch from central Lagos hub&#10;12-month standard Nigson warranty&#10;Bulk order distributor volume discounts" class="w-full rounded-2xl border border-gray-200 bg-white p-4 text-xs font-mono text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20 leading-relaxed"></textarea>
              <p class="mt-1 text-[11px] text-muted-ink">Renders inside the shaded 'Key Highlights' container with green checkmarks.</p>
            </div>
          </div>

          <!-- TAB 2: Specifications Tab Panel -->
          <div *ngIf="activeTab() === 'specifications'" class="space-y-6">
            <div>
              <h3 class="text-xs font-extrabold uppercase tracking-[0.14em] text-muted-ink">Standard Specifications (Table)</h3>
              <p class="text-xs text-muted-ink mt-0.5">These correspond to the rows shown in the Specifications tab on the customer product page.</p>
            </div>

            <div class="grid gap-4 sm:grid-cols-2">
              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Warranty</label>
                <input type="text" [(ngModel)]="product.warranty" name="warranty" placeholder="e.g. 12 Months Standard Manufacturer Warranty" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-xs text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Connectors / Interface</label>
                <input type="text" [(ngModel)]="product.connectors" name="connectors" placeholder="e.g. Type-C to Type-C" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-xs text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Power Rating / Output</label>
                <input type="text" [(ngModel)]="product.powerRating" name="powerRating" placeholder="e.g. 65W PD (20V/3.25A)" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-xs text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Device Compatibility</label>
                <input type="text" [(ngModel)]="product.compatibility" name="compatibility" placeholder="e.g. Universal iOS / Android / USB-C Standards Compliant" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-xs text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Build Material</label>
                <input type="text" [(ngModel)]="product.buildMaterial" name="buildMaterial" placeholder="e.g. Premium Grade Fire-Retardant Polymer & Alloy" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-xs text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Quality Certification</label>
                <input type="text" [(ngModel)]="product.certification" name="certification" placeholder="e.g. CE / RoHS / FCC Quality Verified" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-xs text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              </div>

              <div class="sm:col-span-2">
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Importer & Distributor</label>
                <input type="text" [(ngModel)]="product.importer" name="importer" placeholder="e.g. Nigson Group Ltd, Lagos Nigeria" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-xs text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              </div>
            </div>

            <!-- Custom / Additional Specifications Table -->
            <div class="border-t border-gray-100 pt-6 space-y-4">
              <div class="flex items-center justify-between">
                <div>
                  <h4 class="text-xs font-extrabold uppercase tracking-[0.14em] text-muted-ink">Custom / Additional Specifications</h4>
                  <p class="text-[11px] text-muted-ink mt-0.5">Add any custom key-value pairs (e.g. Length, Cable Weight, Fast Charging Protocol) that appear as rows in the specifications table.</p>
                </div>
                <button type="button" (click)="addCustomSpec()" class="inline-flex items-center gap-1.5 rounded-full border border-gray-200 bg-white px-3 py-1.5 text-xs font-bold text-ink hover:border-brand hover:text-brand shadow-sm transition-colors">
                  + Add Spec Row
                </button>
              </div>

              <div *ngIf="customSpecs.length === 0" class="rounded-xl border border-dashed border-gray-200 p-4 text-center text-xs text-muted-ink">
                No custom specifications added. Click "+ Add Spec Row" to add custom rows like Length, Material, etc.
              </div>

              <div *ngIf="customSpecs.length > 0" class="space-y-2.5">
                <div *ngFor="let spec of customSpecs; let i = index" class="flex items-center gap-3">
                  <input type="text" [(ngModel)]="spec.key" placeholder="Spec Name (e.g. Cable Length)" class="w-1/3 rounded-xl border border-gray-200 bg-white px-3 py-2 text-xs text-ink font-semibold shadow-sm focus:border-brand focus:outline-none">
                  <input type="text" [(ngModel)]="spec.value" placeholder="Spec Value (e.g. 1.2m Braided)" class="flex-1 rounded-xl border border-gray-200 bg-white px-3 py-2 text-xs text-ink shadow-sm focus:border-brand focus:outline-none">
                  <button type="button" (click)="removeCustomSpec(i)" class="rounded-lg p-2 text-red-500 hover:bg-red-50 hover:text-red-700 transition-colors" title="Delete specification">
                    ✕
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- TAB 3: Packaging Tab Panel -->
          <div *ngIf="activeTab() === 'packaging'" class="space-y-6 max-w-4xl">
            <div>
              <h3 class="text-xs font-extrabold uppercase tracking-[0.14em] text-muted-ink">Packaging & Box Contents</h3>
              <p class="text-xs text-muted-ink mt-0.5">Corresponds directly to the Packaging tab on the customer product page.</p>
            </div>

            <div class="grid gap-6 sm:grid-cols-2">
              <div>
                <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Retail Box Contents — one per line</label>
                <textarea [(ngModel)]="product.packaging" name="packaging" rows="4" placeholder="e.g. 1x Unit&#10;1x User Manual & Setup Guide&#10;1x Nigson Warranty Card" class="w-full rounded-2xl border border-gray-200 bg-white p-4 text-xs font-mono text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20 leading-relaxed"></textarea>
                <p class="mt-1 text-[11px] text-muted-ink">Displayed as bullet points inside the Retail Box Contents card.</p>
              </div>

              <div>
                <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-muted-ink">Master Carton Specs — one per line</label>
                <textarea [(ngModel)]="product.cartonSpecs" name="cartonSpecs" rows="4" placeholder="e.g. Carton Quantity: 40 Units / Box&#10;Gross Weight: ~ 0.45 kg per unit box&#10;Barcode: Individually Labelled for Resale" class="w-full rounded-2xl border border-gray-200 bg-white p-4 text-xs font-mono text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20 leading-relaxed"></textarea>
                <p class="mt-1 text-[11px] text-muted-ink">Displayed as bullet points inside the Master Carton Specs card.</p>
              </div>
            </div>
          </div>

          <!-- TAB 4: Availability & Warehouse Tab Panel -->
          <div *ngIf="activeTab() === 'availability'" class="space-y-6">
            <div>
              <h3 class="text-xs font-extrabold uppercase tracking-[0.14em] text-muted-ink">Distribution & Warehouse Availability</h3>
              <p class="text-xs text-muted-ink mt-0.5">Corresponds directly to the Availability tab and inventory tracking.</p>
            </div>

            <div class="grid gap-5 sm:grid-cols-2">
              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Central Lagos Warehouse Status</label>
                <input type="text" [(ngModel)]="product.warehouseStatus" name="warehouseStatus" placeholder="e.g. In Stock & Ready to Ship" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-xs text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20 font-semibold">
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Dispatch Turnaround</label>
                <input type="text" [(ngModel)]="product.dispatchTurnaround" name="dispatchTurnaround" placeholder="e.g. Dispatched within 24 Hours" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-xs text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Nationwide Shipping</label>
                <input type="text" [(ngModel)]="product.nationwideShipping" name="nationwideShipping" placeholder="e.g. 2 - 4 Business Days across Nigeria" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-xs text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Wholesale description (display only)</label>
                <input type="text" [(ngModel)]="product.minWholesaleOrder" name="minWholesaleOrder" placeholder="e.g. 10 Units" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-xs text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20 font-bold text-brand">
              </div>

              <div class="border-t border-gray-100 pt-4 sm:col-span-2 grid gap-5 sm:grid-cols-2">
                <div>
                  <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">On hand stock (physical units)</label>
                  <span *ngIf="!canViewInventory" class="text-sm text-muted-ink">Requires Operations access</span>
                  <input [hidden]="!canViewInventory" type="number" [(ngModel)]="product.stock" name="stock" readonly required placeholder="e.g. 100" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-sm font-bold text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
                  <p class="mt-1 text-[10px] text-muted-ink">Physical stock across warehouses; paid orders remain on hand until dispatch. See Products for available-to-sell quantities.</p>
                </div>

                <div>
                  <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-muted-ink">Low Stock Alert Threshold (Units)</label>
                  <input type="number" [(ngModel)]="product.lowLevel" name="lowLevel" readonly placeholder="e.g. 10" class="w-full rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-sm font-bold text-ink shadow-sm focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/20">
                  <p class="mt-1 text-[10px] text-muted-ink">Triggers warning in admin dashboard.</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Bottom Sticky Save Bar -->
      <div class="flex items-center justify-between pt-6 border-t border-gray-100">
        <a routerLink="/admin/inventory/products" class="rounded-full border border-gray-200 bg-white px-6 py-2.5 text-xs font-bold text-ink hover:bg-gray-50 transition-colors shadow-sm">
          ← Cancel and Return to Products
        </a>

        <div class="flex items-center gap-3">
          <a *ngIf="!isNew()" 
             [routerLink]="['/products', product.id || product.sku]" 
             target="_blank"
             class="inline-flex items-center gap-1.5 rounded-full border border-gray-200 bg-white px-5 py-2.5 text-xs font-bold text-ink hover:border-brand hover:text-brand transition-colors shadow-sm">
            👁️ View on Store
          </a>

          <button (click)="save()" type="button" [disabled]="isSaving()" class="rounded-full bg-brand px-8 py-3 text-xs font-bold uppercase tracking-wider text-white shadow-lg shadow-brand/20 hover:bg-brand-600 transition-colors disabled:opacity-50 flex items-center gap-2">
            <svg *ngIf="isSaving()" class="h-3.5 w-3.5 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <span>{{ isSaving() ? 'Saving Changes...' : (isNew() ? 'Create Product' : 'Save Changes') }}</span>
          </button>
        </div>
      </div>
    </div>
  `
})
export class ProductEditComponent implements OnInit {
  readonly imageRequired = signal(true);
  loadPublicationPolicy() {
    this.imageRequired.set(true);
    this.http.get<{imageRequired:boolean}>('/api/v1/staff/catalogue/publication-policy').subscribe({
      next:policy=>{this.imageRequired.set(policy.imageRequired!==false);this.cdr.markForCheck();},
      error:()=>{this.imageRequired.set(true);this.cdr.markForCheck();}
    });
  }
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private admin = inject(AdminService);
  private catalog = inject(CatalogService);
  private productPrices = inject(ProductPricesService);
  private commercialPrices: CommercialPrices | null = null;
  private originalRetail: string | null = null;
  readonly pricingNotice = signal('');
  get canPublishProduct(): boolean { return this.admin.session.hasRole('CONTENT_PUBLISHER'); }
  get canViewInventory(): boolean { return this.admin.session.hasRole('OPERATIONS'); }
  get canEditRetail(): boolean { return canEditRetailPrice(this.admin.session.roles()); }
  private async loadRetailPrice(skuId: string) {
    this.commercialPrices = null;
    this.originalRetail = null;
    this.product.retail = null;
    this.product.price = null;
    this.product.wholesale = null;
    this.product.distributor = null;
    try {
      if (this.canEditRetail) {
        this.commercialPrices = await this.productPrices.get(skuId);
        const amount=this.commercialPrices.retail.unitAmount;
        if(amount!==null){this.originalRetail=retailAmount(amount);this.product.retail=amount;this.product.price=Number(amount);}
        for(const kind of ['wholesale','distributor'] as const){this.product[kind]=this.commercialPrices[kind].unitAmount;this.product[kind+'Minimum']=this.commercialPrices[kind].minimumQuantity;}
        this.pricingNotice.set('Save Changes confirms retail, wholesale and distributor prices together. Existing price books are preserved. Historical business values shown here become standard rates only after you save.');

      } else {
        const price = await firstValueFrom(this.catalog.getPrice(skuId));
        this.product.retail = price.priceMinorUnits / 100;
        this.product.price = this.product.retail;
        this.originalRetail = retailAmount(this.product.retail);
        this.pricingNotice.set('An active Admin role is required for price changes with normal login. Sales/Finance staff retain their existing approval requirements.');
      }
    } catch (error) { this.pricingNotice.set(this.admin.message(error)); }
    this.cdr.markForCheck();
  }
  private http = inject(HttpClient);
  private cdr = inject(ChangeDetectorRef);
  originalState = 'DRAFT';
  private skuConfig: any = null;
  private originalPackaging = '';
  measurements: Record<string, string> = {
    maximumUnitsPerPackage: '1',
    unitWeightKg: '0.250',
    tareWeightKg: '0.050',
    lengthCm: '15.0',
    widthCm: '10.0',
    heightCm: '5.0'
  };
  packagingFields = [
    { key: 'maximumUnitsPerPackage', label: 'Units per package', integer: true, placeholder: 'e.g. 1' },
    { key: 'unitWeightKg', label: 'Unit weight (kg)', integer: false, placeholder: 'e.g. 0.250' },
    { key: 'tareWeightKg', label: 'Packaging weight (kg)', integer: false, placeholder: 'e.g. 0.050' },
    { key: 'lengthCm', label: 'Length (cm)', integer: false, placeholder: 'e.g. 15.0' },
    { key: 'widthCm', label: 'Width (cm)', integer: false, placeholder: 'e.g. 10.0' },
    { key: 'heightCm', label: 'Height (cm)', integer: false, placeholder: 'e.g. 5.0' }
  ];

  readonly isNew = signal(false);
  readonly isSaving = signal(false);
  readonly saveSuccess = signal(false);
  readonly uploadingSlot = signal<number | null>(null);
  readonly uploadError = signal<string | null>(null);
  readonly activeTab = signal<'description' | 'specifications' | 'packaging' | 'availability'>('description');

  readonly tabs = [
    { id: 'description' as const, label: '1. Description & Overview' },
    { id: 'specifications' as const, label: '2. Specifications Table' },
    { id: 'packaging' as const, label: '3. Packaging & Box' },
    { id: 'availability' as const, label: '4. Availability & Warehouse' }
  ];

  categories: string[] = [];

  customSpecs: CustomSpec[] = [];

  product: any = {
    id: '',
    name: '',
    sku: '',
    category: '',
    brand: '',
    status: 'Draft',
    price: null,
    retail: null,
    wholesale: null,
    distributor: null,
    priceSubtitle: '',
    images: ['', '', '', '', ''],
    image: '',
    desc: '',
    features: '',
    overview: '',
    highlights: '',
    warranty: '',
    connectors: '',
    powerRating: '',
    compatibility: '',
    buildMaterial: '',
    certification: '',
    importer: '',
    packaging: '',
    cartonSpecs: '',
    stock: null,
    lowLevel: null,
    warehouseStatus: '',
    dispatchTurnaround: '',
    nationwideShipping: '',
    minWholesaleOrder: ''
  };

  resetProduct() {
    this.product = {
      id: '',
      name: '',
      sku: '',
      category: '',
      brand: '',
      status: 'Draft',
      price: null,
      retail: null,
      wholesale: null,
      distributor: null,
      priceSubtitle: '',
      images: ['', '', '', '', ''],
      image: '',
      desc: '',
      features: '',
      overview: '',
      highlights: '',
      warranty: '',
      connectors: '',
      powerRating: '',
      compatibility: '',
      buildMaterial: '',
      certification: '',
      importer: '',
      packaging: '',
      cartonSpecs: '',
      stock: null,
      lowLevel: null,
      warehouseStatus: '',
      dispatchTurnaround: '',
      nationwideShipping: '',
      minWholesaleOrder: ''
    };
    this.customSpecs = [];
    this.measurements = {
      maximumUnitsPerPackage: '1',
      unitWeightKg: '0.250',
      tareWeightKg: '0.050',
      lengthCm: '15.0',
      widthCm: '10.0',
      heightCm: '5.0'
    };
    this.skuConfig = null;
    this.originalPackaging = '';
    this.uploadError.set(null);
    this.saveSuccess.set(false);
  }

  ngOnInit() {
    this.loadPublicationPolicy();
    this.http.get<any[]>('/api/v1/staff/categories').subscribe({
      next: rows => {
        if (Array.isArray(rows)) {
          const names = rows.filter(row => row.active).map(row => row.name);
          if (this.product.category && !names.includes(this.product.category)) {
            names.push(this.product.category);
          }
          this.categories = names;
          this.cdr.markForCheck();
        }
      },
      error: error => this.uploadError.set(this.admin.message(error))
    });

    this.route.paramMap.subscribe(params => {
      let skuOrId = params.get('sku');
      if (!skuOrId) {
        // Fallback: extract from URL path if Angular route param is not captured
        const segments = window.location.pathname.split('/').filter(Boolean);
        const editIdx = segments.indexOf('edit');
        if (editIdx > 0) {
          skuOrId = segments[editIdx - 1];
        }
      }

      if (!skuOrId || skuOrId === 'new') {
        this.isNew.set(true);
        this.resetProduct();
        this.cdr.markForCheck();
        return;
      }

      this.isNew.set(false);
      this.loadProductData(skuOrId);
    });
  }

  activeImageCount(): number {
    return (this.product.images || []).filter((img: string) => !!img && img.trim().length > 0).length;
  }

  addCustomSpec() {
    this.customSpecs.push({ key: '', value: '' });
  }

  removeCustomSpec(index: number) {
    this.customSpecs.splice(index, 1);
  }

  onPriceChange(val: any) {
    const num = Number(val);
    if (!isNaN(num) && num > 0) {
      this.product.retail = String(val);
      this.product.price = num;

      this.cdr.markForCheck();
    }
  }

  private async loadProductData(skuOrId: string) {
    this.uploadError.set(null);
    try {
      let id: string | null = null;
      let cachedProduct = this.admin.products().find(p => p.id === skuOrId || p.sku === skuOrId);

      // Strategy 1: If parameter is a valid UUID, use it directly
      if (/^[a-f0-9-]{36}$/i.test(skuOrId)) {
        id = skuOrId;
      } else if (cachedProduct?.id) {
        id = cachedProduct.id;
      } else {
        // Strategy 2: Query public catalogue by SKU query (verified to match exact SKU)
        try {
          const searchRes = await firstValueFrom(
            this.http.get<any>(`/api/v1/catalogue/products?market=NG&q=${encodeURIComponent(skuOrId)}`)
          );
          const items = searchRes?.items || searchRes?.content || [];
          if (items.length > 0 && items[0]?.id) {
            id = items[0].id;
          }
        } catch { }

        // Strategy 3: Query staff products by search query
        if (!id) {
          try {
            const staffRes = await firstValueFrom(
              this.http.get<any>(`/api/v1/staff/products?q=${encodeURIComponent(skuOrId)}`)
            );
            const items = staffRes?.items || staffRes?.content || [];
            if (items.length > 0 && items[0]?.id) {
              id = items[0].id;
            }
          } catch { }
        }

        // Strategy 4: Load full staff products table
        if (!id) {
          try {
            const fullList = await firstValueFrom(this.admin.loadStaffProducts());
            const match = fullList.find(p => p.id === skuOrId || p.sku === skuOrId);
            if (match?.id) id = match.id;
          } catch { }
        }
      }

      if (!id) throw new Error(`Product "${skuOrId}" was not found in the staff catalogue.`);

      // Fetch full product details from backend staff API (with fallback to public catalogue API)
      let full: any;
      try {
        full = await firstValueFrom(this.http.get<any>('/api/v1/staff/products/' + id));
      } catch {
        full = await firstValueFrom(this.http.get<any>(`/api/v1/catalogue/products/${id}?market=NG`));
      }

      const sku = full.skus?.[0];
      const skuId = sku?.id || cachedProduct?.skuId;
      const skuCode = sku?.code || cachedProduct?.sku || (skuOrId !== id ? skuOrId : '');

      const brandName = full.brand?.name || (typeof full.brand === 'string' ? full.brand : cachedProduct?.brand || '');
      const categoryName = full.categories?.[0]?.name || (typeof full.category === 'string' ? full.category : cachedProduct?.category || '');
      const descText = full.description || full.desc || '';
      const statusText = (full.status === 'ACTIVE' || full.status === 'Active') ? 'Active' : (full.status === 'ARCHIVED' || full.status === 'Archived') ? 'Archived' : 'Draft';
      const imageUrls = (full.assets || []).map((a: any) => a.url).filter(Boolean);
      while (imageUrls.length < 5) imageUrls.push('');

      // Ensure the category exists in the dropdown options
      if (categoryName && !this.categories.includes(categoryName)) {
        this.categories = [...this.categories, categoryName];
      }

      // Populate core product model
      this.originalState = full.status || 'ACTIVE';
      this.product = {
        ...this.product,
        id: full.id,
        name: full.name,
        slug: full.slug,
        desc: descText,
        description: descText,
        brand: brandName,
        brandId: full.brand?.id || cachedProduct?.brandId || null,
        category: categoryName,
        sku: skuCode,
        skuId: skuId,
        status: statusText,
        version: full.version ?? cachedProduct?.version ?? 0,
        images: imageUrls,
        image: imageUrls[0] || ''
      };

      // Helper to format specification lines for multiline textareas
      const toMultiline = (val: any, fallback: string): string => {
        if (Array.isArray(val) && val.length > 0) return val.join('\n');
        if (typeof val === 'string' && val.trim()) return val;
        return fallback;
      };

      // Populate only persisted specifications. Empty fields stay empty until an
      // administrator provides real product information; placeholders are shown
      // by the form instead of being saved as catalogue data.
      const specs = full.specifications || {};
      this.product.overview = specs['overview'] || (descText && descText !== full.name ? descText : '');
      this.product.highlights = toMultiline(specs['highlights'], '');
      this.product.features = toMultiline(specs['features'], '');
      this.product.warranty = specs['warranty'] || '';
      this.product.connectors = specs['connectors'] || '';
      this.product.powerRating = specs['power_rating'] ?? specs['powerRating'] ?? '';
      this.product.compatibility = specs['compatibility'] || '';
      this.product.buildMaterial = specs['build_material'] || specs['buildMaterial'] || '';
      this.product.certification = specs['certification'] || '';
      this.product.importer = specs['importer'] || '';
      this.product.packaging = toMultiline(specs['packaging'], '');
      this.product.cartonSpecs = toMultiline(specs['carton_specs'] ?? specs['cartonSpecs'], '');
      this.product.warehouseStatus = specs['warehouse_status'] || specs['warehouseStatus'] || '';
      this.product.dispatchTurnaround = specs['dispatch_turnaround'] || specs['dispatchTurnaround'] || 'Dispatched within 24 Hours';
      this.product.nationwideShipping = specs['nationwide_shipping'] || specs['nationwideShipping'] || '2 - 4 Business Days across Nigeria';
      this.product.minWholesaleOrder = specs['min_wholesale_order'] || specs['minWholesaleOrder'] || '10 Units';
      this.product.priceSubtitle = specs['price_subtitle'] || specs['priceSubtitle'] || 'Wholesale pricing available for verified distributors.';

      const known = new Set([
        'warranty', 'connectors', 'power_rating', 'powerRating',
        'compatibility', 'build_material', 'buildMaterial',
        'certification', 'importer', 'packaging',
        'carton_specs', 'cartonSpecs',
        'warehouse_status', 'warehouseStatus',
        'dispatch_turnaround', 'dispatchTurnaround',
        'nationwide_shipping', 'nationwideShipping',
        'min_wholesale_order', 'minWholesaleOrder',
        'features', 'overview', 'highlights',
        'price_subtitle', 'priceSubtitle',
        'starting_price', 'wholesale_rate', 'distributor_rate'
      ]);
      this.customSpecs = Object.entries(specs).filter(([key]) => !known.has(key)).map(([key, value]) => ({ key, value: String(value) }));

      // Load live SKU details and packaging rules from backend
      if (skuId) {
        try {
          this.skuConfig = await firstValueFrom(this.http.get<any>('/api/v1/staff/skus/' + skuId));
          const rule = this.skuConfig?.carrierPackaging;
          if (rule) {
            for (const field of this.packagingFields) {
              if (rule[field.key] != null) this.measurements[field.key] = String(rule[field.key]);
            }
            this.originalPackaging = JSON.stringify(rule);
          }
        } catch { }

        await this.loadRetailPrice(skuId);

        // Stock data belongs to Operations.
        if(this.admin.session.hasRole('OPERATIONS')) try {
          const invData = await firstValueFrom(
            this.http.get<any>(`/api/v1/staff/inventory?skuId=${skuId}`)
          );
          const balances = invData?.items || invData?.content || [];
          if (balances.length > 0) {
            this.product.stock = balances.reduce((sum: number, b: any) => sum + Number(b.onHand || 0), 0);
            this.product.reserved = balances.reduce((sum: number, b: any) => sum + Number(b.reserved || 0), 0);
          }
        } catch { }
      }

      this.cdr.markForCheck();
    } catch (error: any) {
      this.uploadError.set(this.admin.message(error));
      this.cdr.markForCheck();
    }
  }

  private populateProduct(full: any) {
    this.originalState = full.status || 'ACTIVE';
    const statusMap: Record<string, string> = {
      ACTIVE: 'Active',
      DRAFT: 'Draft',
      ARCHIVED: 'Archived',
      Active: 'Active',
      Draft: 'Draft',
      Archived: 'Archived'
    };
    const assetImages = (full.assets || []).map((a: any) => a.url).filter(Boolean);
    const combinedImages = (full.images && full.images.length > 0) ? full.images : (assetImages.length > 0 ? assetImages : (this.product.images || []));
    this.product = {
      ...this.product,
      ...full,
      brand: typeof full.brand === 'string' ? full.brand : full.brand === null ? '' : full.brand?.name ?? this.product.brand ?? '',
      brandId: full.brand === null ? null : full.brand?.id ?? full.brandId ?? this.product.brandId ?? null,
      desc: full.description || full.desc || this.product.desc || '',
      status: statusMap[full.status] || 'Active',
      retail: full.retail ?? full.price ?? this.product.retail,
      wholesale: full.wholesale ?? this.product.wholesale,
      distributor: full.distributor ?? this.product.distributor,
      images: [...combinedImages]
    };
    const toMultiline = (val: any, fallback = ''): string => {
      if (Array.isArray(val) && val.length > 0) return val.join('\n');
      if (typeof val === 'string' && val.trim()) return val;
      return fallback;
    };

    const specs = full.specifications || {};
    this.product.warranty = specs['warranty'] ?? this.product.warranty ?? '';
    this.product.connectors = specs['connectors'] ?? this.product.connectors ?? '';
    this.product.powerRating = specs['power_rating'] ?? specs['powerRating'] ?? this.product.powerRating ?? '';
    this.product.compatibility = specs['compatibility'] ?? this.product.compatibility ?? '';
    this.product.buildMaterial = specs['build_material'] ?? specs['buildMaterial'] ?? this.product.buildMaterial ?? '';
    this.product.certification = specs['certification'] ?? this.product.certification ?? '';
    this.product.importer = specs['importer'] ?? this.product.importer ?? '';
    this.product.packaging = toMultiline(specs['packaging'], this.product.packaging || '');
    this.product.cartonSpecs = toMultiline(specs['carton_specs'] ?? specs['cartonSpecs'], this.product.cartonSpecs || '');
    this.product.warehouseStatus = specs['warehouse_status'] ?? specs['warehouseStatus'] ?? this.product.warehouseStatus ?? '';
    this.product.dispatchTurnaround = specs['dispatch_turnaround'] ?? specs['dispatchTurnaround'] ?? this.product.dispatchTurnaround ?? '';
    this.product.nationwideShipping = specs['nationwide_shipping'] ?? specs['nationwideShipping'] ?? this.product.nationwideShipping ?? '';
    this.product.minWholesaleOrder = specs['min_wholesale_order'] ?? specs['minWholesaleOrder'] ?? this.product.minWholesaleOrder ?? '';
    this.product.features = toMultiline(specs['features'], this.product.features || '');
    this.product.overview = specs['overview'] ?? this.product.overview ?? (this.product.desc || '');
    this.product.highlights = toMultiline(specs['highlights'], this.product.highlights || '');
    this.product.priceSubtitle = specs['price_subtitle'] ?? specs['priceSubtitle'] ?? this.product.priceSubtitle ?? '';

    const known = new Set([
      'warranty', 'connectors', 'power_rating', 'powerRating',
      'compatibility', 'build_material', 'buildMaterial',
      'certification', 'importer', 'packaging',
      'carton_specs', 'cartonSpecs',
      'warehouse_status', 'warehouseStatus',
      'dispatch_turnaround', 'dispatchTurnaround',
      'nationwide_shipping', 'nationwideShipping',
      'min_wholesale_order', 'minWholesaleOrder',
      'features', 'overview', 'highlights',
      'price_subtitle', 'priceSubtitle',
      'starting_price', 'wholesale_rate', 'distributor_rate'
    ]);
    this.customSpecs = Object.entries(specs).filter(([key]) => !known.has(key)).map(([key, value]) => ({ key, value: String(value) }));
    while (this.product.images.length < 5) this.product.images.push('');
    if (!this.product.image && this.product.images[0]) {
      this.product.image = this.product.images[0];
    }
    this.cdr.markForCheck();
  }

  async onFileChange(event: any, index: number) {
    const file: File | undefined = event.target.files?.[0]; if (!file) return;
    this.uploadError.set(null);
    if (!this.product.id || this.isNew()) { this.uploadError.set('Save the product draft before uploading an image.'); return; }
    this.uploadingSlot.set(index);
    try {
      const bytes = await file.arrayBuffer(); const digest = await crypto.subtle.digest('SHA-256', bytes);
      const checksum = [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('');
      const upload = await firstValueFrom(this.http.post<any>('/api/v1/commerce/files/uploads', { purpose: 'IMAGE', filename: file.name, mimeType: file.type, size: file.size, checksumSha256: checksum, linkedResourceId: this.product.id }, { headers: new HttpHeaders({ 'Idempotency-Key': crypto.randomUUID() }) }));
      const transfer = await fetch(upload.uploadUrl, { method: 'PUT', headers: upload.requiredHeaders, body: file });
      if (!transfer.ok) throw new Error('Image storage rejected the upload (HTTP ' + transfer.status + ').');
      const completed = await firstValueFrom(this.http.post<any>('/api/v1/commerce/files/' + upload.assetId + '/completion', { checksumSha256: checksum }, { headers: new HttpHeaders({ 'Idempotency-Key': crypto.randomUUID(), 'If-Match': '"0"' }) }));
      this.uploadError.set('Image uploaded. It must pass the security scan before publication. Current state: ' + completed.state + '.');
    } catch (error: any) { this.uploadError.set(this.admin.message(error)); } finally { this.uploadingSlot.set(null); }
  }

  removeImage(index: number) {
    this.product.images[index] = '';
    if (index === 0) {
      this.product.image = this.product.images.find((img: string) => !!img) || '';
    }
  }

  async save() {
    let productSaved = false;
    let retailApplied = false;
    if (this.isSaving()) return;
    this.isSaving.set(true);
    this.saveSuccess.set(false);
    this.uploadError.set(null);
    let payload: any = null;
    try {
      this.productPrices.validate(this.product);
      const requestedRetail = this.product.retail == null || String(this.product.retail).trim() === '' ? null : retailAmount(this.product.retail);
      if (!this.product.name?.trim() || !this.product.sku?.trim() || !this.product.category) {
        throw new Error('Name, SKU and a backend category are required.');
      }
      if (!this.isNew() && this.product.status === 'Draft' && this.originalState !== 'DRAFT') {
        throw new Error('Published products can be archived, not changed back to a draft.');
      }
      const hasMeasurements = this.packagingFields.some(field => String(this.measurements[field.key] ?? '').trim());
      let rule: any;
      if (this.isNew() || hasMeasurements) {
        rule = { schemaVersion: 1 };
        for (const field of this.packagingFields) {
          let value = String(this.measurements[field.key] ?? '').trim();
          if (field.integer) {
            const intVal = Math.round(Number(value));
            if (!Number.isFinite(intVal) || intVal < 1) {
              throw new Error('Units per package must be a whole number of at least 1 (e.g. 1, 10, 40).');
            }
            rule[field.key] = String(intVal);
          } else if (field.key === 'tareWeightKg') {
            if (!value) value = '0.050';
            const num = Number(value);
            if (isNaN(num) || num < 0) {
              throw new Error('Packaging weight (kg) must be a non-negative number.');
            }
            rule[field.key] = num.toFixed(3);
          } else {
            if (!value) {
              throw new Error(`Enter measured shipping package: ${field.label}.`);
            }
            const num = Number(value);
            if (isNaN(num) || num <= 0) {
              throw new Error(`${field.label} must be a positive number greater than 0.`);
            }
            rule[field.key] = num.toFixed(field.key === 'unitWeightKg' ? 3 : 1);
          }
        }
      }

      // Map frontend fields to strict snake_case backend keys: [a-z][a-z0-9_]{0,79}
      const specFieldMap: Record<string, string> = {
        warranty: 'warranty',
        connectors: 'connectors',
        powerRating: 'power_rating',
        compatibility: 'compatibility',
        buildMaterial: 'build_material',
        certification: 'certification',
        importer: 'importer',
        packaging: 'packaging',
        cartonSpecs: 'carton_specs',
        warehouseStatus: 'warehouse_status',
        dispatchTurnaround: 'dispatch_turnaround',
        nationwideShipping: 'nationwide_shipping',
        minWholesaleOrder: 'min_wholesale_order',
        features: 'features',
        overview: 'overview',
        highlights: 'highlights',
        priceSubtitle: 'price_subtitle'
      };

      const arrayFields = new Set(['packaging', 'carton_specs', 'features', 'highlights']);
      const specifications: Record<string, any> = {};

      for (const [prop, backendKey] of Object.entries(specFieldMap)) {
        const val = this.product[prop];
        if (val !== undefined && val !== null) {
          if (arrayFields.has(backendKey)) {
            const lines = (Array.isArray(val) ? val : String(val).split('\n'))
              .map(s => String(s).replace(/[\x00-\x1f\x7f-\x9f]/g, '').trim())
              .filter(Boolean)
              .slice(0, 20);
            if (lines.length > 0) {
              specifications[backendKey] = lines;
            }
          } else {
            const clean = String(val).replace(/[\x00-\x1f\x7f-\x9f]/g, ' ').trim().slice(0, 1000);
            if (clean) {
              specifications[backendKey] = clean;
            }
          }
        }
      }

      // Selling prices belong to price books, never descriptive specifications.
      delete specifications['starting_price'];
      delete specifications['wholesale_rate'];
      delete specifications['distributor_rate'];

      for (const spec of this.customSpecs) {
        const safeKey = (spec.key || '')
          .trim()
          .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
          .toLowerCase()
          .replace(/[^a-z0-9_]/g, '_')
          .slice(0, 80);
        if (safeKey.match(/^[a-z][a-z0-9_]{0,79}$/) && spec.value !== undefined && spec.value !== null) {
          const cleanVal = String(spec.value).replace(/[\x00-\x1f\x7f-\x9f]/g, ' ').trim().slice(0, 1000);
          if (cleanVal) specifications[safeKey] = cleanVal;
        }
      }

      payload = {
        ...this.product,
        divisionCode: this.product.divisionCode || 'SYNTHETIC_ELECTRONICS',
        description: this.product.desc,
        specifications,
        carrierPackaging: rule
      };

      const desiredStatus = this.product.status || 'Active';
      if(desiredStatus==='Archived' && this.originalState!=='ARCHIVED' && !this.canPublishProduct) throw new Error('Archiving requires Content Publisher access.');
      if(desiredStatus==='Active' && this.originalState!=='ACTIVE' && !this.admin.session.hasRole('CONTENT_PUBLISHER')) throw new Error('Publishing requires Content Publisher access. Save this product as Draft.');

      let saved = await firstValueFrom(
        this.isNew() ? this.admin.createProduct(payload) : this.admin.updateProduct(this.product.id, payload)
      );
      productSaved = true;
      this.product.id = saved.id;
      this.product.version = saved.version;
      this.product.skuId = saved.skus?.[0]?.id || this.product.skuId;
      this.isNew.set(false);

      if (this.skuConfig && rule && JSON.stringify(rule) !== this.originalPackaging) {
        await firstValueFrom(
          this.http.patch<any>(
            '/api/v1/staff/skus/' + this.skuConfig.id,
            { carrierPackaging: rule },
            { headers: new HttpHeaders({ 'If-Match': '"' + this.skuConfig.version + '"', 'Idempotency-Key': crypto.randomUUID() }) }
          )
        );
      }

      if (desiredStatus === 'Active' && saved.status !== 'ACTIVE') {
        const hasImages = this.activeImageCount() > 0 || (saved.assets && saved.assets.length > 0);
        if (!hasImages && this.imageRequired()) {
          throw new Error('A product must have at least one uploaded image before it can be published in Nigeria. Please upload a primary image in Slot 1, or change Catalog Status to "Draft" to save without publishing.');
        }
        saved = await firstValueFrom(this.admin.publishProduct(saved.id, 'PUBLISH'));
      } else if (desiredStatus === 'Archived' && saved.status !== 'ARCHIVED') {
        saved = await firstValueFrom(this.admin.publishProduct(saved.id, 'ARCHIVE'));
      }

      this.product.version = saved.version;
      this.originalState = saved.status;
      if (rule) this.originalPackaging = JSON.stringify(rule);

      if (requestedRetail !== null && this.canEditRetail) {
        if (!this.product.skuId) throw new Error('Save a valid SKU before setting its selling prices.');
        const view=this.commercialPrices??await this.productPrices.get(this.product.skuId);
        const desired=this.productPrices.desired(view,this.product,this.skuConfig?.packSize||'1');
        if(this.productPrices.changed(view,desired)){this.commercialPrices=await this.productPrices.save(this.product.skuId,desired);retailApplied=true;this.originalRetail=requestedRetail;}
      }

      const fresh = await firstValueFrom(this.http.get<any>('/api/v1/staff/products/' + this.product.id));
      this.populateProduct(fresh);
      if (this.product.skuId) await this.loadRetailPrice(this.product.skuId);
      this.product.version = fresh.version;
      this.originalState = fresh.status;
      if (this.product.skuId) {
        this.skuConfig = await firstValueFrom(this.http.get<any>('/api/v1/staff/skus/' + this.product.skuId));
      }
      this.saveSuccess.set(true);
      this.admin.loadStaffProducts().subscribe();
      this.cdr.markForCheck();
    } catch (error: any) {
      if (payload?.id) {
        this.product.id = payload.id;
        this.product.version = payload.version ?? this.product.version;
        this.isNew.set(false);
      }
      this.uploadError.set((productSaved ? (retailApplied ? 'Product details and selling prices were saved, but refresh failed. ' : 'Product details were saved; the price update was not completed. ') : '') + this.admin.message(error));
      this.cdr.markForCheck();
    } finally {
      this.isSaving.set(false);
      this.cdr.markForCheck();
    }
  }
}
