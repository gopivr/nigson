import { Injectable, inject, signal, computed, effect } from "@angular/core";
import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Observable, of, tap, catchError, switchMap, map, forkJoin, finalize, shareReplay } from "rxjs";
import {stockFromSku} from './stock-status.component';
import { SessionService } from "./session.service";

export interface CartLineItem {
  stock?:number;stockStatus?:string;
  lineId: string;
  skuId: string;
  productId?: string;
  sku: string;
  name: string;
  unitPriceMinorUnits: number;
  quantity: number;
  minimumQuantity?: number;
  packMultiple?: number;
  purchaseError?: string;
  imageUrl?: string;
  categoryName?: string;
}

export interface CartSummary {
  cartId: string;
  items: CartLineItem[];
  subtotalMinorUnits: number;
  currency: string;
  marketId: string;
  version: number;
}

interface BackendLine {
  id: string;
  skuId: string;
  quantity: string | number;
  sku?: string;
  name?: string;
  unitPriceMinorUnits?: number;
  imageUrl?: string;
  categoryName?: string;
}

interface BackendCart {
  id: string;
  marketId: string;
  organizationId: string | null;
  state: string;
  lines: BackendLine[];
  version: number;
  lastActivityAt: string;
  userId?: string;
}

export interface SkuMetadata {
  skuId: string;
  productId?: string;
  code: string;
  name: string;
  priceMinor: number;
  img: string;
  category: string;
}

@Injectable({ providedIn: 'root' })
export class CartService {
  private http = inject(HttpClient);
  private session = inject(SessionService);

  readonly items = signal<CartLineItem[]>([]);
  readonly savedItems = signal<CartLineItem[]>([]);
  readonly cartId = signal<string | null>(null);
  readonly cartVersion = signal<number>(0);
  readonly loading = signal(false);
  readonly syncing = signal(false);
  readonly pricingError = signal<string | null>(null);
  readonly reviewRequired = signal(false);
  readonly readyForCheckout = computed(() => !this.loading() && !this.syncing() && !this.pricingError() && !this.reviewRequired() && this.items().length > 0 && this.selectedItems().every(item=>!item.purchaseError));
  acknowledgePrices() { this.reviewRequired.set(false); }


  // Selective Checkout Signals
  readonly selectedLineIds = signal<Set<string>>(new Set());

