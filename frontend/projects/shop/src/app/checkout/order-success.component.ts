import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import {
  LucideCheckCircle2, LucideClock, LucidePackage, LucideArrowRight, LucideMail
} from '@lucide/angular';
import { CatalogService, OrderPayment, OrderService, ShopService } from 'shared-ui';

type Phase = 'CHECKING' | 'PAID' | 'PENDING' | 'FAILED' | 'UNVERIFIED' | 'COD';

interface OrderView {
  id: string;
  orderNumber: string;
  status: string;
  paymentStatus: string;
  grandTotalMinorUnits: number;
  currency: string;
  recipient: string;
  addressLine: string;
  lines: { name: string; quantity: number; totalMinorUnits: number; imageUrl?: string }[];
}

const POLL_INTERVAL_MS = 3000;
const MAX_POLLS = 20;

@Component({
  selector: 'app-order-success',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    LucideCheckCircle2, LucideClock, LucidePackage, LucideArrowRight, LucideMail
  ],
  template: `
    <div class="min-h-[70vh] bg-background">
      <section class="pt-8 pb-16 md:pt-10 md:pb-20">
        <div class="mx-auto max-w-3xl px-6 text-center">
          <div *ngIf="phase() === 'PAID' || phase() === 'COD'" class="mx-auto grid h-20 w-20 place-items-center rounded-full bg-emerald-500/10 text-emerald-600 animate-scale-in">
            <svg lucideCheckCircle2 class="h-10 w-10"></svg>
          </div>
          <div *ngIf="phase() !== 'PAID' && phase() !== 'COD'" class="mx-auto grid h-20 w-20 place-items-center rounded-full bg-amber-500/10 text-amber-600">
            <svg lucideClock class="h-10 w-10"></svg>
          </div>

          <p class="mt-6 text-[11px] font-semibold uppercase tracking-[0.25em] text-brand">{{ eyebrow() }}</p>
          <h1 class="mt-2 text-4xl md:text-5xl font-extrabold tracking-[-0.02em]">{{ heading() }}</h1>
          <p class="mt-4 text-muted-ink max-w-xl mx-auto">{{ detail() }}</p>

          <p *ngIf="reference()" class="mt-3 text-xs text-muted-ink">
            Payment reference: <span class="font-mono">{{ reference() }}</span>
          </p>

          <div *ngIf="order()" class="mt-10 rounded-2xl border border-hairline bg-white p-6 md:p-8 text-left shadow-sm">
            <div class="flex flex-wrap items-center justify-between gap-4">
              <div>
                <p class="text-[10px] uppercase tracking-widest text-muted-ink">Order number</p>
                <p class="font-bold">{{ order()!.orderNumber }}</p>
              </div>
              <div>
                <p class="text-[10px] uppercase tracking-widest text-muted-ink">Payment status</p>
                <p class="font-bold" [class.text-emerald-600]="phase() === 'PAID'"
                   [class.text-amber-600]="phase() === 'PENDING' || phase() === 'CHECKING'"
                   [class.text-rose-600]="phase() === 'FAILED'">{{ paymentLabel() }}</p>
              </div>
              <div>
                <p class="text-[10px] uppercase tracking-widest text-muted-ink">Total</p>
                <p class="font-bold text-[#D62828]">{{ formatPrice(order()!.grandTotalMinorUnits) }}</p>
              </div>
            </div>

            <ul class="mt-6 space-y-3">
              <li *ngFor="let i of order()!.lines" class="flex items-center gap-3 text-sm">
                <div class="h-12 w-12 rounded-lg overflow-hidden bg-surface-alt shrink-0">
                  <img *ngIf="i.imageUrl" [src]="i.imageUrl" alt="" class="h-full w-full object-cover" />
                </div>
                <div class="flex-1 min-w-0">
                  <p class="truncate font-semibold">{{ i.name }}</p>
                  <p class="text-xs text-muted-ink">Qty {{ i.quantity }}</p>
                </div>
                <p class="font-semibold">{{ formatPrice(i.totalMinorUnits) }}</p>
              </li>
            </ul>

            <div class="mt-6 rounded-xl bg-surface-alt p-5 flex items-start gap-3">
              <svg lucidePackage class="h-5 w-5 text-brand shrink-0 mt-0.5"></svg>
              <div class="text-sm">
                <p class="font-semibold">{{ order()!.recipient }}</p>
                <p class="text-muted-ink mt-1">Shipping to {{ order()!.addressLine }}.</p>
              </div>
            </div>
          </div>

          <div class="mt-8 flex flex-wrap justify-center gap-3">
            <button *ngIf="phase() === 'PENDING' || phase() === 'CHECKING' || phase() === 'UNVERIFIED'"
                    type="button" (click)="checkNow()"
                    class="inline-flex items-center gap-2 rounded-full bg-brand text-white px-6 py-3 text-sm font-semibold hover:bg-brand-deep">
              Check payment status
            </button>
            <a routerLink="/products" class="inline-flex items-center gap-2 rounded-full bg-brand text-white px-6 py-3 text-sm font-semibold hover:bg-brand-deep">
              Continue shopping <svg lucideArrowRight class="h-4 w-4"></svg>
            </a>
            <a *ngIf="order()" [routerLink]="['/account/orders', order()!.id]" class="inline-flex items-center gap-2 rounded-full border border-ink text-ink px-6 py-3 text-sm font-semibold hover:bg-ink hover:text-white">
              View Order
            </a>
            <a *ngIf="!order()" routerLink="/account/orders" class="inline-flex items-center gap-2 rounded-full border border-ink text-ink px-6 py-3 text-sm font-semibold hover:bg-ink hover:text-white">
              View my orders
            </a>
          </div>

          <p *ngIf="phase() === 'PAID'" class="mt-8 inline-flex items-center gap-1.5 text-xs text-muted-ink">
            <svg lucideMail class="h-3.5 w-3.5"></svg> A confirmation email is on its way.
          </p>
          <p *ngIf="phase() === 'COD'" class="mt-8 text-xs text-muted-ink">
            Pay the courier when your order arrives. The order total is shown above.
          </p>
          <p *ngIf="phase() === 'PENDING'" class="mt-8 text-xs text-muted-ink">
            Your payment is verified automatically. This page updates when the provider confirms the transaction.
          </p>
        </div>
      </section>
    </div>
  `
})
export class OrderSuccessComponent implements OnInit, OnDestroy {
  private orderSvc = inject(OrderService);
  shop = inject(ShopService);
  catalog = inject(CatalogService);
  route = inject(ActivatedRoute);

