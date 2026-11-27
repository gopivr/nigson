import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'lib-admin-heading',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="mb-7 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl md:text-3xl font-extrabold tracking-tight text-ink">{{ title }}</h1>
        <p *ngIf="subtitle" class="mt-1 text-sm text-muted-ink">{{ subtitle }}</p>
      </div>
      <ng-content></ng-content>
    </div>
  `
})
export class AdminHeadingComponent {
  @Input({ required: true }) title!: string;
  @Input() subtitle?: string;
}
