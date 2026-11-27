import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideTruck, LucideLock, LucideRefreshCcw, LucideHeadphones } from '@lucide/angular';

@Component({
  selector: 'app-service-benefits',
  standalone: true,
  imports: [CommonModule, LucideTruck, LucideLock, LucideRefreshCcw, LucideHeadphones],
  template: `
    <section class="bg-white py-6 md:py-8">
      <div class="container-page">
        <div
          class="relative overflow-hidden rounded-2xl md:rounded-3xl bg-brand px-4 py-8 md:px-10 md:py-10"
          style="background-image: repeating-linear-gradient(135deg, transparent, transparent 24px, rgba(255,255,255,0.04) 24px, rgba(255,255,255,0.04) 25px);"
        >
          <div class="relative grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div class="flex items-center gap-4 rounded-xl border border-white/20 bg-white/10 px-5 py-4 backdrop-blur-sm md:px-6 md:py-5">
              <div class="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-white md:h-12 md:w-12">
                <svg lucideTruck class="h-5 w-5 text-brand md:h-6 md:w-6" [strokeWidth]="2"></svg>
              </div>
              <div>
                <h3 class="text-sm font-bold text-white md:text-base">Fast & Reliable Delivery</h3>
                <p class="mt-0.5 text-xs text-white/75 md:text-sm">Across Nigeria</p>
              </div>
            </div>

            <div class="flex items-center gap-4 rounded-xl border border-white/20 bg-white/10 px-5 py-4 backdrop-blur-sm md:px-6 md:py-5">
              <div class="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-white md:h-12 md:w-12">
                <svg lucideLock class="h-5 w-5 text-brand md:h-6 md:w-6" [strokeWidth]="2"></svg>
              </div>
              <div>
                <h3 class="text-sm font-bold text-white md:text-base">Secure Payments</h3>
                <p class="mt-0.5 text-xs text-white/75 md:text-sm">Protected checkout</p>
              </div>
            </div>

            <div class="flex items-center gap-4 rounded-xl border border-white/20 bg-white/10 px-5 py-4 backdrop-blur-sm md:px-6 md:py-5">
              <div class="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-white md:h-12 md:w-12">
                <svg lucideRefreshCcw class="h-5 w-5 text-brand md:h-6 md:w-6" [strokeWidth]="2"></svg>
              </div>
              <div>
                <h3 class="text-sm font-bold text-white md:text-base">Easy Returns</h3>
                <p class="mt-0.5 text-xs text-white/75 md:text-sm">Hassle-free process</p>
              </div>
            </div>

            <div class="flex items-center gap-4 rounded-xl border border-white/20 bg-white/10 px-5 py-4 backdrop-blur-sm md:px-6 md:py-5">
              <div class="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-white md:h-12 md:w-12">
                <svg lucideHeadphones class="h-5 w-5 text-brand md:h-6 md:w-6" [strokeWidth]="2"></svg>
              </div>
              <div>
                <h3 class="text-sm font-bold text-white md:text-base">Customer Support</h3>
                <p class="mt-0.5 text-xs text-white/75 md:text-sm">We're here to help</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  `
})
export class ServiceBenefitsComponent {}
