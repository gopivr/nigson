import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./blog-list.component').then(m => m.BlogListComponent)
  },
  {
    path: ':slug',
    loadComponent: () => import('./blog-detail.component').then(m => m.BlogDetailComponent)
  }
];
