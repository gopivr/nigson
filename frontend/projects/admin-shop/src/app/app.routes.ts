import {staffGuard, staffSectionGuard} from 'shared-ui';
import { Routes } from '@angular/router';

export const routes: Routes = [
  {path:'inventory/products/import',canDeactivate:[(component:any)=>!component.saving()],canActivate:[staffGuard,staffSectionGuard],loadComponent:()=>import('./inventory/catalogue-import.component').then(m=>m.CatalogueImportComponent)},
  {path:'categories',canActivate:[staffGuard,staffSectionGuard],loadComponent:()=>import('./inventory/categories.component').then(m=>m.AdminCategoriesComponent)},
  {path:'affiliate-payouts',canActivate:[staffGuard,staffSectionGuard],loadComponent:()=>import('./sales/affiliate-payouts.component').then(m=>m.AdminAffiliatePayoutsComponent)},
  // Sales & CRM Routes
  {
    path: 'orders',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./sales/orders.component').then(m => m.AdminOrdersComponent)
  },
  {
    path: 'customers',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./sales/customers.component').then(m => m.AdminCustomersComponent)
  },
  {
    path: 'quotes',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./sales/quotes.component').then(m => m.AdminQuotesComponent)
  },
  {
    path: 'applications',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./sales/applications.component').then(m => m.AdminApplicationsComponent)
  },
  {
    path: 'inquiries',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./sales/inquiries.component').then(m => m.AdminInquiriesComponent)
  },
  
  // Inventory Routes
  {
    path: 'inventory',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./inventory/inventory-dashboard.component').then(m => m.InventoryDashboardComponent)
  },
  {
    path: 'inventory/products',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./inventory/product-list.component').then(m => m.ProductListComponent)
  },
  {
    path: 'inventory/products/new',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./inventory/product-edit.component').then(m => m.ProductEditComponent)
  },
  {
    path: 'inventory/products/:sku/edit',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./inventory/product-edit.component').then(m => m.ProductEditComponent)
  },
  {
    path: 'inventory/stock-in',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./inventory/stock-adjustment.component').then(m => m.StockAdjustmentComponent),
    data: { type: 'stock-in' }
  },
  {
    path: 'inventory/adjustment',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./inventory/stock-adjustment.component').then(m => m.StockAdjustmentComponent),
    data: { type: 'adjustment' }
  },
  {
    path: 'inventory/movements',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./inventory/stock-movements.component').then(m => m.StockMovementsComponent)
  },
  {
    path: 'inventory/low-stock',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./inventory/stock-report.component').then(m => m.StockReportComponent),
    data: { type: 'low-stock' }
  },
  {
    path: 'inventory/out-of-stock',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./inventory/stock-report.component').then(m => m.StockReportComponent),
    data: { type: 'out-of-stock' }
  }
];
