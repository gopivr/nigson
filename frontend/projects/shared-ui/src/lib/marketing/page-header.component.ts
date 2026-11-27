import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'lib-page-header',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="relative overflow-hidden border-b border-hairline bg-white">
      <div class="container-page relative py-8 md:py-12">
        <p *ngIf="eyebrow" class="mb-3 text-[11px] font-semibold uppercase tracking-[0.25em] text-brand">
          {{ eyebrow }}
        </p>
        
        <h1 class="text-4xl md:text-6xl font-extrabold text-balance max-w-4xl leading-[1.05]">
          {{ title }}
        </h1>
        
        <p *ngIf="subtitle" class="mt-4 max-w-2xl text-base md:text-lg text-muted-ink">
          {{ subtitle }}
        </p>
      </div>
    </section>
  `
})
export class PageHeaderComponent {
  @Input() eyebrow?: string;
  @Input({ required: true }) title!: string;
  @Input() subtitle?: string;
}