  readonly itemCount = computed(() => this.items().reduce((n, i) => n + i.quantity, 0));
  readonly subtotalNaira = computed(() =>
    this.items().reduce((n, i) => n + (i.unitPriceMinorUnits * i.quantity) / 100, 0)
  );
  readonly subtotalDisplay = computed(() =>
    String.fromCharCode(8358) + this.subtotalNaira().toLocaleString("en-NG", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  );

  readonly selectedItems = computed(() => {
    const selected = this.selectedLineIds();
    return this.items().filter(i => selected.has(i.lineId || i.skuId));
  });

  readonly selectedItemCount = computed(() =>
    this.selectedItems().reduce((n, i) => n + i.quantity, 0)
  );

  readonly selectedSubtotalNaira = computed(() =>
    this.selectedItems().reduce((n, i) => n + (i.unitPriceMinorUnits * i.quantity) / 100, 0)
  );

  readonly selectedSubtotalDisplay = computed(() =>
    String.fromCharCode(8358) + this.selectedSubtotalNaira().toLocaleString("en-NG", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  );

  readonly isAllSelected = computed(() => {
    const list = this.items();
    if (!list.length) return false;
    const selected = this.selectedLineIds();
    return list.every(i => selected.has(i.lineId || i.skuId));
  });

  isSelected(lineOrSkuId: string): boolean {
    return this.selectedLineIds().has(lineOrSkuId);
  }

  toggleSelected(lineOrSkuId: string) {
    const current = new Set(this.selectedLineIds());
    if (current.has(lineOrSkuId)) {
      current.delete(lineOrSkuId);
    } else {
      current.add(lineOrSkuId);
    }
    this.selectedLineIds.set(current);
  }

  selectAll(selected: boolean) {
    if (selected) {
      const all = new Set(this.items().map(i => i.lineId || i.skuId));
      this.selectedLineIds.set(all);
    } else {
      this.selectedLineIds.set(new Set());
    }
  }

  constructor() {
    effect(() => {
      const isAuth = this.session.authenticated();
      const uid = this.session.userId();
      if (isAuth && uid) {
        this.handleUserLogin(uid);
      } else if (!isAuth) {
        this.handleUserLogout();
      }
    });
  }

  private getAuthHeaders(): HttpHeaders {
    return new HttpHeaders({ 'Content-Type': 'application/json' });
  }

  private handleUserLogin(userId: string) {
    this.loadCart().subscribe();
  }

  private handleUserLogout() {
    this.cartId.set(null);
    this.pricingError.set(null);
    this.reviewRequired.set(false);
    this.cartVersion.set(0);
    sessionStorage.removeItem('nigson_active_cart_id');
    this.items.set([]);
    this.selectedLineIds.set(new Set());
  }

  private cartLoad?: Observable<CartSummary | null>;
  private skuCache = new Map<string, SkuMetadata>();

  private normalizeImageUrl(url?: string | null): string {
    if (!url) return '/assets/hero-accessories.jpg';
    if (url.includes('cdn.synthetic.test/assets/')) {
      return url.replace(/^https?:\/\/[^\/]+\/assets\//, 'http://localhost:4566/nigson-product-images/');
    }
    if (url.startsWith('/assets/products/')) {
      return url.replace('/assets/products/', 'http://localhost:4566/nigson-product-images/products/');
    }
    return url;
  }

  private resolveSkuMetadata(input: string, fallback?: Partial<SkuMetadata>): SkuMetadata {
    const cached = this.skuCache.get(input);
    if (cached) {
      return {
        ...cached,
        name: fallback?.name || cached.name,
        priceMinor: fallback?.priceMinor ?? cached.priceMinor,
        img: fallback?.img || cached.img,
        category: fallback?.category || cached.category
      };
    }

    return {
      skuId: input,
      code: fallback?.code || input,
      name: fallback?.name || `SKU ${input.slice(0, 8)}`,
      priceMinor: fallback?.priceMinor ?? 0,
      img: fallback?.img || '/assets/hero-accessories.jpg',
      category: fallback?.category || 'General'
    };
  }

  registerSkuMetadata(meta: SkuMetadata) {
    if (!meta || !meta.skuId) return;
    this.skuCache.set(meta.skuId, meta);
    if (meta.code) this.skuCache.set(meta.code, meta);
    if (meta.code) this.skuCache.set(meta.code.toLowerCase(), meta);
    if (meta.productId) this.skuCache.set(meta.productId, meta);
  }

  private ensureSkuMetadataLoaded(lines: BackendLine[]): Observable<void> {
    const missing = (lines || []).some(line => !this.skuCache.has(line.skuId));
    if (!missing) return of(undefined);
    return this.http.get<any>('/api/v1/catalogue/products?market=NG&page=0&size=100').pipe(
      switchMap(res => {
        const rawItems: any[] = res?.items || res?.content || [];
        if (!rawItems.length) return of([]);
        return forkJoin(rawItems.map(item =>
          this.http.get<any>(`/api/v1/catalogue/products/${item.id}?market=NG`).pipe(catchError(() => of(null)))
        ));
      }),
      tap(details => {
        for (const d of (details || [])) {
          if (!d) continue;
          const rawImg = d.assets?.[0]?.url || d.imageUrl;
          const img = this.normalizeImageUrl(rawImg);
          const category = d.categories?.[0]?.name || 'General';
          for (const sku of (d.skus || [])) {
            this.registerSkuMetadata({
              skuId: sku.id,
              productId: d.id,
              code: sku.code,
              name: d.name,
              priceMinor: 0,
              img,
              category
            });
          }
        }
      }),
      map(() => undefined),
      catchError(() => of(undefined))
    );
  }

  ensureCart(): Observable<string | null> {
    if (!this.session.authenticated()) return of(null);
    return this.createNewCart();
  }

  private createNewCart(): Observable<string | null> {
    const headers = this.getAuthHeaders().set('Idempotency-Key', crypto.randomUUID());
    return this.http.get<Array<{ id: string; code: string; currency: string }>>('/api/v1/markets').pipe(
      map(markets => markets.find(market => market.code === 'NG' && market.currency === 'NGN')?.id || null),
      switchMap(marketId => marketId ? this.http.post<BackendCart>('/api/v1/carts', { marketId }, { headers }) : of(null)),
      switchMap(cart => cart ? of(cart) : of(null)),
      tap(cart => {
        if (!cart) return;
        this.cartId.set(cart.id);
        this.cartVersion.set(cart.version);
        sessionStorage.setItem('nigson_active_cart_id', cart.id);
        const previous = this.items();
        this.items.set(this.mapLines(cart.lines).map(line => {
          const old = previous.find(item => item.skuId === line.skuId);
          return old ? {...line, unitPriceMinorUnits: old.unitPriceMinorUnits, minimumQuantity: old.minimumQuantity, packMultiple: old.packMultiple, purchaseError: old.purchaseError} : line;
        }));
      }),
      map(cart => cart?.id ?? null),
      catchError(err => {
        this.pricingError.set('Purchasing access could not be confirmed. Your partner access may be suspended or need review.');
        console.warn('[CartService] Java Commerce cart creation failed:', err);
        return of(null);
      })
    );
  }

  loadCart(): Observable<CartSummary | null> {
    if (!this.session.authenticated()) {
      this.items.set([]);
      this.cartId.set(null);
      return of(null);
    }
    if (this.cartLoad) return this.cartLoad;
    this.loading.set(true);
    this.pricingError.set(null);
    const request = this.ensureCart().pipe(
      switchMap(cartId => {
        if (!cartId) {
          this.loading.set(false);
          return of(null);
        }
        const headers = this.getAuthHeaders();
        return this.http.get<BackendCart>(`/api/v1/carts/${cartId}`, { headers }).pipe(
          switchMap(cart => {
            this.cartId.set(cart.id);
            this.cartVersion.set(cart.version);
            return this.ensureSkuMetadataLoaded(cart.lines).pipe(
              switchMap(() => this.loadLinePrices(this.mapLines(cart.lines))),
              map(mapped => {
                this.items.set(mapped);this.pricingError.set(null);
                this.loading.set(false);
                this.syncing.set(false);
                return {
                  cartId: cart.id,
                  items: mapped,
                  subtotalMinorUnits: mapped.reduce((sum, line) => sum + line.unitPriceMinorUnits * line.quantity, 0),
                  currency: 'NGN',
                  marketId: cart.marketId,
                  version: cart.version
                };
              })
            );
          }),
          catchError(err => {
            this.pricingError.set('Current prices could not be confirmed. Refresh your cart before checkout.');
            console.warn('[CartService] loadCart database error:', err);
            this.loading.set(false);
            return of(null);
          })
        );
      }),
      finalize(() => { this.cartLoad = undefined; }),
      shareReplay({bufferSize: 1, refCount: false})
    );
    this.cartLoad = request;
    return request;
  }

  addItem(skuId: string, quantity = 1, details?: { sku?: string; productId?: string; name?: string; category?: string; priceMinor?: number; image?: string }): Observable<CartSummary | null> {
    if (!this.session.authenticated()) {
      this.session.signIn(window.location.pathname);
      return of(null);
    }

    // Persist rich metadata for this SKU
    if (details) {
      const meta: SkuMetadata = {
        skuId,
        productId: details.productId,
        code: details.sku || skuId,
        name: details.name || `SKU ${skuId.slice(0, 8)}`,
        priceMinor: details.priceMinor || 0,
        img: details.image || '/assets/hero-accessories.jpg',
        category: details.category || 'General'
      };
      this.skuCache.set(skuId, meta);
      if (details.sku) this.skuCache.set(details.sku, meta);
      if (details.productId) this.skuCache.set(details.productId, meta);
    }

    this.syncing.set(true);
    return this.ensureCart().pipe(
      switchMap(cartId => {
        if (!cartId) {
          this.syncing.set(false);
          return of(null);
        }
        const currentLine = this.items().find(i => i.skuId === skuId || i.sku === skuId);
        const targetQty = (currentLine?.quantity || 0) + quantity;

        const headers = this.getAuthHeaders()
          .set('If-Match', `"${this.cartVersion()}"`)
          .set('Idempotency-Key', crypto.randomUUID());

        return this.http.put<BackendCart>(
          `/api/v1/carts/${cartId}/lines/${skuId}`,
          { quantity: String(targetQty) },
          { headers }
        ).pipe(
          switchMap(cart => this.loadLinePrices(this.mapLines(cart.lines)).pipe(map(mapped => {
            this.cartId.set(cart.id);
            this.cartVersion.set(cart.version);
            this.items.set(mapped);this.pricingError.set(null);
            this.syncing.set(false);
            return {
              cartId: cart.id,
              items: mapped,
              subtotalMinorUnits: mapped.reduce((sum, line) => sum + line.unitPriceMinorUnits * line.quantity, 0),
              currency: 'NGN',
              marketId: cart.marketId,
              version: cart.version
            };
          }))),
          catchError(err => {
              console.error('[CartService] addItem database sync error:', err);
            this.syncing.set(false);
              return this.loadCart().pipe(tap(()=>this.pricingError.set('Stock or purchasing rules may have changed. Review your refreshed cart and quantity.')),map(()=>null));
          })
        );
      })
    );
  }

  updateQty(lineIdOrSku: string, quantity: number): Observable<CartSummary | null> {
    if (!this.session.authenticated() || !this.cartId()) {
      return of(null);
    }
    if (quantity <= 0) {
      return this.removeItem(lineIdOrSku);
    }
    const it = this.items().find(i =>
      i.lineId === lineIdOrSku ||
      i.skuId === lineIdOrSku ||
      i.sku === lineIdOrSku ||
      (i.sku && lineIdOrSku && i.sku.toLowerCase() === lineIdOrSku.toLowerCase())
    );
    if (!it) {
      console.warn('[CartService] updateQty target item not found in cart for:', lineIdOrSku);
      return of(null);
    }

    const prevItems = this.items();
    // Optimistic UI update so quantity stepper feels instant and responsive
    this.items.set(prevItems.map(item => item === it ? { ...item, quantity } : item));

    const targetSkuId = it.skuId;
    this.syncing.set(true);
    const headers = this.getAuthHeaders()
      .set('If-Match', `"${this.cartVersion()}"`)
      .set('Idempotency-Key', crypto.randomUUID());

    return this.http.put<BackendCart>(
      `/api/v1/carts/${this.cartId()}/lines/${targetSkuId}`,
      { quantity: String(quantity) },
      { headers }
    ).pipe(
      switchMap(cart => this.loadLinePrices(this.mapLines(cart.lines)).pipe(map(mapped => {
        this.cartVersion.set(cart.version);
        this.items.set(mapped);this.pricingError.set(null);
        this.syncing.set(false);
        return {
          cartId: cart.id,
          items: mapped,
          subtotalMinorUnits: mapped.reduce((sum, line) => sum + line.unitPriceMinorUnits * line.quantity, 0),
          currency: 'NGN',
          marketId: cart.marketId,
          version: cart.version
        };
      }))),
      catchError(err => {
        console.error('[CartService] updateQty database error:', err);
        this.items.set(prevItems);
        this.syncing.set(false);
        return this.loadCart().pipe(tap(()=>this.pricingError.set('Stock or purchasing rules may have changed. Review your refreshed cart and quantity.')),map(()=>null));
      })
    );
  }

  removeItem(lineOrSkuId: string): Observable<CartSummary | null> {
    if (!this.session.authenticated() || !this.cartId()) {
      return of(null);
    }
    const currentItems = this.items();
    const it = currentItems.find(i =>
      i.lineId === lineOrSkuId ||
      i.skuId === lineOrSkuId ||
      i.sku === lineOrSkuId ||
      (i.sku && lineOrSkuId && i.sku.toLowerCase() === lineOrSkuId.toLowerCase())
    );
    if (!it) return of(null);

    // Optimistic UI update
    this.items.set(currentItems.filter(item => item !== it));

    const targetSkuId = it.skuId;
    this.syncing.set(true);
    const headers = this.getAuthHeaders()
      .set('If-Match', `"${this.cartVersion()}"`)
      .set('Idempotency-Key', crypto.randomUUID());

    return this.http.delete<void>(
      `/api/v1/carts/${this.cartId()}/lines/${targetSkuId}`,
      { headers, observe: 'response' }
    ).pipe(
      switchMap(() => this.loadCart()),
      catchError(err => {
        console.warn('[CartService] removeItem database error:', err);
        this.items.set(currentItems);
        this.syncing.set(false);
        return of(null);
      })
    );
  }

  clearCart(): Observable<void> {
    this.items.set([]);
    this.cartId.set(null);
    this.pricingError.set(null);
    this.reviewRequired.set(false);
    this.cartVersion.set(0);
    sessionStorage.removeItem('nigson_active_cart_id');
    return of(undefined);
  }

  saveForLater(lineId: string): Observable<unknown> {
    const it = this.items().find(i => i.lineId === lineId);
    if (it) {
      this.savedItems.update(s => [...s, it]);
      return this.removeItem(lineId);
    }
    return of(null);
  }

  loadSaved(): Observable<CartLineItem[]> {
    return of(this.savedItems());
  }

  moveToCart(skuId: string): Observable<CartSummary | null> {
    const it = this.savedItems().find(s => s.skuId === skuId || s.sku === skuId);
    if (it) {
      this.savedItems.update(s => s.filter(x => x.skuId !== it.skuId));
      return this.addItem(it.skuId, it.quantity);
    }
    return of(null);
  }

  lineForSku(skuId: string): CartLineItem | undefined {
    return this.items().find(i => i.skuId === skuId || i.sku === skuId);
  }

  formatPrice(minorUnits: number): string {
    return String.fromCharCode(8358) + (minorUnits / 100).toLocaleString("en-NG", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  private loadLinePrices(lines: CartLineItem[]): Observable<CartLineItem[]> {
    if (!lines.length) return of([]);
    const previous = this.items();
    return forkJoin(lines.map(line => this.http.get<any>(
      `/api/v1/catalogue/skus/${encodeURIComponent(line.skuId)}/price?market=NG&quantity=${line.quantity}&preview=true`
    ).pipe(
      map(price => {
        const amount = Math.round(Number(price.unitAmount) * 100);
        const minimum = Number(price.minimumQuantity), pack = Number(price.packMultiple);
        if (!Number.isSafeInteger(amount) || amount < 0 || !Number.isFinite(minimum) || !Number.isFinite(pack) || pack <= 0) throw new Error('Invalid current price');
        const old = previous.find(i => i.skuId === line.skuId);
        if (old && (old.unitPriceMinorUnits !== amount || old.minimumQuantity !== minimum || old.packMultiple !== pack)) this.reviewRequired.set(true);
        const valid = line.quantity >= minimum && Math.abs(line.quantity / pack - Math.round(line.quantity / pack)) < 0.000001;
        return { ...line, unitPriceMinorUnits: amount, minimumQuantity: minimum, packMultiple: pack, purchaseError: valid ? undefined : `Minimum ${minimum} units, in multiples of ${pack}. Update the quantity before checkout.` };
      }),
      switchMap(priced=>line.productId?this.http.get<any>('/api/v1/catalogue/products/'+line.productId+'?market=NG').pipe(map(product=>{const stock=stockFromSku(product.skus?.find((sku:any)=>sku.id===line.skuId));const error=stock.stockStatus==='UNKNOWN'?'Availability could not be confirmed. Refresh before checkout.':line.quantity>stock.stock?'The requested quantity is no longer available. Reduce it or remove this item.':priced.purchaseError;return {...priced,...stock,purchaseError:error};})):of({...priced,stock:0,stockStatus:'UNKNOWN',purchaseError:'Availability could not be confirmed. Refresh before checkout.'})),
      catchError(() => {
        const old = previous.find(i => i.skuId === line.skuId);
        const fallbackPrice = 0;
        return of({ ...line, unitPriceMinorUnits: fallbackPrice, stock:0, stockStatus:'UNKNOWN', purchaseError: 'No current price is available. Availability could not be confirmed. Refresh before checkout.' });
      })
    )));
  }

  private mapLines(lines: BackendLine[]): CartLineItem[] {
    const mapped = (lines || []).map(line => {
      const meta = this.resolveSkuMetadata(line.skuId);
      return {
        lineId: line.id || line.skuId,
        skuId: line.skuId,
        productId: meta.productId,
        sku: (line as any).sku || meta.code,
        name: (line as any).name || meta.name,
        unitPriceMinorUnits: Number((line as any).unitPriceMinorUnits) || meta.priceMinor || 0,
        quantity: Number(line.quantity),
        imageUrl: (line as any).imageUrl || meta.img,
        categoryName: (line as any).categoryName || meta.category
      };
    });

    // Ensure newly loaded items are selected for checkout by default
    const currentSelected = new Set(this.selectedLineIds());
    if (currentSelected.size === 0) {
      mapped.forEach(l => currentSelected.add(l.lineId || l.skuId));
    } else {
      mapped.forEach(l => {
        // Keep item selected if it was before or if it's newly added
        const key = l.lineId || l.skuId;
        if (!currentSelected.has(key)) {
          currentSelected.add(key);
        }
      });
    }
    this.selectedLineIds.set(currentSelected);
    return mapped;
  }
}