  readonly order = signal<OrderView | null>(null);
  readonly phase = signal<Phase>('CHECKING');
  readonly reference = signal('');
  private orderId = '';
  private cashOnDelivery = false;
  private polls = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private destroyed = false;

  ngOnInit() {
    const query = this.route.snapshot.queryParamMap;
    this.reference.set(query.get('reference') || query.get('trxref') || '');
    this.cashOnDelivery = query.get('method') === 'cod';
    this.orderId = query.get('id') || this.recalledOrderId(this.reference());
    this.refresh();
  }

  ngOnDestroy() {
    this.destroyed = true;
    if (this.timer) clearTimeout(this.timer);
  }

  heading(): string {
    switch (this.phase()) {
      case 'PAID': return 'Thank you for your order';
      case 'COD': return 'Your cash-on-delivery order is placed';
      case 'PENDING': return 'Confirming your payment';
      case 'FAILED': return 'Payment not completed';
      case 'UNVERIFIED': return 'We could not match this payment';
      default: return 'Checking your payment';
    }
  }

  eyebrow(): string {
    switch (this.phase()) {
      case 'PAID': return 'Payment received';
      case 'COD': return 'Order placed';
      case 'PENDING': return 'Order processing';
      case 'FAILED': return 'Payment failed';
      case 'UNVERIFIED': return 'Payment unverified';
      default: return 'Please wait';
    }
  }

