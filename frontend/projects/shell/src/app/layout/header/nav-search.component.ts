import { Component, ElementRef, ViewChild, HostListener, Input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideSearch, LucideX } from '@lucide/angular';

@Component({
  selector: 'app-nav-search',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideSearch, LucideX],
  template: `
    <div #wrapRef [ngClass]="full ? 'relative w-full' : 'relative'">
      <div
        class="flex items-center transition-all duration-500 ease-out overflow-hidden"
        [ngClass]="full 
          ? 'h-12 w-full rounded-md bg-white border border-ink' 
          : (open() 
            ? 'w-[260px] xl:w-[340px] bg-white border border-hairline shadow-[0_10px_30px_-18px_rgba(0,0,0,0.4)] px-3 h-10'
            : 'w-10 h-10 border border-transparent')"
      >
        <!-- Full mode leading search icon -->
        <svg *ngIf="full" lucideSearch class="ml-4 h-5 w-5 shrink-0 text-muted-ink" aria-hidden="true"></svg>

        <!-- Not full mode toggle/search icon -->
        <button
          *ngIf="!full"
          type="button"
          aria-label="Search products"
          title="Search products"
          (click)="open() ? submit() : open.set(true)"
          class="grid h-10 w-10 shrink-0 place-items-center rounded-full transition-colors"
          [ngClass]="open() 
            ? 'text-brand'
            : (solid ? 'text-ink hover:bg-brand/10 hover:text-brand' : 'text-white hover:bg-white/10')"
        >
          <svg lucideSearch class="h-5 w-5"></svg>
        </button>

        <input
          #inputRef
          type="text"
          [(ngModel)]="query"
          (keydown.enter)="submit(); $event.preventDefault()"
          placeholder="Search products, SKU, category…"
          class="min-w-0 flex-1 bg-transparent text-sm text-ink placeholder:text-muted-ink outline-none transition-opacity duration-300"
          [ngClass]="full || open() ? 'opacity-100 px-3' : 'opacity-0 pointer-events-none w-0'"
        />

        <!-- Full mode trailing submit button -->
        <button
          *ngIf="full"
          type="button"
          aria-label="Search products"
          title="Search products"
          (click)="submit()"
          class="grid h-full w-16 shrink-0 place-items-center bg-brand text-white transition-colors hover:bg-brand-deep"
        >
          <svg lucideSearch class="h-5 w-5"></svg>
        </button>

        <!-- Not full mode close button -->
        <button
          *ngIf="!full && open()"
          type="button"
          aria-label="Close search"
          title="Clear search"
          (click)="query.set(''); open.set(false)"
          class="grid h-8 w-8 shrink-0 place-items-center rounded-full text-muted-ink hover:text-brand transition-colors"
        >
          <svg lucideX class="h-4 w-4"></svg>
        </button>
      </div>
    </div>
  `
})
export class NavSearchComponent {
  @Input() full = false;
  @Input() solid = true;
  @ViewChild('inputRef') inputRef!: ElementRef<HTMLInputElement>;
  @ViewChild('wrapRef') wrapRef!: ElementRef<HTMLDivElement>;

  open = signal(false);
  query = signal('');

  constructor(private router: Router) {}

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    if (!this.full && this.open() && !this.wrapRef.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    this.open.set(false);
  }

  submit() {
    const term = this.query().trim();
    if (!term) {
      this.inputRef?.nativeElement?.focus();
      return;
    }
    this.open.set(false);
    this.router.navigate(['/products'], { queryParams: { q: term } });
  }
}
