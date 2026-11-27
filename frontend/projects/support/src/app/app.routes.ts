import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./contact-enquiry.component').then(m => m.ContactEnquiryComponent)
  },
  {
    path: 'tracking',
    loadComponent: () => import('./order-tracking.component').then(m => m.OrderTrackingComponent)
  },
  {
    path: 'tracking/:id',
    loadComponent: () => import('./order-tracking.component').then(m => m.OrderTrackingComponent)
  }
];
