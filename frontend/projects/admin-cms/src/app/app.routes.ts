import {staffGuard, staffSectionGuard} from 'shared-ui';
import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'blog',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./cms/blog.component').then(m => m.AdminBlogComponent)
  },
  {
    path: 'pages',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./cms/pages.component').then(m => m.AdminPagesComponent)
  },
  {
    path: 'reviews',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./cms/reviews.component').then(m => m.AdminReviewsComponent)
  },
  {
    path: 'pages/home',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./cms/visual-home-editor.component').then(m => m.VisualHomeEditorComponent)
  },
  {
    path: 'pages/:id',
    canActivate: [staffGuard, staffSectionGuard],
    loadComponent: () => import('./cms/visual-home-editor.component').then(m => m.VisualHomeEditorComponent)
  }
];
