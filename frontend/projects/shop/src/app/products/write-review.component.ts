import { Component, Input, Output, EventEmitter, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShopService, ReviewService, SessionService } from 'shared-ui';
import { LucideStar } from '@lucide/angular';

@Component({
  selector: 'app-write-review',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideStar],
  template: `
    <div class="fixed inset-0 z-[70] bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 animate-fade-in" (click)="close.emit()">
      <div class="w-full max-w-md rounded-3xl bg-white p-8 shadow-2xl animate-scale-in" (click)="$event.stopPropagation()">
        <ng-container *ngIf="done; else formTemplate">
          <div class="text-center py-6">
            <div class="mx-auto grid h-14 w-14 place-items-center rounded-full bg-brand/10">
              <svg lucideStar class="h-7 w-7 fill-brand text-brand"></svg>
            </div>
            <h3 class="mt-4 text-xl font-extrabold">Review submitted</h3>
            <p class="mt-2 text-sm text-muted-foreground">Thanks for helping other buyers.</p>
          </div>
        </ng-container>

        <ng-template #formTemplate>
          <form (submit)="submit($event)">
            <h3 class="text-xl font-extrabold">Write a Review</h3>
            <p class="mt-1 text-sm text-muted-foreground">Share your experience with this product.</p>

            <div class="mt-5">
              <p class="text-[11px] font-semibold uppercase tracking-widest text-muted-foreground">Your rating</p>
              <div class="mt-2 flex gap-1">
                <button
                  *ngFor="let i of [1,2,3,4,5]"
                  type="button"
                  [attr.aria-label]="i + ' stars'"
                  (mouseenter)="hover = i"
                  (mouseleave)="hover = 0"
                  (click)="rating = i"
                >
                  <svg
                    lucideStar
                    class="h-7 w-7 transition-colors"
                    [ngClass]="i <= (hover || rating) ? 'fill-brand text-brand' : 'text-hairline'"
                  ></svg>
                </button>
              </div>
            </div>

            <label class="mt-5 block">
              <span class="text-[11px] font-semibold uppercase tracking-widest text-muted-foreground">Name</span>
              <input
                [(ngModel)]="name"
                name="reviewerName"
                placeholder="Your name"
                class="mt-1.5 w-full rounded-xl border border-hairline px-4 py-3 text-sm focus:outline-none focus:border-brand"
              />
            </label>

            <label class="mt-4 block">
              <span class="text-[11px] font-semibold uppercase tracking-widest text-muted-foreground">Review</span>
              <textarea
                [(ngModel)]="text"
                name="reviewText"
                rows="4"
                placeholder="What did you think of this product?"
                class="mt-1.5 w-full rounded-xl border border-hairline px-4 py-3 text-sm focus:outline-none focus:border-brand resize-none"
              ></textarea>
            </label>

            <div *ngIf="error" class="mt-3 text-sm font-semibold text-red-500">{{ error }}</div>

            <div class="mt-6 flex gap-3">
              <button
                type="button"
                (click)="close.emit()"
                class="flex-1 rounded-full border border-hairline px-5 py-3 text-sm font-semibold hover:border-brand hover:text-brand transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                [disabled]="submitting"
                class="flex-1 rounded-full bg-brand text-white px-5 py-3 text-sm font-semibold hover:bg-brand-deep transition-colors disabled:opacity-60"
              >
                {{ submitting ? 'Submitting...' : 'Submit Review' }}
              </button>
            </div>
          </form>
        </ng-template>
      </div>
    </div>
  `
})
export class WriteReviewComponent {
  shop = inject(ShopService);
  private reviewSvc = inject(ReviewService);
  private session = inject(SessionService);

  @Input() sku!: string;
  @Input() productId?: string;
  @Input() orderLineId?: string;
  @Output() close = new EventEmitter<void>();

  rating = 5;
  hover = 0;
  name = this.shop.user()?.fullName ?? '';
  text = '';
  submitting = false;
  done = false;
  error = '';

  submit(e: Event) {
    e.preventDefault();
    this.error = '';

    if (!this.name.trim() || !this.text.trim()) {
      this.error = 'Please enter your name and review.';
      return;
    }

    if (!this.session.authenticated()) {
      this.shop.openLoginRequired('Sign in to submit a verified review.');
      return;
    }

    const kind: 'PRODUCT' | 'TESTIMONIAL' = this.productId ? 'PRODUCT' : 'TESTIMONIAL';
    if (kind === 'PRODUCT' && !this.orderLineId) {
      this.error = 'A verified purchase order is required to review this product.';
      return;
    }

    this.submitting = true;
    this.reviewSvc.submitReview({
      kind,
      productId: kind === 'PRODUCT' ? this.productId : undefined,
      orderLineId: kind === 'PRODUCT' ? this.orderLineId : undefined,
      rating: this.rating,
      title: this.name.trim(),
      body: this.text.trim()
    }).subscribe({
      next: () => {
        this.submitting = false;
        this.done = true;
        setTimeout(() => this.close.emit(), 1400);
      },
      error: (err) => {
        this.submitting = false;
        this.error = err?.error?.detail || err?.error?.message || 'Unable to submit review right now.';
      }
    });
  }
}
