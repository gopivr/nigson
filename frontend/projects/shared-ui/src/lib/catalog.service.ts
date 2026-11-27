import { Injectable, inject, signal, computed, Injector } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, forkJoin, of } from 'rxjs';
import { tap, catchError, map, switchMap } from 'rxjs/operators';
import { CartService } from './cart.service';
import {stockFromSku} from './stock-status.component';

// ─── Backend DTOs ──────────────────────────────────────────────────────────────

/**
 * Product (TASK-16) — aligned to GET /api/v1/catalogue/products response.
 *
 * Key changes from old interface:
 *  - id (UUID) added as primary key — used for all API calls
 *  - price removed (comes from separate GET /api/v1/catalogue/skus/{id}/price)
 *  - originalPrice removed (not a backend concept)
 *  - desc → description (matches backend field name)
 *  - stock removed (comes from GET /api/v1/staff/inventory, not product DTO)
 */
export interface Product {
  /** UUID primary key — use for all backend API calls */
  id: string;
  /** Human-readable SKU code — use for display only */
  sku: string;
  /** SKU UUID for cart operations */
  skuId?: string;
  quoteSkus?: Array<{ id: string; code: string; variantName?: string }>;
  name: string;
  /** Category UUID — resolved to name via categories list */
  categoryId: string;
  /** Resolved category name — populated client-side after fetching categories */
  categoryName?: string;
  description: string;
  /** Technical specifications */
  specifications?: Record<string, any>;
  /** Backend-provided image URLs */
  images?: string[];
  /** Price in NGN minor units (kobo) — divide by 100 for display */
  priceMinorUnits?: number;
  minimumQuantity?: number;
  packMultiple?: number;
  price?: number;
  stock?: number;
  stockStatus?: string;
  lowLevel?: number;
  /** Publication status from backend */
  publicationStatus?: string;
  brand?: string;
  tags?: string[];
  createdAt?: string;

  // Rich editable product details matching product page
  status?: string;
  priceSubtitle?: string;
  retailPrice?: number;
  retail?: number;
  wholesalePrice?: number;
  wholesale?: number;
  distributorPrice?: number;
  distributor?: number;
  features?: string[] | string;
  overview?: string;
  overviewExtra?: string;
  highlights?: string[] | string;
  packaging?: string[] | string;
  cartonSpecs?: string[] | string;
  warehouseStatus?: string;
  dispatchTurnaround?: string;
  nationwideShipping?: string;
  minWholesaleOrder?: string;
  updatedAt?: string;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
  imageUrl?: string;
}

export interface Brand {
  id: string;
  name: string;
}

export interface PriceResponse {
  skuId: string;
  /** Price in NGN minor units (kobo) */
  priceMinorUnits: number;
  minimumQuantity: number;
  packMultiple: number;
  currency: string;
  marketId?: string;
}

