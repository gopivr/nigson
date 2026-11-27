import { Component, signal, computed, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminHeadingComponent, BadgeComponent, AdminService, AdminReview } from 'shared-ui';
import { LucideCheck, LucideTrash2 } from '@lucide/angular';

@Component({
  selector: 'app-admin-reviews',
  standalone: true,
  imports: [CommonModule, FormsModule, AdminHeadingComponent, BadgeComponent, LucideCheck, LucideTrash2],
  template: `
    <lib-admin-heading title="Customer Reviews" subtitle="Moderate and respond to product reviews."></lib-admin-heading>
    
    <div class="rounded-2xl border border-hairline bg-white shadow-sm overflow-hidden">
      <div class="flex items-center gap-4 border-b border-hairline p-4 bg-surface/50">
        <select [(ngModel)]="statusFilter" class="rounded-xl border border-hairline bg-white px-4 py-2 text-sm focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20">
          <option value="All">All Reviews</option>
          <option value="Pending">Pending / Hidden</option>
          <option value="Published">Published</option>
        </select>
      </div>
      
      <div class="overflow-x-auto">
        <table class="w-full min-w-[720px] text-left text-sm text-ink">
          <thead class="border-b border-hairline bg-surface-alt/50 text-[10px] font-bold uppercase tracking-widest text-muted-ink">
            <tr>
              <th class="px-6 py-4">Customer</th>
              <th class="px-6 py-4">Product</th>
              <th class="px-6 py-4">Rating & Review</th>
              <th class="px-6 py-4">Status</th>
              <th class="px-6 py-4">Date</th>
              <th class="px-6 py-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-hairline">
            <tr *ngFor="let r of filteredReviews()" class="transition-colors hover:bg-surface-alt/30">
              <td class="px-6 py-4 font-bold">{{ r.author }}</td>
              <td class="px-6 py-4 text-muted-ink">{{ r.sku || r.product }}</td>
              <td class="px-6 py-4 max-w-sm">
                <div class="flex text-amber-400 mb-1">
                  <span *ngFor="let s of [1,2,3,4,5]">
                    <svg *ngIf="s <= r.rating" viewBox="0 0 24 24" fill="currentColor" class="h-3 w-3"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
                    <svg *ngIf="s > r.rating" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="h-3 w-3"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
                  </span>
                </div>
                <p class="text-xs text-ink line-clamp-2">{{ r.text }}</p>
              </td>
              <td class="px-6 py-4"><lib-badge [tone]="r.status === 'Hidden' ? 'warning' : 'success'">{{ r.status }}</lib-badge></td>
              <td class="px-6 py-4 whitespace-nowrap text-xs text-muted-ink">{{ r.date | date:'mediumDate' }}</td>
              <td class="px-6 py-4 text-right">
                <div class="flex items-center justify-end gap-2">
                  <button *ngIf="r.status === 'Hidden'" (click)="toggleHidden(r.id, false)" class="grid h-8 w-8 place-items-center rounded-lg text-muted-ink transition-colors hover:bg-emerald-100 hover:text-emerald-600" title="Approve">
                    <svg lucideCheck class="h-4 w-4"></svg>
                  </button>
                  <button *ngIf="r.status === 'Published'" (click)="toggleHidden(r.id, true)" class="grid h-8 w-8 place-items-center rounded-lg text-muted-ink transition-colors hover:bg-amber-100 hover:text-amber-600" title="Hide">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="h-4 w-4"><path d="M13.875 18.825A10.05 10.05 0 0 1 12 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 0 1 1.563-3.029m5.858.908a3 3 0 1 1 4.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l18 18M15.364 8.636A10.076 10.076 0 0 1 21.543 12c-1.275 4.057-5.065 7-9.543 7a9.97 9.97 0 0 1-2.545-.333"/></svg>
                  </button>
                  <button (click)="deleteReview(r.id)" class="grid h-8 w-8 place-items-center rounded-lg text-muted-ink transition-colors hover:bg-rose-100 hover:text-rose-600" title="Reject">
                    <svg lucideTrash2 class="h-4 w-4"></svg>
                  </button>
                </div>
              </td>
            </tr>
            <tr *ngIf="filteredReviews().length === 0">
              <td colspan="6" class="p-8 text-center text-muted-ink">No reviews found.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class AdminReviewsComponent implements OnInit {
  admin = inject(AdminService);
  statusFilter = signal('All');

  ngOnInit() {
    this.admin.loadStaffReviews().subscribe();
  }

  filteredReviews = computed(() => {
    const s = this.statusFilter();
    const list = this.admin.reviews();
    if (s === 'All') return list;
    if (s === 'Pending') return list.filter(r => r.status === 'Hidden');
    return list.filter(r => r.status === 'Published');
  });

  toggleHidden(id: string, hide: boolean) {
    this.admin.moderateReview(id, hide ? 'WITHDRAW' : 'PUBLISH').subscribe({error: () => {}});
  }
  deleteReview(id: string) {
    if (confirm('Reject this review? Published reviews should be withdrawn first.')) this.admin.moderateReview(id, 'REJECT').subscribe({error: () => {}});
  }
}
