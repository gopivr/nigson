import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./inventory-view.component').then(m => m.InventoryViewComponent)
  }
];
