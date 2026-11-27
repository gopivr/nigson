import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LucideShoppingBag, LucideArrowRight } from '@lucide/angular';
import { SectionApi } from 'shared-ui';

@Component({
  selector: 'app-wholesale-cta',
  standalone: true,
  imports: [CommonModule, RouterLink, LucideShoppingBag, LucideArrowRight],
  template: `
    <section class="border-y border-hairline bg-white">
      <div class="container-page py-10 md:py-12">
        <div class="flex flex-col items-center gap-8 md:flex-row md:justify-between">
          <!-- Brand -->
          <div class="flex items-center gap-4 md:gap-6">
            <div class="grid h-14 w-14 place-items-center rounded-2xl bg-brand text-white md:h-16 md:w-16">
              <svg lucideShoppingBag class="h-7 w-7"></svg>
            </div>
            <div class="hidden h-10 w-px bg-hairline md:block"></div>
            <span class="text-sm font-extrabold uppercase tracking-[0.12em] text-ink">
              {{ s?.t('eyebrow') || 'NIGSON WHOLESALE' }}
            </span>
          </div>

          <!-- Headline -->
          <div class="text-center md:text-left">
            <h2 class="text-2xl font-extrabold uppercase leading-tight tracking-tight text-ink md:text-3xl lg:text-4xl">
              {{ s?.t('heading') || 'BUY MORE. SAVE MORE.' }}
            </h2>
            <p class="mt-2 text-sm text-muted-foreground">
              {{ s?.t('body') || 'Special pricing for bulk orders, retailers and business customers.' }}
            </p>
          </div>

          <!-- Actions -->
          <div class="flex flex-wrap items-center justify-center gap-3">
            <a
              [routerLink]="s?.t('btn1Link') || '/distributor'"
              class="inline-flex h-12 items-center gap-2 rounded-md bg-brand px-6 text-sm font-bold text-white shadow-brand transition-colors hover:bg-brand-deep"
            >
              {{ s?.t('btn1Label') || 'SHOP WHOLESALE' }} <svg lucideArrowRight class="h-4 w-4"></svg>
            </a>
            <a
              [routerLink]="s?.t('btn2Link') || '/quote'"
              class="inline-flex h-12 items-center rounded-md border border-ink bg-transparent px-6 text-sm font-bold text-ink transition-colors hover:border-brand hover:text-brand"
            >
              {{ s?.t('btn2Label') || 'REQUEST A QUOTE' }}
            </a>
          </div>
        </div>
      </div>
    </section>
  `
})
export class WholesaleCtaComponent {
  @Input() s?: SectionApi;
}