  detail(): string {
    switch (this.phase()) {
      case 'PAID': return 'Your payment was successful and your order is being prepared.';
      case 'COD': return 'Your order is reserved. Pay the courier in cash when your order arrives; no online payment was taken.';
      case 'PENDING': return 'Your order is reserved and we are waiting for the payment provider to confirm the transaction.';
      case 'FAILED': return 'The provider did not complete this payment. Your stock reservation was released and no money was taken.';
      case 'UNVERIFIED': return 'We have your payment reference but cannot match it to an order in this browser. Open your orders to check the latest state.';
      default: return 'We are retrieving the latest payment state for your order.';
    }
  }

  paymentLabel(): string {
    const phase = this.phase();
    if (phase === 'PAID') return 'Paid';
    if (phase === 'COD') return 'Cash on delivery';
    if (phase === 'FAILED') return 'Not completed';
    return 'Confirming';
  }

  checkNow() {
    this.polls = 0;
    this.phase.set('CHECKING');
    this.refresh();
  }

  private refresh() {
    if (this.destroyed) return;
    if (!this.orderId) {
      this.phase.set('UNVERIFIED');
      return;
    }
    if (!this.cashOnDelivery) {
      this.orderSvc.getPayment(this.orderId).subscribe({
        next: payment => this.apply(payment),
        error: () => this.phase.set('UNVERIFIED')
      });
    }
    this.orderSvc.getOrder(this.orderId).subscribe({
      next: raw => {
        this.order.set(this.view(raw));
        if (this.cashOnDelivery) {
          this.phase.set(raw?.paymentStatus === 'PAID' ? 'PAID' : raw?.status === 'CANCELLED' ? 'FAILED' : 'COD');
        }
      },
      error: () => { if (this.cashOnDelivery) this.phase.set('UNVERIFIED'); }
    });
  }

  private apply(payment: OrderPayment) {
    if (payment.settled || payment.state === 'SUCCEEDED') {
      this.phase.set('PAID');
      this.clearRecall();
      return;
    }
    if (payment.state === 'FAILED' || payment.state === 'CANCELLED') {
      this.phase.set('FAILED');
      this.clearRecall();
      return;
    }
    this.phase.set('PENDING');
    this.schedulePoll();
  }

  private schedulePoll() {
    if (this.destroyed || this.polls >= MAX_POLLS) return;
    this.polls += 1;
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.refresh(), POLL_INTERVAL_MS);
  }

  private recalledOrderId(reference: string): string {
    try {
      if (reference) return sessionStorage.getItem('nigson.checkout.payment.' + reference) || '';
      const last = sessionStorage.getItem('nigson.checkout.last') || '';
      return last ? sessionStorage.getItem('nigson.checkout.payment.' + last) || '' : '';
    } catch {
      return '';
    }
  }

  private clearRecall() {
    try {
      if (this.reference()) sessionStorage.removeItem('nigson.checkout.payment.' + this.reference());
      sessionStorage.removeItem('nigson.checkout.last');
    } catch { /* storage may be unavailable in private browsing */ }
  }

  private view(raw: any): OrderView {
    return {
      id: raw?.orderId || this.orderId,
      orderNumber: raw?.orderNumber || '',
      status: raw?.status || '',
      paymentStatus: raw?.paymentStatus || '',
      grandTotalMinorUnits: raw?.totalMinorUnits ?? 0,
      currency: raw?.currency || 'NGN',
      recipient: raw?.buyerContact?.fullName || '',
      addressLine: [raw?.deliveryAddress?.line1, raw?.deliveryAddress?.city, raw?.deliveryAddress?.state].filter(Boolean).join(', '),
      lines: (raw?.items || []).map((line: any) => ({
        name: line.name || '',
        quantity: Number(line.quantity) || 1,
        totalMinorUnits: (line.unitPriceMinorUnits || 0) * (Number(line.quantity) || 1),
        imageUrl: line.imageUrl
      }))
    };
  }

  formatPrice(minorUnits: number) { return this.orderSvc.formatPrice(minorUnits); }
}
