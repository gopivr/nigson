import { Injectable, signal, computed, Injector, inject, effect } from '@angular/core';
import { Observable, of } from 'rxjs';
import { SessionService } from './session.service';
import { CartService } from './cart.service';
import { Product } from './catalog.service';

export interface User {
  id: string;
  fullName: string;
  email: string;
  roles: string[];
  createdAt: string;
  phone?: string;
}

export interface CartItem {
  lineId?: string;
  sku: string;
  skuId?: string;
  productId?: string;
  name: string;
  category: string;
  price: number;
  image?: string;
  qty: number;
}

export interface CartToast {
  id: string;
  name: string;
  image?: string;
  price?: number;
  qty: number;
}


export interface Order {
  id: string;
  number: string;
  date: string;
  items: CartItem[];
  subtotal: number;
  shipping: number;
  tax: number;
  total: number;
  paymentMethod: string;
  status: 'Processing' | 'Shipped' | 'Delivered' | 'Cancelled';
  estimatedDelivery: string;
  customer: {
    fullName: string;
    email: string;
    phone: string;
  };
  address: {
    street: string;
    city: string;
    state: string;
    country: string;
    postal: string;
  };
  deliveryMethod: string;
}

@Injectable({
  providedIn: 'root'
})
export class ShopService {
  private cartService = inject(CartService);
  private session = inject(SessionService);
  private injector = inject(Injector);

  readonly hydrated = signal(false);
  readonly user = signal<User | null>(null);
  readonly loginRequired = signal<{ open: boolean; reason?: string }>({ open: false });
  readonly lastAddedToast = signal<CartToast | null>(null);
  private toastTimeout: any = null;

  readonly cart = computed<CartItem[]>(() => {
    return this.cartService.items().map(i => ({
      lineId: i.lineId,
      sku: i.sku || i.skuId,
      skuId: i.skuId,
      productId: i.productId,
      name: i.name,
      category: i.categoryName || 'General',
      price: i.unitPriceMinorUnits / 100,
      image: i.imageUrl,
      qty: i.quantity
    }));
  });

  readonly cartCount = computed(() => this.cart().reduce((n, i) => n + i.qty, 0));
  readonly cartSubtotal = computed(() => this.cart().reduce((n, i) => n + i.qty * i.price, 0));
  readonly isAdmin = computed(() => this.session.isAdmin());

  constructor() {
    this.init();
    const session = this.injector.get(SessionService, null, { optional: true });
    if (session && session.authenticated()) {
      this.user.set({
        id: session.userId() || '',
        fullName: session.fullName() || 'Nigson User',
        email: session.email() || '',
        roles: session.roles(),
        createdAt: new Date().toISOString()
      });
    }
    effect(() => {
      if (session && session.authenticated()) {
        this.user.set({
          id: session.userId() || '',
          fullName: session.fullName() || 'Nigson User',
          email: session.email() || '',
          roles: session.roles(),
          createdAt: new Date().toISOString()
        });
      } else {
        this.user.set(null);
      }
    });
  }

  private init() {
    this.hydrated.set(true);
  }

  openLoginRequired(reason?: string) {
    this.loginRequired.set({ open: true, reason });
  }

  closeLoginRequired() {
    this.loginRequired.set({ open: false });
  }

  updateProfile(data: any) {
    const current = this.user();
    if (!current) return;
    const updated = { ...current, ...data };
    this.user.set(updated);
  }


  signOut(): Observable<void> {
    this.user.set(null);
    try {
      const session = this.injector.get(SessionService, null, { optional: true });
      return session ? session.logout() : of(undefined);
    } catch { return of(undefined); }
  }

  showToast(item: { name: string; image?: string; price?: number; qty?: number }) {
    if (this.toastTimeout) clearTimeout(this.toastTimeout);
    this.lastAddedToast.set({
      id: Math.random().toString(36).substring(2, 9),
      name: item.name,
      image: item.image,
      price: item.price,
      qty: item.qty ?? 1
    });
    this.toastTimeout = setTimeout(() => {
      this.lastAddedToast.set(null);
    }, 4000);
  }

  dismissToast() {
    if (this.toastTimeout) clearTimeout(this.toastTimeout);
    this.lastAddedToast.set(null);
  }

  addToCart(item: Omit<CartItem, 'qty'> & { skuId?: string; productId?: string; priceMinorUnits?: number }, qty = 1): boolean {
    const available=(item as any).stock;const already=this.cart().find(line=>line.skuId===item.skuId)?.qty||0;
    if((item as any).stockStatus==='OUT_OF_STOCK'||(item as any).stockStatus==='UNKNOWN'||typeof available==='number'&&qty+already>available)return false;
    if (!this.session.authenticated()) {
      this.loginRequired.set({ open: true, reason: 'Please sign in to add products to your cart.' });
      this.session.signIn(window.location.pathname);
      return false;
    }
    const skuId = item.skuId || (item as any).id || item.sku;
    const priceMinor = item.priceMinorUnits ?? Math.round((item.price ?? 0) * 100);
    this.cartService.addItem(skuId, qty, {
      sku: item.sku,
      productId: item.productId || (item as any).id,
      name: item.name,
      category: item.category,
      priceMinor: priceMinor,
      image: item.image
    }).subscribe(cart=>{if(cart)this.showToast({ name: item.name, image: item.image, price: priceMinor / 100, qty });});
    return true;
  }

  removeFromCart(skuOrLineId: string) {
    this.cartService.removeItem(skuOrLineId).subscribe();
  }

  updateQty(skuOrLineId: string, qty: number) {
    this.cartService.updateQty(skuOrLineId, qty).subscribe();
  }

  clearCart() {
    this.cartService.clearCart().subscribe();
  }


  initialsOf(fullName: string | undefined): string {
    if (!fullName) return 'U';
    const parts = fullName.trim().split(' ');
    if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
    return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
  }
}