export interface ProductsPage {
  content: Product[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ProductFilters {
  q?: string;
  categoryId?: string;
  brandId?: string;
  page?: number;
  size?: number;
  sort?: string;
}

// ─── Service ───────────────────────────────────────────────────────────────────

/**
 * CatalogService (TASK-15 to TASK-27)
 *
 * Replaces all hardcoded PRODUCTS/CATEGORIES arrays with real HTTP calls to:
 *   GET /api/v1/catalogue/products               — TASK-15, TASK-23
 *   GET /api/v1/catalogue/products/{id}           — TASK-21
 *   GET /api/v1/catalogue/products/{id}/recommendations — TASK-22
 *   GET /api/v1/catalogue/categories             — TASK-17
 *   GET /api/v1/catalogue/brands                 — TASK-18
 *   GET /api/v1/catalogue/skus/{id}/price        — TASK-19
 *   GET /api/v1/reviews?productId={id}           — TASK-26 (rating)
 *
 * Price display: backend returns kobo (NGN × 100). Use formatPrice() which
 * divides by 100 before display (TASK-20).
 */
export const PRODUCT_SKU_MAP: Record<string, { sku: string; skuId: string; priceMinorUnits: number; image: string }> = {
  '11111111-2222-3333-4444-555555555501': { sku: 'NG-EB-01', skuId: '11111111-2222-3333-4444-555555555511', priceMinorUnits: 2500000, image: '/assets/cat-earbuds.jpg' },
  '22222222-2222-3333-4444-555555555502': { sku: 'NG-PB-01', skuId: '22222222-2222-3333-4444-555555555512', priceMinorUnits: 3500000, image: '/assets/nigson-power.jpg' },
  '33333333-2222-3333-4444-555555555503': { sku: 'NG-CH-01', skuId: '33333333-2222-3333-4444-555555555513', priceMinorUnits: 1850000, image: '/assets/cat-charger.jpg' },
  '44444444-2222-3333-4444-555555555504': { sku: 'NG-CB-01', skuId: '44444444-2222-3333-4444-555555555514', priceMinorUnits: 550000, image: '/assets/cat-cable.jpg' },
  '55555555-2222-3333-4444-555555555505': { sku: 'NG-CC-01', skuId: '55555555-2222-3333-4444-555555555515', priceMinorUnits: 1500000, image: '/assets/shopcat/car-chargers.jpg' },
  '66666666-2222-3333-4444-555555555506': { sku: 'NG-SP-01', skuId: '66666666-2222-3333-4444-555555555516', priceMinorUnits: 1950000, image: '/assets/cat-powerbank.jpg' },
  '34c745d2-b6ad-4dab-8eda-4c295687b0c5': { sku: 'NG-GAN-100', skuId: '4eff5dd2-44ed-49fd-a715-5d3f5176f5ee', priceMinorUnits: 2800000, image: '/assets/cat-charger.jpg' },
  '3d84c1ea-b96c-45af-a665-1ef8c8cdc9dc': { sku: 'PQ28', skuId: '4886036e-574a-4bc3-aaaa-ee82af43cf1b', priceMinorUnits: 2400000, image: '/assets/cat-powerbank.jpg' }
};

export const DEFAULT_CATEGORIES: Category[] = [
  { id: '10000000-0000-0000-0000-000000000001', name: 'Earbuds', slug: 'earbuds', imageUrl: '/assets/cat-earbuds.jpg' },
  { id: '10000000-0000-0000-0000-000000000002', name: 'Power Banks', slug: 'power-banks', imageUrl: '/assets/cat-powerbank.jpg' },
  { id: '11111111-2222-3333-4444-555555555503', name: 'Cables', slug: 'cables', imageUrl: '/assets/cat-cable.jpg' },
  { id: '11111111-2222-3333-4444-555555555504', name: 'Chargers', slug: 'chargers', imageUrl: '/assets/cat-charger.jpg' },
  { id: '11111111-2222-3333-4444-555555555505', name: 'Extension Sockets', slug: 'extension-sockets', imageUrl: '/assets/cat-powerbank.jpg' }
];

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private http = inject(HttpClient);
  private injector = inject(Injector);

  private syncWithCart(products: Product[]) {
    try {
      const cartSvc = this.injector.get(CartService, null, { optional: true });
      if (cartSvc) {
        for (const p of products) {
          if (p?.skuId) {
            cartSvc.registerSkuMetadata({
              skuId: p.skuId,
              productId: p.id,
              code: p.sku,
              name: p.name,
              priceMinor: p.priceMinorUnits ?? 0,
              img: this.primaryImage(p),
              category: p.categoryName || 'General'
            });
          }
        }
      }
    } catch { }
  }

  // ─── Reactive state ──────────────────────────────────────────────────────

  readonly products = signal<Product[]>([]);
  readonly categories = signal<Category[]>(DEFAULT_CATEGORIES);
  readonly brands = signal<Brand[]>([]);
  readonly loading = signal(false);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly currentPage = signal(0);

  /** Loaded flag — true once initial products list has been fetched */
  readonly loaded = signal(false);

  /**
   * True when Commerce rejects market-scoped catalogue reads because the
   * requested market is configured but not activated (422 MARKET_RULE_VIOLATION)
   * or is unknown (404 NOT_FOUND). Views show an explicit "catalogue not
   * available" state instead of a generic error or a misleading empty result.
   */
  readonly catalogueUnavailable = signal(false);

  private marketUnavailable(error: unknown): boolean {
    if (!(error instanceof HttpErrorResponse)) return false;
    const code = (error.error as any)?.code;
    return (error.status === 422 && code === 'MARKET_RULE_VIOLATION') ||
      (error.status === 404 && code === 'NOT_FOUND');
  }

  // ─── Static fallback image map (for display while backend images load) ────

  readonly CAT_IMG: Record<string, string> = {
    'Power Banks': '/assets/cat-powerbank.jpg',
    'Earbuds': '/assets/cat-earbuds.jpg',
    'Cables': '/assets/cat-cable.jpg',
    'Chargers': '/assets/cat-charger.jpg',
    'Extension Sockets': '/assets/cat-powerbank.jpg',
    'Motion-Activated Lights': '/assets/hero-accessories.jpg',
    'Wireless Chargers': '/assets/cat-charger.jpg',
    'Car Chargers': '/assets/cat-charger.jpg',
    'Home Chargers': '/assets/cat-charger.jpg',
    'Adapters': '/assets/cat-charger.jpg',
    'Power Strips': '/assets/cat-powerbank.jpg',
    'Lighting': '/assets/hero-accessories.jpg',
    'FMCG': '/assets/hero-accessories.jpg',
  };
  readonly FALLBACK_IMG = '/assets/hero-accessories.jpg';

  resolveImageUrl(url: string | null | undefined): string {
    if (!url) return this.FALLBACK_IMG;
    if (url.includes('cdn.synthetic.test/assets/')) {
      return url.replace(/^https?:\/\/[^\/]+\/assets\//, 'http://localhost:4566/nigson-product-images/');
    }
    if (url.startsWith('/assets/products/')) {
      return url.replace('/assets/products/', 'http://localhost:4566/nigson-product-images/products/');
    }
    return url;
  }

  // ─── Local Overrides / Synchronized Edits ────────────────────────────────

  saveProductOverride(idOrSku: string, override: Partial<Product>): void {
    const merged = { ...override, updatedAt: new Date().toISOString() };
    this.products.update(all => all.map(p =>
      (p.id === idOrSku || p.sku === idOrSku || (merged.id && p.id === merged.id) || (merged.sku && p.sku === merged.sku))
        ? { ...p, ...merged }
        : p
    ));
  }

  removeProductOverride(idOrSku: string): void {
    this.products.update(all => all.filter(p => p.id !== idOrSku && p.sku !== idOrSku));
  }

  getProductOverride(_idOrSku: string): Partial<Product> | null { return null; }

  // ─── Products ─────────────────────────────────────────────────────────────

  /**
   * TASK-15, TASK-23: Load products from backend with optional filters.
   * Results stored in `products` signal and enriched with real SKUs, prices, categories, and overrides.
   */
  loadProducts(filters: ProductFilters = {}): Observable<ProductsPage> {
    this.loading.set(true);
    let params = new HttpParams().set('market', 'NG');
    if (filters.q) params = params.set('q', filters.q);
    if (filters.categoryId) params = params.set('categoryId', filters.categoryId);
    if (filters.brandId) params = params.set('brandId', filters.brandId);
    if (filters.page != null) params = params.set('page', String(filters.page));
    if (filters.size != null) params = params.set('size', String(filters.size));
    if (filters.sort) params = params.set('sort', filters.sort);

    return this.http.get<any>('/api/v1/catalogue/products', { params }).pipe(
      switchMap(res => this.withProductDetails(res)),
      tap(page => {
        this.catalogueUnavailable.set(false);
        const enriched = this.enrichWithCategories(page.content);
        this.products.set(enriched);
        this.syncWithCart(enriched);
        this.totalElements.set(page.totalElements);
        this.totalPages.set(page.totalPages);
        this.currentPage.set(page.number);
        this.loading.set(false);
        this.loaded.set(true);
      }),
      catchError(err => {
        if (this.marketUnavailable(err)) {
          this.catalogueUnavailable.set(true);
        } else {
          console.error('[CatalogService] Backend /api/v1/catalogue/products failed:', err);
        }
        this.products.set([]);
        this.totalElements.set(0);
        this.totalPages.set(0);
        this.currentPage.set(0);
        this.loading.set(false);
        this.loaded.set(true);
        return of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
      })
    );
  }

  /**
   * The public list projection is intentionally minimal, so enrich each card
   * from the authoritative product read: real SKU ids (for the price call),
   * real category ids/names and published asset URLs. No client-side guessing.
   */
  private withProductDetails(res: any): Observable<ProductsPage> {
    const rawItems: any[] = res?.items || res?.content || [];
    if (!rawItems.length) {
      return of({
        content: [],
        totalElements: res?.totalElements ?? 0,
        totalPages: res?.totalPages ?? 0,
        number: res?.page ?? 0,
        size: res?.size ?? 20
      });
    }
    const requests = rawItems.map(item =>
      this.http.get<any>(`/api/v1/catalogue/products/${item.id}?market=NG`).pipe(
        map(detail => this.toProduct(item, detail)),
        catchError(() => of(this.toProduct(item, null)))
      )
    );
    return forkJoin(requests).pipe(switchMap(products => forkJoin(products.map(product => this.withSellingPrice(product)))), map(content => ({
      content,
      totalElements: res?.totalElements ?? content.length,
      totalPages: res?.totalPages ?? 1,
      number: res?.page ?? 0,
      size: res?.size ?? 20
    })));
  }

  private toProduct(item: any, detail: any): Product {
    const sku = detail?.skus?.find((s:any)=>s.available) || detail?.skus?.[0];
    const category = detail?.categories?.[0];
    const override = this.getProductOverride(item.id) || (sku?.code ? this.getProductOverride(sku.code) : null);
    const assetImages: string[] = (detail?.assets ?? []).map((a: any) => this.resolveImageUrl(a.url)).filter(Boolean);
    const primaryImage = item.imageUrl ? this.resolveImageUrl(item.imageUrl) : this.FALLBACK_IMG;
    const specs = detail?.specifications || {};

    const product: Product = {
      id: item.id,
      sku: sku?.code || '',
      skuId: sku?.id,
      quoteSkus: (detail?.skus ?? [])
        .filter((row: any) => row.available)
        .map((row: any) => ({ id: row.id, code: row.code, variantName: row.variantName || undefined })),
      name: item.name,
      categoryId: category?.id || '',
      categoryName: category?.name || '',
      description: (detail?.description || item.name || '').trim(),
      images: assetImages.length ? assetImages : [primaryImage],
      publicationStatus: item.available ? 'PUBLISHED' : 'UNAVAILABLE',
      brand: detail?.brand?.name || item.brandName || '',
      tags: [],
      price: undefined,
      priceMinorUnits: undefined
    };
    return { ...(override ? { ...product, ...override } : product), ...stockFromSku(sku), price: undefined, priceMinorUnits: undefined, retail: undefined, retailPrice: undefined };
  }

  /**
   * TASK-21: Load single product by UUID or SKU.
   * Resolves UUID, fetches backend record with all images/specifications, and merges overrides.
   */
  getProduct(idOrSku: string): Observable<Product> {
    const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(idOrSku);
    if (!isUuid) {
      // Find UUID by SKU or slug from loaded products
      const found = this.products().find(p =>
        p.sku?.toUpperCase() === idOrSku.toUpperCase() ||
        p.id === idOrSku ||
        p.name?.toUpperCase().startsWith(idOrSku.toUpperCase())
      );
      if (found?.id && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(found.id)) {
        return this.getProductByUuid(found.id, idOrSku);
      }
      return this.loadProducts({ size: 100 }).pipe(
        switchMap(page => {
          const match = page.content.find(p =>
            p.sku?.toUpperCase() === idOrSku.toUpperCase() ||
            p.id === idOrSku ||
            p.name?.toUpperCase().startsWith(idOrSku.toUpperCase())
          );
          const targetId = match?.id || idOrSku;
          return this.getProductByUuid(targetId, idOrSku);
        })
      );
    }
    return this.getProductByUuid(idOrSku);
  }

  private getProductByUuid(uuid: string, aliasSku?: string): Observable<Product> {
    return this.http.get<any>(`/api/v1/catalogue/products/${uuid}?market=NG`).pipe(
      map(raw => {
        const selectedSku = raw.skus?.find((s:any)=>s.available) || raw.skus?.[0];
        const sku = selectedSku?.code || '';
        const override = this.getProductOverride(raw.id) || (sku ? this.getProductOverride(sku) : null) || (aliasSku ? this.getProductOverride(aliasSku) : null);
        const catName = raw.categories?.[0]?.name || '';


        // Map ALL backend assets into images array
        const backendImages: string[] = raw.assets?.length
          ? raw.assets.map((a: any) => this.resolveImageUrl(a.url)).filter(Boolean)
          : (raw.imageUrl ? [this.resolveImageUrl(raw.imageUrl)] : [this.FALLBACK_IMG]);

        // Merge images: keep any customized override images, and ensure all backend S3 assets are included
        const mergedImages: string[] = [];
        if (override?.images?.length) {
          for (const img of override.images) {
            if (img && typeof img === 'string' && img.trim() && !mergedImages.includes(img.trim())) {
              mergedImages.push(img.trim());
            }
          }
        }
        for (const bImg of backendImages) {
          if (bImg && !mergedImages.includes(bImg)) {
            mergedImages.push(bImg);
          }
        }
        if (mergedImages.length === 0) {
          mergedImages.push(this.FALLBACK_IMG);
        }

        const specs = raw.specifications || {};


        const p: Product = {
          id: raw.id,
          sku: override?.sku || sku,
          skuId: selectedSku?.id || raw.id,
          quoteSkus: (raw.skus || []).filter((s: any) => s.available).map((s: any) => ({
            id: s.id,
            code: s.code,
            variantName: s.variantName || undefined
          })),
          name: override?.name || raw.name,
          categoryId: raw.categories?.[0]?.id || catName,
          categoryName: override?.categoryName || catName,
          description: (override?.description || raw.description || '').replace(/\s*<!--\[price:.*?\]-->/g, '').trim(),
          publicationStatus: 'PUBLISHED',
          brand: override?.brand || raw.brand?.name || 'Nigson',
          ...override,
          specifications: {
            ...(raw.specifications || {}),
            ...specs,
            ...(override?.specifications || {})
          },
          overview: specs['overview'] || override?.overview,
          highlights: specs['highlights'] || override?.highlights,
          features: specs['features'] || override?.features,
          cartonSpecs: specs['carton_specs'] || specs['cartonSpecs'] || override?.cartonSpecs,
          packaging: specs['packaging'] || override?.packaging,
          warehouseStatus: specs['warehouse_status'] || specs['warehouseStatus'] || override?.warehouseStatus,
          dispatchTurnaround: specs['dispatch_turnaround'] || specs['dispatchTurnaround'] || override?.dispatchTurnaround,
          nationwideShipping: specs['nationwide_shipping'] || specs['nationwideShipping'] || override?.nationwideShipping,
          minWholesaleOrder: specs['min_wholesale_order'] || specs['minWholesaleOrder'] || override?.minWholesaleOrder,
          priceSubtitle: specs['price_subtitle'] || specs['priceSubtitle'] || override?.priceSubtitle,
          retail: undefined,
          retailPrice: undefined,
          wholesale: undefined,
          wholesalePrice: undefined,
          distributor: undefined,
          distributorPrice: undefined,
          images: mergedImages,
          priceMinorUnits: undefined,
          price: undefined
        };
        Object.assign(p,stockFromSku(selectedSku));
        const finalP = this.enrichWithCategories([p])[0];
        return finalP;
      }),
      switchMap(product => this.withSellingPrice(product)),
      tap(product => this.syncWithCart([product])),
      catchError(err => {
        const override = this.getProductOverride(uuid) || (aliasSku ? this.getProductOverride(aliasSku) : null);
        if (override) {
          return this.withSellingPrice({...override,...stockFromSku(undefined)} as Product);
        }
        console.error('[CatalogService] getProduct failed:', err);
        throw err;
      })
    );
  }

  /**
   * TASK-22: Recommended products for a given product UUID.
   */
  getRecommended(id: string): Observable<Product[]> {
    return this.http.get<any[]>(`/api/v1/catalogue/products/${id}/recommendations?market=NG`).pipe(
      map(list => this.enrichWithCategories(list || [])),
      catchError(() => of([]))
    );
  }

  // ─── Categories (TASK-17) ─────────────────────────────────────────────────

  loadCategories(): Observable<Category[]> {
    return this.http.get<any[]>('/api/v1/catalogue/categories?market=NG').pipe(
      map(cats => (cats || []).map(c => ({
        id: c.id,
        name: c.name,
        slug: c.slug,
        imageUrl: this.CAT_IMG[c.name] || this.FALLBACK_IMG
      }))),
      tap(cats => { this.catalogueUnavailable.set(false); this.categories.set(cats); }),
      catchError(err => {
        if (this.marketUnavailable(err)) {
          this.catalogueUnavailable.set(true);
        } else {
          console.error('[CatalogService] loadCategories failed:', err);
        }
        return of([]);
      })
    );
  }

  // ─── Brands (TASK-18) ─────────────────────────────────────────────────────

  loadBrands(): Observable<Brand[]> {
    return this.http.get<Brand[]>('/api/v1/catalogue/brands').pipe(
      tap(brands => this.brands.set(brands)),
      catchError(err => {
        if (this.marketUnavailable(err)) this.catalogueUnavailable.set(true);
        return of([]);
      })
    );
  }

  // ─── Pricing (TASK-19, TASK-20) ──────────────────────────────────────────

  /**
   * Load price for a SKU UUID. Returns price in minor units (kobo).
   * Use formatPrice() to convert for display.
   */
  private withSellingPrice(product: Product): Observable<Product> {
    const unpriced = { ...product, price: undefined, priceMinorUnits: undefined, retail: undefined, retailPrice: undefined, wholesale: undefined, wholesalePrice: undefined, distributor: undefined, distributorPrice: undefined };
    if (!product.skuId) return of(unpriced);
    return this.getPrice(product.skuId).pipe(
      map(price => ({ ...unpriced, priceMinorUnits: price.priceMinorUnits, minimumQuantity: price.minimumQuantity, packMultiple: price.packMultiple, price: price.priceMinorUnits / 100, retail: price.priceMinorUnits / 100, retailPrice: price.priceMinorUnits / 100 })),
      catchError(() => of(unpriced))
    );
  }

  getPrice(skuId: string, quantity = 1): Observable<PriceResponse> {
    return this.http.get<any>(`/api/v1/catalogue/skus/${skuId}/price?market=NG&quantity=${quantity}&preview=true`).pipe(
      map(res => ({ skuId: res.skuId, priceMinorUnits: Math.round(Number(res.unitAmount) * 100), currency: res.currency, minimumQuantity: Number(res.minimumQuantity), packMultiple: Number(res.packMultiple) }))
    );
  }

  /**
   * TASK-20: Format price from NGN minor units (kobo) to display string.
   * Backend always returns kobo — divide by 100 before showing.
   *
   * Examples:
   *   formatPrice(2850000) → '₦28,500.00'
   *   formatPrice(450000)  → '₦4,500.00'
   */
  formatPrice(minorUnits: number): string {
    const naira = minorUnits / 100;
    return '₦' + naira.toLocaleString('en-NG', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  /** Format price from whole Naira (legacy local data) — for transition period only */
  formatPriceNaira(naira: number): string {
    return '₦' + naira.toLocaleString('en-NG', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  // ─── Image helpers ────────────────────────────────────────────────────────

  imageFor(categoryName: string | undefined | null): string {
    if (!categoryName) return this.FALLBACK_IMG;
    return this.CAT_IMG[categoryName] ?? this.FALLBACK_IMG;
  }

  primaryImage(product: Product): string {
    return product.images?.[0] ?? this.imageFor(product.categoryName ?? '');
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  /** Enrich products with resolved category name from loaded categories signal */
  private enrichWithCategories(products: Product[]): Product[] {
    const cats = this.categories();
    if (!cats.length) return products;
    const catMap = new Map(cats.map(c => [c.id, c.name]));
    return products.map(p => ({
      ...p,
      categoryName: catMap.get(p.categoryId) ?? p.categoryName
    }));
  }

  /** TASK-27: Is product a "deal"? Driven by backend campaigns, not hardcoded SKUs */
  isDeal(p: Product): boolean {
    return p.tags?.includes('deal') ?? false;
  }

  /** TASK-27: Is product a new arrival? Based on createdAt date (last 30 days) */
  isNewArrival(p: Product): boolean {
    if (!p.createdAt) return false;
    const days = (Date.now() - new Date(p.createdAt).getTime()) / 86400000;
    return days <= 30;
  }

  // ─── Legacy hash (kept for non-rating use cases during transition) ─────────
  hash(s: string): number {
    let h = 0;
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
    return h;
  }

  // ─── TASK-84: Market Validation (API-029) ──────────────────────────────────
  readonly marketActive = signal<boolean>(true);
  readonly marketCurrency = signal<string>('NGN');

  /** Validate that the NG (Nigeria) market policy is active from GET /api/v1/markets */
  validateMarket(): Observable<boolean> {
    return this.http.get<any[]>('/api/v1/markets').pipe(
      map(markets => {
        const ng = (markets || []).find(m => m.code === 'NG' || m.currency === 'NGN');
        const active = !!ng;
        this.marketActive.set(active);
        if (ng?.currency) this.marketCurrency.set(ng.currency);
        return active;
      }),
      catchError(() => {
        // Fallback gracefully in development / test environments
        this.marketActive.set(true);
        return of(true);
      })
    );
  }
}
