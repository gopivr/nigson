import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-auth-shell',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="min-h-screen bg-white flex flex-col lg:flex-row">
      <!-- Left: brand panel -->
      <aside class="relative hidden lg:flex flex-col justify-between w-[42%] bg-ink text-white overflow-hidden">
        <!-- We use a background color placeholder if the image fails, but try to load the asset -->
        <div class="absolute inset-0 h-full w-full bg-ink"></div>
        <img
          src="assets/hero-accessories.jpg"
          alt=""
          class="absolute inset-0 h-full w-full object-cover opacity-30"
          onerror="this.style.display='none'"
        />
        <div class="absolute inset-0 bg-gradient-to-br from-black via-black/70 to-brand/70"></div>
        <div class="relative p-10">
          <a routerLink="/" class="inline-flex items-center gap-2.5">
            <span class="grid h-10 w-10 place-items-center rounded-md bg-brand text-white font-extrabold text-lg">
              N
            </span>
            <span class="font-extrabold text-xl tracking-tight">NIGSON</span>
          </a>
        </div>
        <div class="relative p-10">
          <p class="text-[11px] font-semibold uppercase tracking-[0.25em] text-white/60">
            Nigson Group
          </p>
          <h2 class="mt-3 text-3xl md:text-4xl font-extrabold leading-tight max-w-md">
            Building Value.<br />Delivering Excellence.
          </h2>
          <p class="mt-4 text-white/70 max-w-md leading-relaxed">
            Trusted importer & distributor of premium phone accessories, FMCG products
            and real estate across Nigeria and Africa.
          </p>
          <div class="mt-10 flex items-center gap-6 text-xs text-white/60">
            <span>250k+ Customers</span>
            <span class="h-3 w-px bg-white/20"></span>
            <span>18 States</span>
            <span class="h-3 w-px bg-white/20"></span>
            <span>ISO Certified</span>
          </div>
        </div>
      </aside>

      <!-- Right: form -->
      <main class="flex-1 flex flex-col">
        <div class="lg:hidden p-6 border-b border-hairline">
          <a routerLink="/" class="inline-flex items-center gap-2.5">
            <span class="grid h-9 w-9 place-items-center rounded-md bg-brand text-white font-extrabold">N</span>
            <span class="font-extrabold text-lg tracking-tight text-ink">NIGSON</span>
          </a>
        </div>
        <div class="flex-1 flex items-center justify-center px-6 py-8 md:py-10">
          <div class="w-full max-w-md">
            <p *ngIf="eyebrow" class="text-[11px] font-semibold uppercase tracking-[0.25em] text-brand">{{ eyebrow }}</p>
            <h1 class="mt-2 text-3xl md:text-4xl font-extrabold leading-tight tracking-[-0.02em] text-ink">
              {{ title }}
            </h1>
            <p *ngIf="subtitle" class="mt-3 text-sm text-muted-ink leading-relaxed">{{ subtitle }}</p>
            <div class="mt-8">
              <ng-content></ng-content>
            </div>
            <div *ngIf="hasFooter" class="mt-8 text-sm text-center text-muted-ink">
              <ng-content select="[footer]"></ng-content>
            </div>
          </div>
        </div>
      </main>
    </div>
  `
})
export class AuthShellComponent {
  @Input() eyebrow?: string;
  @Input({ required: true }) title!: string;
  @Input() subtitle?: string;
  @Input() hasFooter: boolean = false;
}
