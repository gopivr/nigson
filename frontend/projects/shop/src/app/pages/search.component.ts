import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { CatalogService, PageHeaderComponent, Product } from 'shared-ui';
import { ProductCardComponent } from '../products/product-card.component';
import { LucideLoader2 } from '@lucide/angular';

/**
 * SearchComponent (TASK-24)
 * Replaces local PRODUCTS.filter() with GET /api/v1/catalogue/products?q=...
 */
@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule, ProductCardComponent, PageHeaderComponent, LucideLoader2],
  template: `
    <div class="bg-surface min-h-screen">
      <lib-page-header [title]="headerTitle()" [subtitle]="headerSubtitle()"></lib-page-header>

      <section class="container-page py-12">
        <!-- Loading -->
        <div *ngIf="catalog.loading()" class="flex justify-center py-20">
          <svg lucideLoader2 class="h-8 w-8 text-brand animate-spin"></svg>
        </div>

        <!-- Results -->
        <ng-container *ngIf="!catalog.loading() && catalog.products().length > 0">
          <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            <app-product-card *ngFor="let p of catalog.products()" [p]="p"></app-product-card>
          </div>
        </ng-container>

        <!-- No results -->
        <ng-container *ngIf="!catalog.loading() && catalog.products().length === 0">
          <div class="rounded-2xl border border-border bg-surface-alt/40 py-16 text-center">
            <p class="text-lg font-semibold text-ink">No products found</p>
            <p class="mt-2 text-sm text-muted-foreground">
              We couldn't find a match{{ term() ? ' for "' + term() + '"' : '' }}.
            </p>
          </div>
        </ng-container>
      </section>
    </div>
  `
})
export class SearchComponent implements OnInit {
  catalog = inject(CatalogService);
  route = inject(ActivatedRoute);

  readonly term = signal('');

  headerTitle = () => this.term() ? `Results for "${this.term()}"` : 'Search Products';
  headerSubtitle = () => this.catalog.totalElements()
    ? `${this.catalog.totalElements()} products found`
    : 'Search our product catalogue';

  ngOnInit() {
    const q = this.route.snapshot.queryParams['q'] ?? '';
    this.term.set(q);
    if (q) {
      this.catalog.loadProducts({ q, size: 30 }).subscribe();
    }
  }
}
