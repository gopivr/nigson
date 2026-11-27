import { Component, Input, Output, EventEmitter, ElementRef, HostListener, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideChevronDown, LucideSearch } from '@lucide/angular';

export type Country = { code: string; name: string; dial: string; flag: string };

export const COUNTRIES: Country[] = [
  { code: "NG", name: "Nigeria", dial: "+234", flag: "🇳🇬" },
  { code: "GH", name: "Ghana", dial: "+233", flag: "🇬🇭" },
  { code: "KE", name: "Kenya", dial: "+254", flag: "🇰🇪" },
  { code: "ZA", name: "South Africa", dial: "+27", flag: "🇿🇦" },
  { code: "EG", name: "Egypt", dial: "+20", flag: "🇪🇬" },
  { code: "GB", name: "United Kingdom", dial: "+44", flag: "🇬🇧" },
  { code: "US", name: "United States", dial: "+1", flag: "🇺🇸" },
];

export const DEFAULT_COUNTRY = COUNTRIES[0];

@Component({
  selector: 'ui-country-select',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideChevronDown, LucideSearch],
  template: `
    <div class="relative h-full">
      <button
        type="button"
        (click)="open.set(!open())"
        aria-label="Select country code"
        class="flex h-full items-center gap-1.5 rounded-l-xl border-r border-hairline px-3 py-3 text-sm font-medium text-ink hover:text-brand transition-colors"
      >
        <span class="text-base leading-none">{{ value()?.flag || defaultCountry.flag }}</span>
        <span>{{ value()?.dial || defaultCountry.dial }}</span>
        <svg lucideChevronDown class="h-3.5 w-3.5 text-muted-ink transition-transform" [class.rotate-180]="open()"></svg>
      </button>

      <div *ngIf="open()" class="absolute left-0 top-[calc(100%+8px)] z-50 w-72 overflow-hidden rounded-2xl border border-hairline bg-white shadow-xl animate-scale-in">
        <div class="flex items-center gap-2 border-b border-hairline px-3 py-2.5">
          <svg lucideSearch class="h-4 w-4 text-muted-ink"></svg>
          <input
            #searchInput
            autofocus
            [(ngModel)]="query"
            placeholder="Search country or code"
            class="w-full bg-transparent text-sm outline-none"
          />
        </div>
        <ul class="max-h-64 overflow-y-auto py-1">
          <li *ngFor="let c of filteredList()">
            <button
              type="button"
              (click)="select(c)"
              class="flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm hover:bg-surface-alt transition-colors"
              [class.text-brand]="c.code === value()?.code"
              [class.font-semibold]="c.code === value()?.code"
              [class.text-ink]="c.code !== value()?.code"
            >
              <span class="text-base">{{ c.flag }}</span>
              <span class="flex-1 truncate">{{ c.name }}</span>
              <span class="text-muted-ink">{{ c.dial }}</span>
            </button>
          </li>
          <li *ngIf="filteredList().length === 0" class="px-3 py-6 text-center text-sm text-muted-ink">
            No matches
          </li>
        </ul>
      </div>
    </div>
  `
})
export class CountrySelectComponent {
  @Input() value = signal<Country | null>(null);
  @Output() valueChange = new EventEmitter<Country>();

  defaultCountry = DEFAULT_COUNTRY;
  open = signal(false);
  query = signal('');

  filteredList = computed(() => {
    const q = this.query().trim().toLowerCase();
    if (!q) return COUNTRIES;
    return COUNTRIES.filter(c => 
      c.name.toLowerCase().includes(q) || 
      c.dial.includes(q) || 
      c.code.toLowerCase().includes(q)
    );
  });

  constructor(private el: ElementRef) {}

  @HostListener('document:mousedown', ['$event'])
  onDocumentClick(event: MouseEvent) {
    if (!this.el.nativeElement.contains(event.target)) {
      this.open.set(false);
    }
  }

  select(c: Country) {
    this.valueChange.emit(c);
    this.open.set(false);
    this.query.set('');
  }
}
