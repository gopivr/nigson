import { Component, Input, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideChevronLeft, LucideChevronRight, LucideStar, LucideQuote } from '@lucide/angular';
import { SectionApi } from 'shared-ui';

@Component({
  selector: 'app-testimonials',
  standalone: true,
  imports: [CommonModule, LucideChevronLeft, LucideChevronRight, LucideStar, LucideQuote],
  template: `
    <section *ngIf="items().length > 0" class="border-t border-hairline bg-surface-alt py-32" data-cms-section="testimonials">
      <div class="container-page max-w-5xl">
        <p class="text-[11px] font-semibold uppercase tracking-[0.25em] text-brand mb-6 reveal-on-scroll" data-cms-element="eyebrow">
          {{ s?.t('eyebrow') }}
        </p>
        <h2 class="text-5xl md:text-6xl font-extrabold leading-[1.05] tracking-[-0.03em] mb-16 reveal-on-scroll text-balance" data-cms-element="heading">
          {{ s?.t('heading') }}
        </h2>
        <div class="rounded-3xl bg-white border border-hairline p-10 md:p-16 relative reveal-on-scroll" data-cms-element="card">
          <svg lucideQuote class="absolute top-8 right-8 h-16 w-16 text-brand/10"></svg>
          
          <div class="flex gap-1 mb-6">
            <ng-container *ngFor="let _ of [1,2,3,4,5]">
              <svg lucideStar class="h-5 w-5 fill-brand text-brand"></svg>
            </ng-container>
          </div>
          
          <blockquote class="text-2xl md:text-4xl font-semibold leading-[1.25] tracking-[-0.01em] text-ink text-balance">
            &quot;{{ active()?.quote }}&quot;
          </blockquote>
          
          <div class="mt-10 flex items-end justify-between flex-wrap gap-6">
            <div>
              <p class="text-lg font-extrabold">{{ active()?.name }}</p>
              <p class="text-sm text-muted-ink">{{ active()?.role }}</p>
            </div>
            
            <div class="flex items-center gap-3">
              <button
                type="button"
                (click)="prev()"
                aria-label="Previous"
                class="grid h-12 w-12 place-items-center rounded-full border border-hairline hover:bg-brand hover:text-white hover:border-brand transition-colors"
              >
                <svg lucideChevronLeft class="h-5 w-5"></svg>
              </button>
              <button
                type="button"
                (click)="next()"
                aria-label="Next"
                class="grid h-12 w-12 place-items-center rounded-full border border-hairline hover:bg-brand hover:text-white hover:border-brand transition-colors"
              >
                <svg lucideChevronRight class="h-5 w-5"></svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  `
})
export class TestimonialsComponent {
  @Input() s: SectionApi | null | undefined = null;
  
  i = signal(0);
  
  items = computed(() => {
    if (!this.s) return [];
    return this.s.cards((f: any) => f);
  });
  
  active = computed(() => {
    const list = this.items();
    if (list.length === 0) return null;
    return list[this.math.min(this.i(), list.length - 1)];
  });

  math = Math;

  prev() {
    const len = this.items().length;
    if (len === 0) return;
    this.i.set((this.i() - 1 + len) % len);
  }
  
  next() {
    const len = this.items().length;
    if (len === 0) return;
    this.i.set((this.i() + 1) % len);
  }
}
