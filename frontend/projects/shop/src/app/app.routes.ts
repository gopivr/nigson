import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'products',
    loadComponent: () => import('./products/product-list.component').then(m => m.ProductListComponent)
  },
  {
    path: 'products/:id',
    loadComponent: () => import('./products/product-detail.component').then(m => m.ProductDetailComponent)
  },
  {
    path: 'cart',
    loadComponent: () => import('./cart/cart.component').then(m => m.CartComponent)
  },
  {
    path: 'checkout',
    loadComponent: () => import('./checkout/checkout.component').then(m => m.CheckoutComponent)
  },
  {
    path: 'checkout/success',
    loadComponent: () => import('./checkout/order-success.component').then(m => m.OrderSuccessComponent)
  },
  {
    path: 'search',
    loadComponent: () => import('./pages/search.component').then(m => m.SearchComponent)
  },
  {
    path: 'quote',
    loadComponent: () => import('./pages/quote.component').then(m => m.QuoteComponent)
  },
  {
    path: '',
    redirectTo: 'products',
    pathMatch: 'full'
  }
];

