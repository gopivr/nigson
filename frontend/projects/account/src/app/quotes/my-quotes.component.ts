import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { QuoteRequestHistoryItem, QuoteService } from 'shared-ui';
import {
  LucideClipboardList,
  LucideArrowRight,
  LucidePlus,
  LucideLoader2,
  LucideAlertCircle
} from '@lucide/angular';

@Component({
  selector: 'app-my-quotes',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    LucideClipboardList,
    LucideArrowRight,
    LucidePlus,
    LucideLoader2,
    LucideAlertCircle
  ],
  template: `
    <div class="space-y-6">
      <div class="rounded-2xl border border-hairline bg-white p-6 md:p-8">
        <div class="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p class="text-[10px] font-semibold uppercase tracking-widest text-brand">Wholesale</p>
            <h2 class="text-2xl font-extrabold text-ink">My Quote Requests</h2>
          </div>
          <a
            routerLink="/quote"
            class="inline-flex items-center gap-2 rounded-full bg-brand text-white px-5 py-2.5 text-sm font-semibold hover:bg-brand-deep transition-colors shadow-sm"
          >
            <svg lucidePlus class="h-4 w-4"></svg> New Quote Request
          </a>
        </div>
      </div>

      <div *ngIf="quoteSvc.loadError()" class="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 flex items-center justify-between">
        <div class="flex items-center gap-2">
          <svg lucideAlertCircle class="h-5 w-5 text-red-600"></svg>
          <span>{{ quoteSvc.loadError() }}</span>
        </div>
        <button (click)="quoteSvc.loadQuotes().subscribe()" class="font-semibold text-red-600 hover:text-red-800">
          Try again
        </button>
      </div>

      <!-- Loading State -->
      <div *ngIf="quoteSvc.loading()" class="rounded-2xl border border-hairline bg-white p-16 text-center">
        <svg lucideLoader2 class="mx-auto h-8 w-8 animate-spin text-brand"></svg>
        <p class="mt-4 text-sm text-muted-ink">Loading your quote requests...</p>
      </div>

      <!-- Empty state -->
      <div *ngIf="!quoteSvc.loading() && !quoteSvc.loadError() && quotes().length === 0" class="rounded-2xl border border-hairline bg-white p-16 text-center">
        <div class="mx-auto grid h-16 w-16 place-items-center rounded-full bg-surface-alt">
          <svg lucideClipboardList class="h-8 w-8 text-muted-ink"></svg>
        </div>
        <p class="mt-4 text-lg font-bold text-ink">No quote requests yet</p>
        <p class="mt-2 text-sm text-muted-ink">Submit a wholesale quote request and track pricing or negotiations here.</p>
        <a routerLink="/quote" class="mt-6 inline-flex items-center gap-2 rounded-full bg-brand text-white px-6 py-3 text-sm font-semibold hover:bg-brand-deep transition-colors shadow-sm">
          Request a Quote <svg lucideArrowRight class="h-4 w-4"></svg>
        </a>
      </div>

      <!-- Quotes list -->
      <div *ngIf="!quoteSvc.loading() && quotes().length > 0" class="rounded-2xl border border-hairline bg-white overflow-hidden shadow-sm">
        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm">
            <thead class="border-b border-hairline bg-surface-alt/50 text-[10px] font-bold uppercase tracking-widest text-muted-ink">
              <tr>
                <th class="px-6 py-4">Request</th>
                <th class="px-6 py-4">Products</th>
                <th class="px-6 py-4">Message</th>
                <th class="px-6 py-4">Status</th>
                <th class="px-6 py-4">Date</th>
                <th class="px-6 py-4">Quote</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-hairline">
              <tr *ngFor="let q of quotes()" class="hover:bg-surface-alt/30 transition-colors">
                <td class="px-6 py-4 font-bold text-ink">{{ q.subject }}</td>
                <td class="px-6 py-4 text-muted-ink max-w-xs">
                  {{ productsLabel(q) }}
                </td>
                <td class="px-6 py-4 text-muted-ink max-w-sm truncate" [title]="q.message">{{ q.message }}</td>
                <td class="px-6 py-4 whitespace-nowrap">
                  <span class="rounded-full px-3 py-1 text-[11px] font-bold" [ngClass]="quoteSvc.statusClass(q.state)">
                    {{ quoteSvc.statusLabel(q.state) }}
                  </span>
                </td>
                <td class="px-6 py-4 whitespace-nowrap text-xs text-muted-ink">{{ formatDate(q.createdAt) }}</td>
                <td class="px-6 py-4 text-xs text-muted-ink">{{ q.quoteId ? 'Quote linked' : 'Awaiting response' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `
})
export class MyQuotesComponent implements OnInit {
  quoteSvc = inject(QuoteService);

  get quotes() {
    return this.quoteSvc.quotes;
  }

  ngOnInit(): void {
    this.quoteSvc.loadQuotes().subscribe();
  }

  productsLabel(quote: QuoteRequestHistoryItem): string {
    return quote.products.map(product => `${product.skuId} × ${product.quantity}`).join(', ');
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '-';
    try {
      return new Date(dateStr).toLocaleDateString('en-NG', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
      });
    } catch {
      return dateStr;
    }
  }
}
