import { Routes } from '@angular/router';
import { AccountLayoutComponent } from './layout/account-layout.component';

export const routes: Routes = [
  {
    path: '',
    component: AccountLayoutComponent,
    children: [
      {path:'affiliate',loadComponent:()=>import('./affiliate/affiliate.component').then(m=>m.AffiliateComponent)},
      {
        path: '',
        loadComponent: () => import('./profile/profile.component').then(m => m.ProfileComponent)
      },
      {
        path: 'orders',
        loadComponent: () => import('./orders/orders-list.component').then(m => m.OrdersListComponent)
      },
      {
        path: 'orders/:id',
        loadComponent: () => import('./orders/order-detail.component').then(m => m.OrderDetailComponent)
      },
      {
        path: 'addresses',
        loadComponent: () => import('./addresses/addresses.component').then(m => m.AddressesComponent)
      },
      {
        path: 'wishlist',
        loadComponent: () => import('./wishlist/wishlist.component').then(m => m.WishlistComponent)
      },
      {
        path: 'quotes',
        loadComponent: () => import('./quotes/my-quotes.component').then(m => m.MyQuotesComponent)
      },
      {
        path: 'settings',
        loadComponent: () => import('./settings/settings.component').then(m => m.SettingsComponent)
      }
    ]
  }
];
