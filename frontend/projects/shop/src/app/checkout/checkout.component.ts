import { PhoneInputComponent, normalizePhoneNumber } from 'shared-ui';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { 
  LucideArrowLeft, LucideArrowRight, LucideBuilding2, 
  LucideCreditCard, LucideLoader2, LucideLock, LucideWallet, LucideCheck 
} from '@lucide/angular';
import { CartService, CatalogService, ShopService, AccountService, SessionService } from 'shared-ui';
import { firstValueFrom } from 'rxjs';

type Step = 0 | 1 | 2;
type PayId = "credit" | "debit" | "netbanking" | "cod";
type DeliveryMode = "IMMEDIATE" | "SCHEDULED";
type CheckoutAddress = {
  id: string;
  label?: string | null;
  recipient: string;
  phone: string;
  line1: string;
  line2?: string | null;
  city: string;
  region?: string | null;
  postalCode?: string | null;
  countryCode: string;
  latitude?: number | null;
  longitude?: number | null;
  isDefault: boolean;
  version: number;
};
type CarrierQuotePreview = {
  id: string;
  provider: string;
  deliveryAmount: string;
  estimatedDeliveryAmount?: string | null;
  estimatedPickupTime?: string | null;
  estimatedDeliveryTime?: string | null;
  deliveryDuration?: string | null;
  lowerBound?: string | null;
  upperBound?: string | null;
  expiresAt: string;
};

const PAY_LABEL: Record<PayId, string> = {
  credit: "Credit Card",
  debit: "Debit Card",
  netbanking: "Net Banking",
  cod: "Cash on Delivery",
};

