import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./home/home').then(m => m.HomeComponent),
    pathMatch: 'full'
  },
  {
    path: 'about',
    loadComponent: () => import('./pages/about.component').then(m => m.AboutComponent)
  },
  {
    path: 'contact',
    loadComponent: () => import('./pages/contact.component').then(m => m.ContactComponent)
  },
  {
    path: 'careers',
    loadComponent: () => import('./pages/careers.component').then(m => m.CareersComponent)
  },
  {
    path: 'distributor',
    loadComponent: () => import('./pages/distributor.component').then(m => m.DistributorComponent)
  },
  {
    path: 'affiliate',
    loadComponent: () => import('./pages/distributor.component').then(m => m.DistributorComponent),
    data: { tab: 'affiliate' }
  },
  {
    path: 'blog',
    redirectTo: 'insights',
    pathMatch: 'full'
  },
  {
    path: 'blog/:slug',
    redirectTo: 'insights/:slug',
    pathMatch: 'full'
  },
  {
    path: 'insights',
    loadComponent: () => import('./pages/insights.component').then(m => m.InsightsComponent)
  },
  {
    path: 'insights/:slug',
    loadComponent: () => import('./pages/insight-post.component').then(m => m.InsightPostComponent)
  },
  {
    path: 'support/:page',
    loadComponent: () => import('./pages/support.component').then(m => m.SupportComponent)
  }
];
