import { Component, Input, TemplateRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'lib-stat-card',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div
      [class]="'rounded-2xl border border-hairline bg-white p-5 transition-all duration-200 shadow-[0_1px_2px_rgba(17,17,17,0.04)] ' + 
              (to ? 'hover:shadow-md hover:border-brand/40 hover:bg-surface-alt/30 cursor-pointer group ' : '') + 
              (className || '')"
    >
      <ng-container *ngIf="to; else noLink">
        <a [routerLink]="to" class="block">
          <ng-container *ngTemplateOutlet="content"></ng-container>
        </a>
      </ng-container>
      <ng-template #noLink>
        <ng-container *ngTemplateOutlet="content"></ng-container>
      </ng-template>
    </div>

    <ng-template #content>
      <div class="flex items-start justify-between">
        <div>
          <p class="text-[10px] font-bold uppercase tracking-widest text-muted-ink">{{ label }}</p>
          <p class="mt-2 text-2xl font-extrabold text-ink">{{ value }}</p>
          <p *ngIf="hint" class="mt-1 text-xs text-muted-ink">{{ hint }}</p>
        </div>
        <div *ngIf="icon" class="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-red-50 text-brand transition-colors group-hover:bg-brand group-hover:text-white">
          <ng-content></ng-content>
        </div>
      </div>
    </ng-template>
  `
})
export class StatCardComponent {
  @Input({ required: true }) label!: string;
  @Input({ required: true }) value!: string | number | null;
  @Input() hint?: string;
  @Input() to?: string | any[];
  @Input() className?: string;
  @Input() icon?: boolean = false; // We can use ng-content to pass the lucide icon, and set icon=true if we want the box
}
