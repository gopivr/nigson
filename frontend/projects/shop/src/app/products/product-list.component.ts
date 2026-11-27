import { Component, computed, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideSearch, LucideLoader2 } from '@lucide/angular';
import { CatalogService, Product } from 'shared-ui';
import { ProductCardComponent } from './product-card.component';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { Subject, Subscription } from 'rxjs';

/**
 * Robust category resolver that matches UUIDs, exact category names/slugs,
 * typo-tolerant prefix matches (e.g. 'Earbudsj' -> 'Earbuds'),
 * and common storefront CMS aliases (e.g. 'Home Chargers' -> 'Chargers').
 */
export function resolveCategory(rawCategory: string, categories: { id: string; name: string; slug?: string }[]): string | null {
  if (!rawCategory || !categories?.length) return null;
  const trimmed = rawCategory.trim();

  // 1. Direct UUID match
  if (/^[0-9a-f-]{36}$/i.test(trimmed)) {
    const byId = categories.find(c => c.id.toLowerCase() === trimmed.toLowerCase());
    return byId ? byId.id : trimmed;
  }

  const norm = trimmed.toLowerCase();

  // 2. Exact match on name or slug
  const exact = categories.find(c => c.name.toLowerCase() === norm || c.slug?.toLowerCase() === norm);
  if (exact) return exact.id;

  // 3. Cleaned match (strip trailing non-alphanumeric or stray characters)
  const cleaned = norm.replace(/[^a-z0-9\s]/g, '').trim();
  const cleanedMatch = categories.find(c => {
    const cName = c.name.toLowerCase();
    const cSlug = (c.slug || '').toLowerCase();
    return cName === cleaned || cSlug === cleaned;
  });
  if (cleanedMatch) return cleanedMatch.id;

  // 4. StartsWith / Prefix match (e.g. 'earbudsj' starts with 'earbuds')
  const prefixMatch = categories.find(c => {
    const cName = c.name.toLowerCase();
    return norm.startsWith(cName) || cName.startsWith(norm) ||
           cleaned.startsWith(cName) || cName.startsWith(cleaned);
  });
  if (prefixMatch) return prefixMatch.id;

  // 5. Semantic aliases for known storefront categories (sorted longest to shortest so specific phrases match first)
  const aliasMap: Record<string, string> = {
    'power strips': 'extension sockets',
    'power strip': 'extension sockets',
    'surge protector': 'extension sockets',
    'extension sockets': 'extension sockets',
    'sockets': 'extension sockets',
    'wireless chargers': 'wireless chargers',
    'wireless charger': 'wireless chargers',
    'home chargers': 'chargers',
    'car chargers': 'chargers',
    'car accessories': 'power and accessories',
    'mobile accessories': 'cables',
    'power banks': 'power banks',
    'power bank': 'power banks',
    'earbudsj': 'earbuds',
    'earbuds': 'earbuds',
    'earbud': 'earbuds',
    'headphone': 'earbuds',
    'chargers': 'chargers',
    'charger': 'chargers',
    'charging': 'chargers',
    'cables': 'cables',
    'cable': 'cables',
    'power': 'power banks',
    'audio': 'audio',
    'wireless': 'wireless chargers',
    'smartphones': 'smartphones',
    'smartphone': 'smartphones',
    'laptops': 'laptops',
    'laptop': 'laptops',
    'tablets': 'tablets',
    'tablet': 'tablets',
    'gaming': 'gaming',
    'storage': 'storage',
    'networking': 'networking'
  };

  const sortedAliases = Object.entries(aliasMap).sort((a, b) => b[0].length - a[0].length);
  for (const [key, targetCat] of sortedAliases) {
    if (norm.includes(key) || key.includes(norm) || cleaned.includes(key)) {
      const targetFound = categories.find(c => c.name.toLowerCase() === targetCat || c.slug?.toLowerCase() === targetCat);
      if (targetFound) return targetFound.id;
    }
  }

  // 6. Contains / Substring match (e.g. 'home chargers' contains 'chargers')
  const containsMatch = categories.find(c => {
    const cName = c.name.toLowerCase();
    return norm.includes(cName) || cName.includes(norm);
  });
  if (containsMatch) return containsMatch.id;

  return null;
}

/**
 * ProductListComponent (TASK-15, TASK-17, TASK-23)
 *
 * Loads all catalog products & categories from backend:
 *  - GET /api/v1/catalogue/products?q=...&categoryId=...
 *  - GET /api/v1/catalogue/categories
 */
