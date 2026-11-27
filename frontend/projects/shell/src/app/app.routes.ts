import { Routes, UrlSegment } from '@angular/router';
import { loadRemoteModule } from '@angular-architects/native-federation';
import { BffCallbackComponent } from './bff-callback/bff-callback.component';
import { authGuard, adminGuard, staffGuard, staffSectionGuard } from 'shared-ui';

export function shopMatcher(url: UrlSegment[]) {
  if (url.length === 0) return null;
  const path = url[0].path;
  if (['products', 'cart', 'checkout', 'search', 'quote'].includes(path)) {
    return { consumed: [] };
  }
  return null;
}

export const routes: Routes = [
  {
    path: 'auth/callback',
    component: BffCallbackComponent
  },
  {
    path: 'auth',
    loadChildren: () => loadRemoteModule('auth', './Routes')
      .then(m => m.routes)
      .catch(() => import('../../../auth/src/app/app.routes').then(m => m.routes))
  },
  {
    path: 'account',
    canActivate: [authGuard],
    loadChildren: () => loadRemoteModule('account', './Routes')
      .then(m => m.routes)
      .catch(() => import('../../../account/src/app/app.routes').then(m => m.routes))
  },

  // â”€â”€ Admin section â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // The shell owns the full /admin route tree because only the host (shell) app
  // can call loadRemoteModule for other remotes via the federation orchestrator.
  {
    path: 'admin',
    canActivate: [staffGuard],
    canActivateChild: [staffSectionGuard],
    loadComponent: () => Promise.resolve()
      .then(() => loadRemoteModule('admin-core', './AdminLayout'))
      .then(m => m.AdminLayoutComponent)
      .catch(() => import('../../../admin-core/src/app/layout/admin-layout.component').then(m => m.AdminLayoutComponent)),
    children: [
      // admin-core owned routes
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-core', './AdminDashboard'))
          .then(m => m.AdminDashboardComponent)
          .catch(() => import('../../../admin-core/src/app/dashboard/admin-dashboard.component').then(m => m.AdminDashboardComponent))
      },
      {
        path: 'analytics',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-core', './Analytics'))
          .then(m => m.AnalyticsComponent)
          .catch(() => import('../../../admin-core/src/app/analytics/analytics.component').then(m => m.AnalyticsComponent))
      },
      {
        path: 'users',
        canActivate: [adminGuard],
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-core', './Users'))
          .then(m => m.AdminUsersComponent)
          .catch(() => import('../../../admin-core/src/app/users/users.component').then(m => m.AdminUsersComponent))
      },
      {
        path: 'roles',
        canActivate: [adminGuard],
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-core', './Roles'))
          .then(m => m.AdminRolesComponent)
          .catch(() => import('../../../admin-core/src/app/roles/roles.component').then(m => m.AdminRolesComponent))
      },

      // CMS routes (admin-cms MFE with local fallback)
      {
        path: 'pages',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-cms', './Pages'))
          .then(m => m.AdminPagesComponent)
          .catch(() => import('../../../admin-cms/src/app/cms/pages.component').then(m => m.AdminPagesComponent))
      },
      {
        path: 'pages/:id',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-cms', './VisualHome'))
          .then(m => m.VisualHomeEditorComponent)
          .catch(() => import('../../../admin-cms/src/app/cms/visual-home-editor.component').then(m => m.VisualHomeEditorComponent))
      },
      {
        path: 'reviews',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-cms', './Reviews'))
          .then(m => m.AdminReviewsComponent)
          .catch(() => import('../../../admin-cms/src/app/cms/reviews.component').then(m => m.AdminReviewsComponent))
      },
      {
        path: 'blog',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-cms', './Blog'))
          .then(m => m.AdminBlogComponent)
          .catch(() => import('../../../admin-cms/src/app/cms/blog.component').then(m => m.AdminBlogComponent))
      },

      // Inventory routes (admin-shop MFE with local fallback)
      {
        path: 'inventory',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-shop', './InventoryDashboard'))
          .then(m => m.InventoryDashboardComponent)
          .catch(() => import('../../../admin-shop/src/app/inventory/inventory-dashboard.component').then(m => m.InventoryDashboardComponent))
      },
      {
        path: 'inventory/products/import',canDeactivate:[(component:any)=>!component.saving()],
        loadComponent: () => Promise.resolve().then(() => loadRemoteModule('admin-shop','./CatalogueImport')).then(m=>m.CatalogueImportComponent).catch(()=>import('../../../admin-shop/src/app/inventory/catalogue-import.component').then(m=>m.CatalogueImportComponent))
      },
      {
        path: 'inventory/products',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-shop', './ProductList'))
          .then(m => m.ProductListComponent)
          .catch(() => import('../../../admin-shop/src/app/inventory/product-list.component').then(m => m.ProductListComponent))
      },
      {
        path: 'inventory/products/new',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-shop', './ProductEdit'))
          .then(m => m.ProductEditComponent)
          .catch(() => import('../../../admin-shop/src/app/inventory/product-edit.component').then(m => m.ProductEditComponent))
      },
      {
        path: 'inventory/products/:sku/edit',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-shop', './ProductEdit'))
          .then(m => m.ProductEditComponent)
          .catch(() => import('../../../admin-shop/src/app/inventory/product-edit.component').then(m => m.ProductEditComponent))
      },
      {
        path: 'inventory/stock-in',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-shop', './StockAdjustment'))
          .then(m => m.StockAdjustmentComponent)
          .catch(() => import('../../../admin-shop/src/app/inventory/stock-adjustment.component').then(m => m.StockAdjustmentComponent)),
        data: { type: 'stock-in' }
      },
      {
        path: 'inventory/adjustment',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-shop', './StockAdjustment'))
          .then(m => m.StockAdjustmentComponent)
          .catch(() => import('../../../admin-shop/src/app/inventory/stock-adjustment.component').then(m => m.StockAdjustmentComponent)),
        data: { type: 'adjustment' }
      },
      {
        path: 'inventory/movements',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-shop', './StockMovements'))
          .then(m => m.StockMovementsComponent)
          .catch(() => import('../../../admin-shop/src/app/inventory/stock-movements.component').then(m => m.StockMovementsComponent))
      },
      {
        path: 'inventory/low-stock',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-shop', './StockReport'))
          .then(m => m.StockReportComponent)
          .catch(() => import('../../../admin-shop/src/app/inventory/stock-report.component').then(m => m.StockReportComponent)),
        data: { type: 'low-stock' }
      },
      {
        path: 'inventory/out-of-stock',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-shop', './StockReport'))
          .then(m => m.StockReportComponent)
          .catch(() => import('../../../admin-shop/src/app/inventory/stock-report.component').then(m => m.StockReportComponent)),
        data: { type: 'out-of-stock' }
      },

      {path:'categories',loadComponent:()=>Promise.resolve().then(()=>loadRemoteModule('admin-shop','./Categories')).then(m=>m.AdminCategoriesComponent).catch(()=>import('../../../admin-shop/src/app/inventory/categories.component').then(m=>m.AdminCategoriesComponent))},
      // Sales / Lead routes (admin-shop MFE with local fallback)
      {path:'affiliate-payouts',loadComponent:()=>Promise.resolve().then(()=>loadRemoteModule('admin-shop','./AffiliatePayouts')).then(m=>m.AdminAffiliatePayoutsComponent).catch(()=>import('../../../admin-shop/src/app/sales/affiliate-payouts.component').then(m=>m.AdminAffiliatePayoutsComponent))},
      {
        path: 'orders',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-shop', './Orders'))
          .then(m => m.AdminOrdersComponent)
          .catch(() => import('../../../admin-shop/src/app/sales/orders.component').then(m => m.AdminOrdersComponent))
      },
      {
        path: 'customers',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-shop', './Customers'))
          .then(m => m.AdminCustomersComponent)
          .catch(() => import('../../../admin-shop/src/app/sales/customers.component').then(m => m.AdminCustomersComponent))
      },
      {
        path: 'quotes',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-shop', './Quotes'))
          .then(m => m.AdminQuotesComponent)
          .catch(() => import('../../../admin-shop/src/app/sales/quotes.component').then(m => m.AdminQuotesComponent))
      },
      {
        path: 'applications',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-shop', './Applications'))
          .then(m => m.AdminApplicationsComponent)
          .catch(() => import('../../../admin-shop/src/app/sales/applications.component').then(m => m.AdminApplicationsComponent))
      },
      {
        path: 'inquiries',
        loadComponent: () => Promise.resolve()
          .then(() => loadRemoteModule('admin-shop', './Inquiries'))
          .then(m => m.AdminInquiriesComponent)
          .catch(() => import('../../../admin-shop/src/app/sales/inquiries.component').then(m => m.AdminInquiriesComponent))
      },
    ]
  },

  // â”€â”€ Public / Shop routes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  {
    path: '',
    loadChildren: () => loadRemoteModule('marketing', './Routes')
      .then(m => m.routes)
      .catch(() => import('../../../marketing/src/app/app.routes').then(m => m.routes))
  },
  {
    matcher: shopMatcher,
    loadChildren: () => loadRemoteModule('shop', './Routes')
      .then(m => m.routes)
      .catch(err => {
        console.warn('[Shell] Remote shop load failed, falling back to local shop routes:', err);
        return import('../../../shop/src/app/app.routes').then(m => m.routes);
      })
  }
];