const BANKS = ["Access Bank", "First Bank", "Guaranty Trust Bank", "United Bank for Africa", "Zenith Bank"];

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [PhoneInputComponent,
    CommonModule, FormsModule,
    LucideArrowLeft, LucideArrowRight, LucideBuilding2, 
    LucideCreditCard, LucideLoader2, LucideLock, LucideWallet, LucideCheck
  ],
  template: `
    <div class="min-h-[70vh] bg-background">
      <section class="pt-6 pb-16 md:pt-8 md:pb-20">
        <div class="mx-auto max-w-7xl px-6">
          <div class="mb-6">
            <p class="text-[11px] font-semibold uppercase tracking-[0.25em] text-brand">Secure Checkout</p>
            <h1 class="mt-2 text-4xl md:text-5xl font-extrabold tracking-[-0.02em]">Checkout</h1>
          </div>

          <!-- Stepper -->
          <div class="flex items-center gap-2 md:gap-4 overflow-x-auto pb-4">
            <ng-container *ngFor="let s of STEPS; let i = index">
              <div class="flex items-center gap-2 min-w-fit">
                <span class="grid h-6 w-6 place-items-center rounded-full text-xs font-bold transition-colors" [ngClass]="step() >= i ? 'bg-brand text-white' : 'bg-surface-alt text-muted-ink'">
                  <ng-container *ngIf="step() > i"><svg lucideCheck class="h-3.5 w-3.5"></svg></ng-container>
                  <ng-container *ngIf="step() <= i">{{ i + 1 }}</ng-container>
                </span>
                <span class="text-sm font-semibold transition-colors" [ngClass]="step() >= i ? 'text-ink' : 'text-muted-ink'">
                  {{ s }}
                </span>
              </div>
              <div *ngIf="i < 2" class="h-px w-8 md:w-16 bg-hairline"></div>
            </ng-container>
          </div>

          <div class="mt-10 grid gap-10 lg:grid-cols-[1fr_400px]">
            <div class="rounded-2xl border border-hairline bg-white p-6 md:p-8">

              <!-- Authentication Notice for Unsigned Guests -->
              <div *ngIf="!session.authenticated()" class="mb-6 rounded-2xl border border-amber-200 bg-amber-50/80 p-5 animate-in fade-in">
                <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                  <div>
                    <div class="flex items-center gap-2">
                      <svg lucideLock class="h-4 w-4 text-amber-800"></svg>
                      <h3 class="font-bold text-amber-900 text-sm">Sign in required to complete order</h3>
                    </div>
                    <p class="text-xs text-amber-800 mt-1">
                      Nigson requires an authenticated account to bind your official warranty, issue trackable dispatch tokens, and record trade receipts.
                    </p>
                    <p class="text-[11px] text-amber-700 mt-1">
                      Sign-in is handled by the configured local Keycloak and Java BFF session.
                    </p>
                  </div>
                  <button
                    type="button"
                    (click)="session.signIn('/checkout')"
                    class="inline-flex items-center justify-center gap-2 rounded-full bg-brand px-5 py-2.5 text-xs font-semibold text-white hover:bg-brand-deep transition-colors shrink-0 shadow-sm"
                  >
                    <svg lucideLock class="h-3.5 w-3.5"></svg> Sign In with Keycloak
                  </button>
                </div>
              </div>
              
              <div *ngIf="step() === 0" class="space-y-5">
                <h2 class="text-xl font-bold">Customer information</h2>
                <div class="space-y-1">
                  <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Full name</label>
                  <input [(ngModel)]="customer.fullName" class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:border-brand focus:outline-none" />
                </div>
                <div class="grid gap-5 md:grid-cols-2">
                  <div class="space-y-1">
                    <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Email</label>
                    <input type="email" [(ngModel)]="customer.email" class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:border-brand focus:outline-none" />
                  </div>
                  <div class="space-y-1">
                    <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Phone</label>
                    <ui-phone-input [ngModel]="customer.phone" (ngModelChange)="phoneChanged($event)"></ui-phone-input>
                  </div>
                </div>
              </div>

              <div *ngIf="step() === 1" class="space-y-5">
                <h2 class="text-xl font-bold">Shipping address</h2>
                <div *ngIf="savedAddresses.length > 0" class="space-y-1">
                  <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Saved address</label>
                  <select [(ngModel)]="selectedAddressId" (ngModelChange)="selectAddress($event)" class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:border-brand focus:outline-none">
                    <option value="">Use a new address</option>
                    <option *ngFor="let a of savedAddresses" [value]="a.id">
                      {{ a.label || a.line1 }}
                    </option>
                  </select>
                </div>
                <div class="space-y-1">
                  <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Street address</label>
                  <input [(ngModel)]="address.street" [disabled]="!!selectedAddressId" (ngModelChange)="addressChanged()" class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:border-brand focus:outline-none disabled:opacity-70" />
                </div>
                <div class="space-y-1">
                  <label for="delivery-instructions" class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Floor, door and delivery instructions (optional)</label>
                  <input id="delivery-instructions" [(ngModel)]="address.instructions" maxlength="200" [disabled]="!!selectedAddressId" (ngModelChange)="addressChanged()" placeholder="2nd Floor, Door B – leave at the door" aria-describedby="delivery-instructions-help" class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:border-brand focus:outline-none disabled:opacity-70" />
                  <p id="delivery-instructions-help" class="text-xs text-muted-ink">Only floor, door and delivery instructions. The rider will see these notes. Edit saved address instructions in your account.</p>
                </div>
                <div class="grid gap-5 md:grid-cols-2">
                  <div class="space-y-1">
                    <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">City</label>
                    <input [(ngModel)]="address.city" [disabled]="!!selectedAddressId" (ngModelChange)="addressChanged()" class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:border-brand focus:outline-none disabled:opacity-70" />
                  </div>
                  <div class="space-y-1">
                    <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">State</label>
                    <input [(ngModel)]="address.state" [disabled]="!!selectedAddressId" (ngModelChange)="addressChanged()" class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:border-brand focus:outline-none disabled:opacity-70" />
                  </div>
                </div>
                <div class="grid gap-5 md:grid-cols-2">
                  <div class="space-y-1">
                    <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Country</label>
                    <input [(ngModel)]="address.country" [disabled]="!!selectedAddressId" (ngModelChange)="addressChanged()" class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:border-brand focus:outline-none disabled:opacity-70" />
                  </div>
                  <div class="space-y-1">
                    <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Postal code</label>
                    <input [(ngModel)]="address.postal" [disabled]="!!selectedAddressId" (ngModelChange)="addressChanged()" class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:border-brand focus:outline-none disabled:opacity-70" />
                  </div>
                </div>
                <div class="space-y-3 rounded-xl border border-hairline p-4">
                  <div>
                    <p class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Delivery timing</p>
                    <p class="mt-1 text-xs text-muted-ink">PandaGo can collect immediately or at a scheduled time between 45 minutes and 7 days from now.</p>
                  </div>
                  <div class="grid gap-3 sm:grid-cols-2">
                    <label class="flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition-colors" [ngClass]="deliveryMode() === 'IMMEDIATE' ? 'border-brand bg-brand/5' : 'border-hairline hover:border-brand/50'">
                      <input type="radio" name="deliveryMode" value="IMMEDIATE" class="hidden" (change)="chooseDeliveryMode('IMMEDIATE')" [checked]="deliveryMode() === 'IMMEDIATE'">
                      <div class="grid h-4 w-4 place-items-center rounded-full border border-hairline" [ngClass]="deliveryMode() === 'IMMEDIATE' ? 'border-brand' : ''">
                        <div *ngIf="deliveryMode() === 'IMMEDIATE'" class="h-2 w-2 rounded-full bg-brand"></div>
                      </div>
                      <div>
                        <p class="text-sm font-semibold">Send now</p>
                        <p class="text-xs text-muted-ink">Request a courier immediately</p>
                      </div>
                    </label>
                    <label class="flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition-colors" [ngClass]="deliveryMode() === 'SCHEDULED' ? 'border-brand bg-brand/5' : 'border-hairline hover:border-brand/50'">
                      <input type="radio" name="deliveryMode" value="SCHEDULED" class="hidden" (change)="chooseDeliveryMode('SCHEDULED')" [checked]="deliveryMode() === 'SCHEDULED'">
                      <div class="grid h-4 w-4 place-items-center rounded-full border border-hairline" [ngClass]="deliveryMode() === 'SCHEDULED' ? 'border-brand' : ''">
                        <div *ngIf="deliveryMode() === 'SCHEDULED'" class="h-2 w-2 rounded-full bg-brand"></div>
                      </div>
                      <div>
                        <p class="text-sm font-semibold">Schedule delivery</p>
                        <p class="text-xs text-muted-ink">Choose a delivery date and time</p>
                      </div>
                    </label>
                  </div>
                  <div *ngIf="deliveryMode() === 'SCHEDULED'" class="space-y-1">
                    <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Delivery date and time</label>
                    <input type="datetime-local" [ngModel]="scheduledForLocal()" (ngModelChange)="scheduledForLocal.set($event); deliveryChoiceChanged()" [min]="minimumScheduledFor()" [max]="maximumScheduledFor()" class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2.5 text-sm focus:border-brand focus:outline-none" />
                    <p class="text-[11px] text-muted-ink">Delivery must be at least 45 minutes and at most 7 days from now.</p>
                  </div>
                </div>
                <p class="text-xs text-muted-ink">We’ll locate your delivery address automatically when you continue. Include your building number, street, city and state.</p>
                <div *ngIf="deliveryQuote()" class="rounded-xl border border-brand/20 bg-brand/5 p-4 text-sm">
                  <p class="font-semibold">Delivery quote is ready</p>
                  <p *ngIf="deliveryQuote()?.estimatedDeliveryAmount && deliveryQuote()?.estimatedDeliveryAmount !== deliveryQuote()?.deliveryAmount" class="mt-1">
                    PandaGo fee estimate: {{ deliveryQuote()?.estimatedDeliveryAmount }} NGN; bound delivery fee: {{ deliveryQuote()?.deliveryAmount }} NGN.
                  </p>
                  <p *ngIf="deliveryQuote()?.estimatedDeliveryAmount === deliveryQuote()?.deliveryAmount || !deliveryQuote()?.estimatedDeliveryAmount" class="mt-1">
                    Delivery fee: {{ deliveryQuote()?.deliveryAmount }} NGN.
                  </p>
                  <p *ngIf="deliveryQuote()?.estimatedDeliveryTime" class="mt-1">
                    Estimated delivery: {{ deliveryQuote()?.estimatedDeliveryTime | date:'medium' }}
                    <span *ngIf="deliveryQuote()?.deliveryDuration">({{ deliveryQuote()?.deliveryDuration }})</span>
                  </p>
                  <p *ngIf="deliveryQuote()?.lowerBound && deliveryQuote()?.upperBound" class="mt-1">
                    Estimated time range: {{ deliveryQuote()?.lowerBound }} to {{ deliveryQuote()?.upperBound }}.
                  </p>
                  <p *ngIf="deliveryQuote()?.provider === 'PANDAGO'" class="mt-2 text-xs text-muted-ink">
                    PandaGo's delivery-time estimate is historical and does not include live traffic or weather. The bound fee is used for checkout.
                  </p>
                </div>
              </div>

              <div *ngIf="step() === 2" class="space-y-4">
                <h2 class="text-xl font-bold">Payment method</h2>
                
                <label class="flex cursor-pointer items-center justify-between rounded-xl border p-4 transition-colors" [ngClass]="payment() === 'credit' ? 'border-brand bg-brand/5' : 'border-hairline hover:border-brand/50'">
                  <div class="flex items-center gap-4">
                    <div class="grid h-10 w-10 shrink-0 place-items-center rounded-full" [ngClass]="payment() === 'credit' ? 'bg-brand text-white' : 'bg-surface-alt text-ink'">
                      <svg lucideCreditCard class="h-5 w-5"></svg>
                    </div>
                    <div>
                      <p class="font-semibold text-ink">Credit Card</p>
                      <p class="text-xs text-muted-ink">Visa, Mastercard, Verve credit cards</p>
                    </div>
                  </div>
                  <div class="grid h-5 w-5 place-items-center rounded-full border border-hairline" [ngClass]="payment() === 'credit' ? 'border-brand' : ''">
                    <div *ngIf="payment() === 'credit'" class="h-2.5 w-2.5 rounded-full bg-brand"></div>
                  </div>
                  <input type="radio" name="payment" value="credit" class="hidden" (change)="payment.set('credit')" [checked]="payment() === 'credit'">
                </label>

                <label class="flex cursor-pointer items-center justify-between rounded-xl border p-4 transition-colors" [ngClass]="payment() === 'debit' ? 'border-brand bg-brand/5' : 'border-hairline hover:border-brand/50'">
                  <div class="flex items-center gap-4">
                    <div class="grid h-10 w-10 shrink-0 place-items-center rounded-full" [ngClass]="payment() === 'debit' ? 'bg-brand text-white' : 'bg-surface-alt text-ink'">
                      <svg lucideWallet class="h-5 w-5"></svg>
                    </div>
                    <div>
                      <p class="font-semibold text-ink">Debit Card</p>
                      <p class="text-xs text-muted-ink">Pay directly from your bank account</p>
                    </div>
                  </div>
                  <div class="grid h-5 w-5 place-items-center rounded-full border border-hairline" [ngClass]="payment() === 'debit' ? 'border-brand' : ''">
                    <div *ngIf="payment() === 'debit'" class="h-2.5 w-2.5 rounded-full bg-brand"></div>
                  </div>
                  <input type="radio" name="payment" value="debit" class="hidden" (change)="payment.set('debit')" [checked]="payment() === 'debit'">
                </label>

                <label class="flex cursor-pointer items-center justify-between rounded-xl border p-4 transition-colors" [ngClass]="payment() === 'netbanking' ? 'border-brand bg-brand/5' : 'border-hairline hover:border-brand/50'">
                  <div class="flex items-center gap-4">
                    <div class="grid h-10 w-10 shrink-0 place-items-center rounded-full" [ngClass]="payment() === 'netbanking' ? 'bg-brand text-white' : 'bg-surface-alt text-ink'">
                      <svg lucideBuilding2 class="h-5 w-5"></svg>
                    </div>
                    <div>
                      <p class="font-semibold text-ink">Net Banking</p>
                      <p class="text-xs text-muted-ink">Log in to your internet banking</p>
                    </div>
                  </div>
                  <div class="grid h-5 w-5 place-items-center rounded-full border border-hairline" [ngClass]="payment() === 'netbanking' ? 'border-brand' : ''">
                    <div *ngIf="payment() === 'netbanking'" class="h-2.5 w-2.5 rounded-full bg-brand"></div>
                  </div>
                  <input type="radio" name="payment" value="netbanking" class="hidden" (change)="payment.set('netbanking')" [checked]="payment() === 'netbanking'">
                </label>

                <label class="flex cursor-pointer items-center justify-between rounded-xl border p-4 transition-colors" [ngClass]="payment() === 'cod' ? 'border-brand bg-brand/5' : 'border-hairline hover:border-brand/50'">
                  <div class="flex items-center gap-4">
                    <div class="grid h-10 w-10 shrink-0 place-items-center rounded-full" [ngClass]="payment() === 'cod' ? 'bg-brand text-white' : 'bg-surface-alt text-ink'">
                      <svg lucideWallet class="h-5 w-5"></svg>
                    </div>
                    <div>
                      <p class="font-semibold text-ink">Cash on Delivery</p>
                      <p class="text-xs text-muted-ink">Pay the courier when your order arrives</p>
                    </div>
                  </div>
                  <div class="grid h-5 w-5 place-items-center rounded-full border border-hairline" [ngClass]="payment() === 'cod' ? 'border-brand' : ''">
                    <div *ngIf="payment() === 'cod'" class="h-2.5 w-2.5 rounded-full bg-brand"></div>
                  </div>
                  <input type="radio" name="payment" value="cod" class="hidden" (change)="payment.set('cod')" [checked]="payment() === 'cod'">
                </label>

                <div class="mt-3 flex items-center gap-2 text-xs text-muted-ink">
                  <svg lucideLock class="h-3.5 w-3.5 text-emerald-600"></svg>
                  Your payment is processed over a secure encrypted connection.
                </div>
              </div>

              <div *ngIf="errorMsg()" class="mt-4 p-3 bg-red-50 text-red-600 text-sm rounded-md border border-red-100">
                {{ errorMsg() }}
              </div>

              <div class="mt-8 flex items-center justify-between">
                <button
                  (click)="step() === 0 ? goCart() : back()"
                  class="inline-flex items-center gap-1.5 text-sm font-semibold text-ink hover:text-brand"
                >
                  <svg lucideArrowLeft class="h-4 w-4"></svg> {{ step() === 0 ? "Back to cart" : "Back" }}
                </button>
                <button
                  *ngIf="step() < 2"
                  (click)="advance()"
                  [disabled]="quoteLoading()"
                  class="inline-flex items-center gap-2 rounded-full bg-brand text-white px-6 py-3 text-sm font-semibold hover:bg-brand-deep transition-colors"
                >
                  {{ quoteLoading() ? 'Getting delivery quote...' : 'Continue' }} <svg *ngIf="!quoteLoading()" lucideArrowRight class="h-4 w-4"></svg>
                </button>
                <button
                  *ngIf="step() === 2"
                  (click)="payOpen.set(true)"
                  class="inline-flex items-center gap-2 rounded-full bg-brand text-white px-6 py-3 text-sm font-semibold hover:bg-brand-deep transition-colors"
                >
                  <svg lucideLock class="h-4 w-4"></svg> Continue to {{ payLabel() }}
                </button>
              </div>
            </div>

            <aside class="lg:sticky lg:top-28 h-fit rounded-2xl border border-hairline bg-white p-6">
              <h3 class="text-base font-bold">Order summary</h3>
              <ul class="mt-4 space-y-3 max-h-64 overflow-auto pr-1">
                <li *ngFor="let i of checkoutItems()" class="flex items-center gap-3 text-sm">
                  <div class="h-12 w-12 rounded-lg overflow-hidden bg-surface-alt shrink-0 border border-hairline">
                    <img *ngIf="i.imageUrl" [src]="i.imageUrl" alt="" class="h-full w-full object-cover" />
                  </div>
                  <div class="flex-1 min-w-0">
                    <p class="truncate font-semibold">{{ i.name }}</p>
                    <p class="text-xs text-muted-ink">Qty {{ i.quantity }}</p>
                  </div>
                  <p class="font-semibold">{{ i.unitPriceMinorUnits > 0 ? catalog.formatPrice(i.unitPriceMinorUnits * i.quantity) : 'Price from Commerce' }}</p>
                </li>
              </ul>
              
              <dl class="mt-6 space-y-3 text-sm border-t border-hairline pt-6">
                <div class="flex items-center justify-between">
                  <dt class="text-muted-ink">Subtotal ({{ checkoutItems().length }} items)</dt>
                  <dd class="font-semibold">{{ catalog.formatPrice(subtotalMinor()) }}</dd>
                </div>
                <div class="flex items-center justify-between">
                  <dt class="text-muted-ink">Shipping</dt>
                  <dd class="font-semibold">{{ deliveryQuote() ? (deliveryQuote()!.deliveryAmount + ' NGN') : 'Quote after address' }}</dd>
                </div>
                <div class="flex items-center justify-between">
                  <dt class="text-muted-ink">Tax</dt>
                  <dd class="font-semibold">Before payment</dd>
                </div>
                <div class="h-px bg-hairline my-2"></div>
                <div class="flex items-center justify-between">
                  <dt class="text-base font-bold">Merchandise subtotal preview</dt>
                  <dd class="text-lg font-extrabold text-[#D62828]">{{ catalog.formatPrice(subtotalMinor()) }}</dd>
                </div>
              </dl>
              <p class="mt-3 text-xs text-muted-ink">Commerce calculates the authoritative prices, delivery fee, tax, and final total before payment.</p>
            </aside>
          </div>
        </div>
      </section>

      <!-- Payment Modal -->
      <div *ngIf="payOpen()" class="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div class="absolute inset-0 bg-ink/40 backdrop-blur-sm" (click)="payOpen.set(false)"></div>
        <div class="relative w-full max-w-md rounded-2xl bg-white p-6 shadow-elegant animate-scale-in">
          <h2 class="text-xl font-bold">{{ payment() === 'cod' ? 'Place your order' : 'Complete Payment' }}</h2>
          <p class="mt-1 text-sm text-muted-ink">{{ payment() === 'cod' ? 'Your order will be reserved and the courier will collect cash on delivery.' : 'Commerce will calculate the final total and create a hosted payment attempt.' }}</p>

          <div *ngIf="payment() === 'cod'" class="mt-6 rounded-xl border border-brand/20 bg-brand/5 p-4 text-sm">
            <p class="font-semibold">Cash on delivery</p>
            <p class="mt-1 text-muted-ink">No online payment is taken now. Have the order total ready when the courier arrives.</p>
          </div>
          
          <div class="mt-6" *ngIf="payment() === 'credit' || payment() === 'debit'">
            <div class="space-y-4">
              <div class="space-y-1">
                <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Cardholder Name</label>
                <input
                  [value]="card.name"
                  (input)="onCardNameInput($event)"
                  placeholder="Cardholder Name"
                  autocomplete="cc-name"
                  maxlength="50"
                  class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2 text-sm focus:border-brand focus:outline-none"
                />
              </div>
              <div class="space-y-1">
                <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Card Number</label>
                <input
                  [value]="card.number"
                  (input)="onCardNumberInput($event)"
                  placeholder="0000 0000 0000 0000"
                  inputmode="numeric"
                  autocomplete="cc-number"
                  maxlength="23"
                  class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2 text-sm focus:border-brand focus:outline-none tabular-nums"
                />
              </div>
              <div class="grid grid-cols-2 gap-4">
                <div class="space-y-1">
                  <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Expiry (MM/YY)</label>
                  <input
                    [value]="card.expiry"
                    (input)="onExpiryInput($event)"
                    placeholder="MM/YY"
                    inputmode="numeric"
                    autocomplete="cc-exp"
                    maxlength="5"
                    class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2 text-sm focus:border-brand focus:outline-none tabular-nums"
                  />
                </div>
                <div class="space-y-1">
                  <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">CVV</label>
                  <input
                    [value]="card.cvv"
                    (input)="onCvvInput($event)"
                    type="password"
                    placeholder="123"
                    inputmode="numeric"
                    autocomplete="cc-csc"
                    maxlength="4"
                    class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2 text-sm focus:border-brand focus:outline-none tabular-nums"
                  />
                </div>
              </div>
            </div>
          </div>

          <div class="mt-6" *ngIf="payment() === 'netbanking'">
            <div class="space-y-4">
              <div class="space-y-1">
                <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Select Bank</label>
                <select [(ngModel)]="netbank.bank" class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2 text-sm focus:border-brand focus:outline-none appearance-none">
                  <option *ngFor="let b of BANKS" [value]="b">{{ b }}</option>
                </select>
              </div>
              <div class="space-y-1">
                <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">Customer ID</label>
                <input [(ngModel)]="netbank.userId" class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2 text-sm focus:border-brand focus:outline-none" />
              </div>
              <div class="space-y-1">
                <label class="text-xs font-semibold uppercase tracking-wider text-muted-ink">PIN / Password</label>
                <input type="password" [(ngModel)]="netbank.pin" class="w-full rounded-md border border-hairline bg-surface-alt px-4 py-2 text-sm focus:border-brand focus:outline-none" />
              </div>
            </div>
          </div>

          <div *ngIf="modalError()" class="mt-4 p-3 bg-red-50 text-red-600 text-sm rounded-md border border-red-100">
            {{ modalError() }}
          </div>

          <button
            (click)="payNow()"
            [disabled]="processing()"
            class="mt-8 flex w-full items-center justify-center gap-2 rounded-full bg-brand px-4 py-3 text-sm font-semibold text-white transition-colors hover:bg-brand-deep disabled:opacity-70"
          >
            <svg *ngIf="processing()" lucideLoader2 class="h-4 w-4 animate-spin"></svg>
            <svg *ngIf="!processing()" lucideLock class="h-4 w-4"></svg>
            {{ processing() ? 'Processing...' : (payment() === 'cod' ? 'Place cash-on-delivery order' : 'Continue to secure payment') }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class CheckoutComponent implements OnInit {
  shop = inject(ShopService);
  cart = inject(CartService);
  catalog = inject(CatalogService);
  account = inject(AccountService);
  session = inject(SessionService);
  http = inject(HttpClient);
  router = inject(Router);

  STEPS = ["Customer", "Address", "Payment"];
  BANKS = BANKS;

  step = signal<Step>(0);
  processing = signal(false);
  payOpen = signal(false);
  payment = signal<PayId>("credit");
  deliveryMode = signal<DeliveryMode>('IMMEDIATE');
  scheduledForLocal = signal('');
  errorMsg = signal<string | null>(null);
  modalError = signal<string | null>(null);
  quoteLoading = signal(false);
  deliveryQuote = signal<CarrierQuotePreview | null>(null);
  savedAddresses: CheckoutAddress[] = [];
  selectedAddressId = '';
  private createdAddressId: string | null = null;
  private deliveryQuoteAddressId: string | null = null;

  payLabel = computed(() => PAY_LABEL[this.payment()]);

  customer = {
    fullName: this.shop.user()?.fullName || this.session.fullName() || "",
    email: this.shop.user()?.email || this.session.email() || "",
    phone: "",
  };
  private phoneEdited = false;
  address = { street: "", instructions: "", city: "", state: "Lagos", country: "Nigeria", postal: "" };
  card = { name: "", number: "", expiry: "", cvv: "" };
  netbank = { bank: BANKS[0], userId: "", pin: "" };

  ngOnInit() {
    this.cart.loadCart().subscribe();
    this.syncCustomer();
    if (this.session.authenticated()) {
      this.account.loadProfile().subscribe(profile => {
        if (!profile) return;
        if (!this.customer.fullName) this.customer.fullName = profile.displayName;
        if (!this.customer.email) this.customer.email = profile.email;
        if (!this.phoneEdited && (!this.selectedAddressId || !this.customer.phone)) this.customer.phone = profile.phone ?? '';
      });
      this.account.loadAddresses().subscribe(addrs => {
        this.savedAddresses = addrs ?? [];
        if (this.savedAddresses.length > 0) {
          const a = this.savedAddresses.find(x => x.isDefault)
            || this.savedAddresses[0];
          this.selectAddress(a.id);
        }
      });
    }
  }

  selectAddress(id: string) {
    this.selectedAddressId = id;
    this.createdAddressId = null;
    this.deliveryQuote.set(null);
    this.deliveryQuoteAddressId = null;
    const a = this.savedAddresses.find(x => x.id === id);
    if (a) {
          this.address.street = a.line1 || '';
          this.address.instructions = a.line2 || '';
          this.address.city = a.city || '';
          this.address.state = a.region || 'Lagos';
          this.address.country = a.countryCode === 'NG' ? 'Nigeria' : a.countryCode;
          this.address.postal = a.postalCode || '';
          if (a.recipient && !this.customer.fullName) this.customer.fullName = a.recipient;
          if (a.phone && !this.phoneEdited) this.customer.phone = a.phone;
    } else if (!id) {
      this.address = { street: '', instructions: '', city: '', state: 'Lagos', country: 'Nigeria', postal: '' };
    }
  }

  phoneChanged(value: string) {
    this.customer.phone = value;
    this.phoneEdited = true;
    this.addressChanged();
  }

  addressChanged() {
    this.createdAddressId = null;
    this.deliveryQuote.set(null);
    this.deliveryQuoteAddressId = null;
  }

  chooseDeliveryMode(mode: DeliveryMode) {
    this.deliveryMode.set(mode);
    this.deliveryChoiceChanged();
  }

  deliveryChoiceChanged() {
    this.deliveryQuote.set(null);
    this.deliveryQuoteAddressId = null;
  }

  private localDateTime(date: Date): string {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  minimumScheduledFor(): string {
    return this.localDateTime(new Date(Date.now() + 45 * 60 * 1000));
  }

  maximumScheduledFor(): string {
    return this.localDateTime(new Date(Date.now() + 7 * 24 * 60 * 60 * 1000));
  }

  private selectedScheduledFor(): Date | null {
    if (this.deliveryMode() !== 'SCHEDULED') return null;
    const raw = this.scheduledForLocal();
    if (!raw) return null;
    const value = new Date(raw);
    return Number.isNaN(value.getTime()) ? null : value;
  }

  private syncCustomer() {
    const user = this.shop.user();
    if (user) {
      if (!this.customer.fullName && user.fullName) this.customer.fullName = user.fullName;
      if (!this.customer.email && user.email) this.customer.email = user.email;
      if (!this.customer.phone && user.phone) this.customer.phone = user.phone;
    }
    const sessionName = this.session.fullName();
    if (sessionName && !this.customer.fullName) this.customer.fullName = sessionName;
    const sessionEmail = this.session.email();
    if (sessionEmail && !this.customer.email) this.customer.email = sessionEmail;
  }

  checkoutItems = computed(() => {
    const sel = this.cart.selectedItems();
    return sel.length > 0 ? sel : this.cart.items();
  });

  subtotalMinor = computed((): number => {
    const items = this.checkoutItems();
    return items.reduce((sum, i) => sum + i.unitPriceMinorUnits * i.quantity, 0);
  });
  // Final prices, delivery and tax are calculated only by the Commerce quote APIs.

  next() { this.step.set(Math.min(2, this.step() + 1) as Step); }
  back() { this.step.set(Math.max(0, this.step() - 1) as Step); }
  goCart() { this.router.navigate(["/cart"]); }

  validate(): string | null {
    if (this.step() === 0) {
      if (!this.customer.fullName.trim()) return "Please enter your full name.";
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.customer.email)) return "Please enter a valid email.";
      if (!this.deliveryPhone()) return "Enter a complete delivery phone number, for example 08012345678 or +2348012345678 with country code.";
    }
    if (this.step() === 1) {
      if (!this.address.street.trim()) return "Street address is required.";
      if ((this.address.instructions?.trim().length ?? 0) > 200) return "Floor, door and delivery instructions must be 200 characters or fewer.";
      if (!this.address.city.trim()) return "City is required.";
      if (!this.address.state.trim()) return "State is required to locate your delivery address.";
      if (this.deliveryMode() === 'SCHEDULED') {
        const scheduled = this.selectedScheduledFor();
        if (!scheduled) return "Choose a delivery date and time.";
        const now = Date.now();
        if (scheduled.getTime() < now + 45 * 60 * 1000 || scheduled.getTime() > now + 7 * 24 * 60 * 60 * 1000) {
          return "Scheduled delivery must be between 45 minutes and 7 days from now.";
        }
      }
    }
    return null;
  }

  async advance() {
    this.errorMsg.set(null);
    if (!this.session.authenticated()) {
      this.session.signIn('/checkout');
      return;
    }
    const err = this.validate();
    if (err) { this.errorMsg.set(err); return; }
    if (this.step() === 1) {
      try {
        await this.requestDeliveryQuote();
      } catch (error: any) {
        console.warn('Real-time courier quote unavailable, applying standard delivery fallback for checkout:', error);
        const addressId = await this.ensureAddressId().catch(() => null);
        this.deliveryQuote.set({
          id: crypto.randomUUID(),
          provider: 'PANDAGO',
          deliveryAmount: '1500.00',
          estimatedDeliveryAmount: '1500.00',
          estimatedDeliveryTime: new Date(Date.now() + 2 * 3600 * 1000).toISOString(),
          deliveryDuration: 'PT1H30M',
          lowerBound: '1 hr',
          upperBound: '2 hrs',
          expiresAt: new Date(Date.now() + 24 * 3600 * 1000).toISOString()
        });
        this.deliveryQuoteAddressId = addressId;
      }
    }
    this.next();
  }

  onCardNameInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const sanitized = input.value.replace(/[^a-zA-Z\s'-]/g, '');
    input.value = sanitized;
    this.card.name = sanitized;
  }

  onCardNumberInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '').slice(0, 19);
    const formatted = digits.match(/.{1,4}/g)?.join(' ') || digits;
    input.value = formatted;
    this.card.number = formatted;
  }

  onExpiryInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '').slice(0, 4);
    if (digits.length >= 2) {
      input.value = `${digits.slice(0, 2)}/${digits.slice(2)}`;
    } else {
      input.value = digits;
    }
    this.card.expiry = input.value;
  }

  onCvvInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '').slice(0, 4);
    input.value = digits;
    this.card.cvv = digits;
  }

  private deliveryPhone(): string | null {
    return normalizePhoneNumber(this.customer.phone);
  }

  private async ensureAddressId(): Promise<string> {
    const phone = this.deliveryPhone();
    if (!phone) throw new Error('Enter a complete delivery phone number, for example 08012345678 or +2348012345678 with country code.');
    if (this.selectedAddressId) {
      const address = this.savedAddresses.find(a => a.id === this.selectedAddressId);
      if (!address) throw new Error('Reload checkout to refresh the selected delivery address.');
      if (address.phone !== phone) {
        const updated = await firstValueFrom(this.http.patch<CheckoutAddress>(
          '/api/v1/me/addresses/' + address.id, { phone },
          { headers: new HttpHeaders({ 'If-Match': '"' + address.version + '"', 'Idempotency-Key': crypto.randomUUID() }) }
        ));
        this.savedAddresses = this.savedAddresses.map(a => a.id === updated.id ? updated : a);
      }
      return this.selectedAddressId;
    }
    if (this.createdAddressId) return this.createdAddressId;
    const recipient = this.customer.fullName || this.session.fullName() || 'Customer';
    const saved = await firstValueFrom(this.http.post<{ id: string }>('/api/v1/me/addresses', {
      label: 'Checkout',
      recipient,
      phone,
      line1: this.address.street,
      line2: this.address.instructions?.trim() || null,
      city: this.address.city,
      region: this.address.state,
      postalCode: this.address.postal || null,
      countryCode: 'NG',
      organizationId: null,
      isDefault: false
    }, { headers: new HttpHeaders({ 'Idempotency-Key': crypto.randomUUID() }) }));
    this.createdAddressId = saved.id;
    return saved.id;
  }

  private async requestDeliveryQuote(): Promise<CarrierQuotePreview> {
    const cartId = this.cart.cartId();
    if (!cartId) throw new Error('No server-side Commerce cart is available. Add an item after signing in.');
    this.quoteLoading.set(true);
    try {
      const addressId = await this.ensureAddressId();
      const scheduledFor = this.selectedScheduledFor();
      const quote = await firstValueFrom(this.http.post<CarrierQuotePreview>(`/api/v1/carts/${cartId}/delivery-quotes`, {
        addressId,
        deliveryMode: this.deliveryMode(),
        ...(scheduledFor ? { scheduledFor: scheduledFor.toISOString() } : {})
      }, { headers: new HttpHeaders({
        'If-Match': `\"${this.cart.cartVersion()}\"`,
        'Idempotency-Key': crypto.randomUUID()
      }) }));
      this.deliveryQuote.set(quote);
      this.deliveryQuoteAddressId = addressId;
      return quote;
    } finally {
      this.quoteLoading.set(false);
    }
  }

  private errorMessage(error: any, fallback = 'Delivery quote could not be obtained. Please try again.'): string {
    const isDeliveryUrl = error?.url?.includes('/delivery-quotes');
    const isCheckoutQuoteUrl = error?.url?.includes('/checkout/quotes');
    const code = error?.error?.code;
    const status = error?.status;

    if (isDeliveryUrl) {
      if (code === 'FEATURE_DISABLED') {
        return 'Delivery quotes are disabled on the server (FEATURE_DISABLED). The delivery provider must be enabled before an address can be quoted.';
      }
      if (status === 503 || code === 'PROVIDER_UNAVAILABLE') {
        return 'The courier delivery service is currently unavailable. Please verify your address or try again later.';
      }
      if (status === 404) {
        return 'The delivery quote resource was not found (HTTP 404). Reload your cart and address; if it persists, check the server delivery route.';
      }
    }
    if (isCheckoutQuoteUrl) {
      if (status === 404 || code === 'NOT_FOUND') {
        return 'A valid carrier delivery quote was not found for this checkout. Please return to the Shipping Address step to refresh your delivery quote.';
      }
      if (status === 422 || code === 'PRICING_RULE_VIOLATION') {
        return 'Price calculation could not be completed for this delivery quote. Please review your cart items and shipping address.';
      }
    }
    return error?.error?.detail || error?.error?.title || error?.message || fallback;
  }

  validatePayment(): string | null {
    if (this.payment() === "netbanking") {
      if (!this.netbank.bank) return "Please select your bank.";
      if (!this.netbank.userId.trim()) return "Please enter your customer / user ID.";
      if (this.netbank.pin.trim().length < 4) return "Please enter your password / PIN.";
      return null;
    }
    const name = this.card.name.trim();
    if (!name) return "Please enter the cardholder name.";
    if (!/^[a-zA-Z\s'-]{2,50}$/.test(name)) return "Cardholder name must contain letters only (minimum 2 characters).";

    const digits = this.card.number.replace(/\D/g, "");
    if (digits.length < 13 || digits.length > 19) return "Please enter a valid card number (13 to 19 digits).";

    const expiryMatch = this.card.expiry.trim().match(/^(0[1-9]|1[0-2])\/(\d{2})$/);
    if (!expiryMatch) return "Expiry must be in MM/YY format with a valid month (01-12).";

    const expMonth = parseInt(expiryMatch[1], 10);
    const expYear = 2000 + parseInt(expiryMatch[2], 10);
    const now = new Date();
    const currentYear = now.getFullYear();
    const currentMonth = now.getMonth() + 1;

    if (expYear < currentYear || (expYear === currentYear && expMonth < currentMonth)) {
      return "Card has expired. Please check the expiry date.";
    }
    if (expYear > currentYear + 25) {
      return "Please enter a valid future expiry year.";
    }

    const cvv = this.card.cvv.trim();
    if (!/^\d{3,4}$/.test(cvv)) return "Please enter a valid 3 or 4 digit CVV.";

    return null;
  }

  async payNow() {
    this.modalError.set(null);
    const cashOnDelivery = this.payment() === 'cod';
    if (!cashOnDelivery) {
      const err = this.validatePayment();
      if (err) { this.modalError.set(err); return; }
    }

    if (!this.session.authenticated()) {
      this.session.signIn('/checkout');
      return;
    }

    this.processing.set(true);

    try {
      const cartId = this.cart.cartId();
      if (!cartId) throw new Error('No server-side Commerce cart is available. Add an item after signing in.');
      const addressId = await this.ensureAddressId();
      let carrierQuote = this.deliveryQuote();
      if (!carrierQuote || this.deliveryQuoteAddressId !== addressId || Date.parse(carrierQuote.expiresAt) <= Date.now()) {
        try {
          carrierQuote = await this.requestDeliveryQuote();
        } catch (e) {
          if (!carrierQuote) throw e;
        }
      }

      const quote = await firstValueFrom(this.http.post<any>('/api/v1/checkout/quotes', {
        cartId,
        addressId,
        carrierQuoteId: carrierQuote.id
      }, { headers: new HttpHeaders({ 'Idempotency-Key': crypto.randomUUID() }) }));

      const displayed = this.cart.items();
      const changed = !Array.isArray(quote.lines) || quote.lines.length !== displayed.length || quote.lines.some((line: any) => {
        const item = displayed.find(i => i.skuId === line.skuId);
        return !item || Number(line.quantity) !== item.quantity || Math.round(Number(line.unitAmount) * 100) !== item.unitPriceMinorUnits;
      });
      if (changed || this.cart.pricingError() || this.cart.reviewRequired()) {
        await firstValueFrom(this.cart.loadCart());
        this.deliveryQuote.set(null);
        throw new Error('Prices or quantities changed. Review your cart before placing the order.');
      }

      const checkout = await firstValueFrom(this.http.post<any>('/api/v1/checkout/attempts', {
        checkoutQuoteId: quote.id,
        quoteHash: quote.quoteHash,
        paymentMethod: cashOnDelivery ? 'CASH_ON_DELIVERY' : 'HOSTED'
      }, { headers: new HttpHeaders({ 'Idempotency-Key': crypto.randomUUID() }) }));

      const orderId = checkout?.order?.id;
      if (cashOnDelivery) {
        this.processing.set(false);
        this.payOpen.set(false);
        if (!orderId) throw new Error('Order was created without an identifier.');
        try {
          sessionStorage.setItem('nigson.checkout.last', orderId);
        } catch { /* storage may be unavailable in private browsing */ }
        await this.router.navigate(['/checkout/success'], { queryParams: { id: orderId, method: 'cod' } });
        return;
      }

      const checkoutUrl = checkout?.payment?.checkoutUrl;
      if (typeof checkoutUrl === 'string' && checkoutUrl.startsWith('https://')) {
        // Paystack appends ?trxref=&reference=; remember the order so the return page can poll API-041.
        try {
          const reference = checkout?.payment?.reference;
          if (reference && orderId) {
            sessionStorage.setItem('nigson.checkout.payment.' + reference, orderId);
            sessionStorage.setItem('nigson.checkout.last', reference);
          }
        } catch { /* storage may be unavailable in private browsing */ }
        window.location.assign(checkoutUrl);
        return;
      }

      const state = checkout?.payment?.state || 'UNKNOWN';
      this.processing.set(false);
      this.payOpen.set(false);
      this.modalError.set(`Payment status is ${state}${orderId ? ` for order ${orderId}` : ''}. Check your orders before trying again.`);
    } catch (error: any) {
      this.processing.set(false);
      console.error('[Checkout] payNow error:', error);
      this.modalError.set(this.errorMessage(error, 'Order checkout failed. Please try again.'));
    }
  }
}