@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideSearch, LucideLoader2, ProductCardComponent],
  template: `
    <div class="min-h-screen bg-background">
      <header class="bg-surface-alt border-b border-border py-6 md:py-8">
        <div class="container-page text-center">
          <span class="inline-block px-3 py-1 rounded-full bg-brand/10 text-brand text-xs font-bold uppercase tracking-widest mb-4">
            Product Catalog
          </span>
          <h1 class="text-3xl md:text-5xl font-extrabold text-ink tracking-tight mb-4">
            Phone accessories, power solutions &amp; FMCG.
          </h1>
          <p class="text-muted-foreground text-sm md:text-base max-w-2xl mx-auto">
            Curated global sourcing, competitive wholesale pricing, and consistent supply across Nigeria.
          </p>
        </div>
      </header>

      <section class="container-page py-12">
        <!-- Search bar -->
        <div class="flex flex-col md:flex-row md:items-center gap-3 mb-8">
          <div class="relative flex-1">
            <svg lucideSearch class="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"></svg>
            <input
              [ngModel]="searchQuery()"
              (ngModelChange)="onSearch($event)"
              placeholder="Search by name, SKU or feature..."
              class="w-full rounded-full border border-border bg-background pl-11 pr-4 py-3 text-sm focus:outline-none focus:border-brand"
            />
          </div>
        </div>

        <!-- Category chips from backend (all available categories) -->
        <div class="flex flex-wrap gap-2 mb-10">
          <button
            (click)="selectCategory(null)"
            [ngClass]="activeCategory() === null ? 'bg-ink text-background border-ink' : 'border-border text-muted-foreground hover:border-brand hover:text-brand'"
            class="rounded-full border px-4 py-1.5 text-sm font-medium transition-colors"
          >All</button>
          <button
            *ngFor="let c of catalog.categories()"
            (click)="selectCategory(c.id)"
            [ngClass]="activeCategory() === c.id ? 'bg-ink text-background border-ink' : 'border-border text-muted-foreground hover:border-brand hover:text-brand'"
            class="rounded-full border px-4 py-1.5 text-sm font-medium transition-colors"
          >{{ c.name }}</button>
        </div>

        <!-- Catalogue not activated for this market -->
        <div *ngIf="!catalog.loading() && catalog.catalogueUnavailable()" class="rounded-2xl border border-border bg-surface-alt px-6 py-16 text-center">
          <h2 class="text-xl md:text-2xl font-bold text-ink mb-3">Catalogue is not available yet</h2>
          <p class="mx-auto max-w-xl text-sm md:text-base text-muted-foreground">
            The Nigeria market has not been activated for selling, so products and categories cannot be shown.
            Please check back later or contact us if you expected the store to be open.
          </p>
        </div>

        <!-- Loading state -->
        <div *ngIf="!catalog.catalogueUnavailable() && catalog.loading()" class="flex items-center justify-center py-20">
          <svg lucideLoader2 class="h-8 w-8 text-brand animate-spin"></svg>
        </div>

        <!-- Empty state -->
        <div *ngIf="!catalog.catalogueUnavailable() && !catalog.loading() && catalog.products().length === 0" class="py-20 text-center text-muted-foreground">
          No products match your search.
        </div>

        <!-- Product grid -->
        <div *ngIf="!catalog.loading() && catalog.products().length > 0" class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <app-product-card *ngFor="let p of catalog.products()" [p]="p"></app-product-card>
        </div>

        <!-- Pagination -->
        <div *ngIf="catalog.totalPages() > 1" class="mt-10 flex items-center justify-center gap-2">
          <button
            (click)="changePage(catalog.currentPage() - 1)"
            [disabled]="catalog.currentPage() === 0"
            class="rounded-full border border-border px-4 py-2 text-sm font-semibold disabled:opacity-40 hover:border-brand hover:text-brand transition-colors"
          >Previous</button>
          <span class="text-sm text-muted-foreground">
            Page {{ catalog.currentPage() + 1 }} of {{ catalog.totalPages() }}
          </span>
          <button
            (click)="changePage(catalog.currentPage() + 1)"
            [disabled]="catalog.currentPage() >= catalog.totalPages() - 1"
            class="rounded-full border border-border px-4 py-2 text-sm font-semibold disabled:opacity-40 hover:border-brand hover:text-brand transition-colors"
          >Next</button>
        </div>
      </section>
    </div>
  `
})
export class ProductListComponent implements OnInit, OnDestroy {
  catalog = inject(CatalogService);
  route = inject(ActivatedRoute);
  router = inject(Router);

  readonly searchQuery = signal('');
  readonly activeCategory = signal<string | null>(null);
  private searchSubject = new Subject<string>();
  private subs = new Subscription();

  ngOnInit() {
    const initialParam = this.route.snapshot.queryParams['categoryId'] || this.route.snapshot.queryParams['category'] || null;

    // 1. Always fetch all real categories from backend on initialization
    this.catalog.loadCategories().subscribe(cats => {
      if (initialParam && cats?.length) {
        const resolvedId = resolveCategory(initialParam, cats);
        this.activeCategory.set(resolvedId);
        this.loadProducts(0);
      }
    });

    // If no initial category parameter was passed, load all products immediately
    if (!initialParam) {
      this.activeCategory.set(null);
      this.loadProducts(0);
    }

    // 2. Listen to route query param changes (e.g. clicking category links while on the page)
    this.subs.add(
      this.route.queryParams.subscribe(params => {
        const catParam = params['categoryId'] || params['category'] || null;
        const currentCats = this.catalog.categories();

        if (currentCats && currentCats.length > 0) {
          const resolvedId = catParam ? resolveCategory(catParam, currentCats) : null;
          if (resolvedId !== this.activeCategory()) {
            this.activeCategory.set(resolvedId);
            this.loadProducts(0);
          }
        }
      })
    );

    // 3. Debounced search
    this.subs.add(
      this.searchSubject.pipe(
        debounceTime(350),
        distinctUntilChanged()
      ).subscribe(q => {
        this.searchQuery.set(q);
        this.loadProducts(0);
      })
    );
  }

  ngOnDestroy() {
    this.subs.unsubscribe();
  }

  onSearch(q: string) {
    this.searchSubject.next(q);
  }

  selectCategory(categoryId: string | null) {
    this.activeCategory.set(categoryId);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: categoryId ? { categoryId } : {},
      queryParamsHandling: 'replace'
    });
    this.loadProducts(0);
  }

  changePage(page: number) {
    this.loadProducts(page);
  }

  private loadProducts(page = 0) {
    const activeCat = this.activeCategory();
    const categoryUuid = (activeCat && /^[0-9a-f-]{36}$/i.test(activeCat)) ? activeCat : undefined;

    this.catalog.loadProducts({
      q: this.searchQuery() || undefined,
      categoryId: categoryUuid,
      page,
      size: 20,
    }).subscribe();
  }
}
