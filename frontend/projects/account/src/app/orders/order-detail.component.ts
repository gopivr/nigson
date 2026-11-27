import { Component, inject, computed, OnInit, signal, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer, filter, switchMap, catchError, EMPTY, take } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { OrderService, BackendOrder, OrderTracking, OrderReturns, OrderReturn } from 'shared-ui';
import { 
  LucideArrowLeft, LucideXCircle, LucideTruck
} from '@lucide/angular';

@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink,
    LucideArrowLeft, LucideXCircle, LucideTruck
  ],
  template: `
    <div class="space-y-6">
      <a routerLink="/account/orders" class="inline-flex items-center gap-1.5 text-sm font-semibold text-brand hover:underline">
        <svg lucideArrowLeft class="h-4 w-4"></svg> Back to orders
      </a>

      <div *ngIf="loading()" class="rounded-2xl border border-hairline bg-white p-16 text-center">
        <p class="text-sm text-muted-ink">Loading order details...</p>
      </div>

      <div *ngIf="!loading() && !order()" class="rounded-2xl border border-hairline bg-white p-10 text-center">
        <p class="text-muted-ink">Order not found.</p>
      </div>

      <ng-container *ngIf="!loading() && order()">
        <div class="rounded-2xl border border-hairline bg-white p-6 md:p-8">
          <div class="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p class="text-[10px] font-semibold uppercase tracking-widest text-muted-ink">Order Reference</p>
              <h2 class="text-2xl font-extrabold text-ink">{{ order()?.orderNumber }}</h2>
              <p class="text-xs text-muted-ink mt-1">Placed {{ formatDate(order()?.createdAt ?? '') }}</p>
            </div>
            <div class="text-right">
              <p class="text-[10px] font-semibold uppercase tracking-widest text-muted-ink">Status</p>
              <span class="inline-block mt-1 rounded-full px-3 py-1 text-xs font-bold" [ngClass]="statusColor(order()?.status ?? '')">
                {{ statusLabel(order()?.status ?? '') }}
              </span>
              <div class="mt-2" *ngIf="canCancel()">
                <button
                  (click)="cancelOrder()"
                  [disabled]="cancelling()"
                  class="inline-flex items-center gap-1 rounded-full border border-rose-200 px-3 py-1 text-xs font-semibold text-rose-600 hover:bg-rose-50 transition-colors disabled:opacity-50"
                >
                  <svg lucideXCircle class="h-3.5 w-3.5"></svg>
                  {{ cancelling() ? 'Requesting...' : 'Cancel Order' }}
                </button>
              </div>
            </div>
          </div>

          <p *ngIf="orderSvcError()" role="alert" class="mt-4 text-sm text-rose-700">{{ orderSvcError() }}</p>
          <div *ngIf="order()?.cancellation" role="status" aria-live="polite" class="mt-4 rounded-xl border border-hairline bg-surface-alt p-4 text-sm text-ink">
            <p>{{ cancellationMessage() }}</p>
            <p *ngIf="refundMessage()" class="mt-1 text-muted-ink">{{ refundMessage() }}</p>
          </div>

          <!-- Tracking section (TASK-45) -->
          <div id="tracking" class="mt-8 border-t border-hairline pt-6 scroll-mt-24">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <svg lucideTruck class="h-4 w-4 text-brand"></svg>
                <p class="text-xs font-semibold uppercase tracking-[0.2em] text-ink/70">Order Progress</p>
              </div>
              <span *ngIf="order()?.trackingNumber" class="text-xs text-muted-ink">
                Tracking: <strong class="text-ink font-mono">{{ order()?.trackingNumber }}</strong>
              </span>
            </div>

            <p *ngIf="tracking()?.trackingNumber" class="mt-3 text-sm text-muted-ink">PandaGo order ID: <span class="font-mono text-ink">{{ tracking()!.trackingNumber }}</span></p>
            <ol class="mt-4 grid grid-cols-2 md:grid-cols-5 gap-3">
              <li *ngFor="let s of getTimeline(); let i = index" class="relative">
                <div [ngClass]="['h-1.5 w-full rounded-full transition-colors', isStepDone(s, order()?.status ?? '') ? 'bg-brand' : 'bg-surface-alt']"></div>
                <div class="mt-3 flex items-start gap-2">
                  <div [ngClass]="['grid h-6 w-6 shrink-0 place-items-center rounded-full text-[10px] font-bold', isStepDone(s, order()?.status ?? '') ? 'bg-brand text-white' : 'bg-surface-alt text-muted-ink']">
                    {{ i + 1 }}
                  </div>
                  <p [ngClass]="['text-xs font-semibold', isStepDone(s, order()?.status ?? '') ? 'text-ink' : 'text-muted-ink']">{{ statusLabel(s) }}</p>
                </div>
              </li>
            </ol>

            <div *ngIf="tracking()?.scans?.length" class="mt-6 space-y-4 border-l-2 border-brand pl-4 ml-2">
              <div *ngFor="let scan of tracking()!.scans" class="relative">
                <div class="absolute -left-[21px] top-1.5 h-2.5 w-2.5 rounded-full bg-brand"></div>
                <p class="text-sm font-semibold text-ink">{{ scan.status }}</p>
                <p class="text-xs text-muted-ink">{{ scan.timestamp | date:'medium' }}<ng-container *ngIf="scan.location"> &bull; {{ scan.location }}</ng-container></p>
                <p *ngIf="scan.description" class="mt-1 text-xs text-muted-ink">{{ scan.description }}</p>
              </div>
            </div>
            <p *ngIf="tracking() && !tracking()!.scans.length" class="mt-5 text-xs text-muted-ink">
              No tracking events yet. Shipment updates appear here after dispatch.
            </p>
          </div>
        </div>

        <div class="grid gap-6 md:grid-cols-3">
          <!-- Order Items -->
          <div class="md:col-span-2 space-y-6">
            <div class="rounded-2xl border border-hairline bg-white p-6">
              <h3 class="text-sm font-extrabold uppercase tracking-widest border-b border-hairline pb-4 mb-4">Items ({{ order()?.items?.length ?? 0 }})</h3>
              <ul class="space-y-4">
                <li *ngFor="let item of order()?.items" class="flex gap-4">
                  <img *ngIf="item.imageUrl" [src]="item.imageUrl" alt="" class="h-20 w-20 rounded-xl object-cover border border-hairline" />
                  <div class="flex-1">
                    <p class="font-semibold text-sm text-ink">{{ item.name }}</p>
                    <p class="text-xs text-muted-ink font-mono">SKU: {{ item.sku }}</p>
                    <div class="mt-2 flex items-center justify-between text-sm">
                      <span class="font-medium text-muted-ink">{{ formatPrice(item.unitPriceMinorUnits) }} &times; {{ item.quantity }}</span>
                      <span class="font-bold text-ink">{{ formatPrice(item.unitPriceMinorUnits * item.quantity) }}</span>
                    </div>
                  </div>
                </li>
              </ul>
            </div>

            <!-- Returns (API-237/238) -->
            <div *ngIf="returns()" class="rounded-2xl border border-hairline bg-white p-6">
              <h3 class="text-sm font-extrabold uppercase tracking-widest border-b border-hairline pb-4 mb-4">Returns</h3>

              <p *ngIf="!returns()!.delivered" class="text-xs text-muted-ink">
                Returns become available after this order has been delivered.
              </p>

              <div *ngIf="returns()!.requests.length" class="space-y-3">
                <div *ngFor="let item of returns()!.requests" class="rounded-xl border border-hairline bg-surface-alt/40 p-4">
                  <div class="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <p class="text-sm font-semibold text-ink">{{ item.productName }} &times; {{ item.quantity }}</p>
                      <p class="text-xs text-muted-ink">Requested {{ formatDate(item.createdAt) }}</p>
                    </div>
                    <span class="rounded-full bg-white px-2.5 py-1 text-[11px] font-bold uppercase tracking-wide text-ink">{{ returnStateLabel(item.state) }}</span>
                  </div>
                  <p class="mt-2 text-xs text-muted-ink">{{ returnMessage(item) }}</p>
                  <p *ngIf="item.instructions" class="mt-2 rounded-lg bg-white p-3 text-xs text-ink">
                    <span class="font-semibold">Return instructions:</span> {{ item.instructions }}
                  </p>
                  <p *ngIf="item.receiptReference" class="mt-2 text-xs text-muted-ink">
                    Receipt reference: <span class="font-mono text-ink">{{ item.receiptReference }}</span>
                  </p>
                  <p *ngIf="item.refundState" class="mt-2 text-xs text-muted-ink">{{ returnRefundMessage(item) }}</p>
                </div>
              </div>

              <div *ngIf="returns()!.delivered && returnableLines().length" class="mt-5 space-y-3 border-t border-hairline pt-4">
                <p class="text-xs font-semibold uppercase tracking-widest text-muted-ink">Request another return</p>
                <label class="block text-xs font-semibold text-ink">
                  Product
                  <select [(ngModel)]="returnLineId" class="mt-1 w-full rounded-xl border border-hairline bg-white px-3 py-2 text-sm font-normal focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20">
                    <option *ngFor="let line of returnableLines()" [value]="line.orderLineId">
                      {{ line.name }} ({{ line.availableQuantity }} available)
                    </option>
                  </select>
                </label>
                <label class="block text-xs font-semibold text-ink">
                  Quantity
                  <input type="number" [min]="scaleStep()" [max]="selectedReturnLine()?.availableQuantity ?? scaleStep()" [step]="scaleStep()" [(ngModel)]="returnQuantity" class="mt-1 w-full rounded-xl border border-hairline bg-white px-3 py-2 text-sm font-normal focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20">
                </label>
                <label class="block text-xs font-semibold text-ink">
                  Reason
                  <textarea rows="3" maxlength="500" [(ngModel)]="returnReason" placeholder="Tell us what is wrong with the product" class="mt-1 w-full rounded-xl border border-hairline bg-white px-3 py-2 text-sm font-normal focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"></textarea>
                </label>
                <p *ngIf="returnError()" role="alert" class="text-xs text-rose-700">{{ returnError() }}</p>
                <button (click)="submitReturn()" [disabled]="returning()" class="rounded-xl bg-brand px-4 py-2 text-sm font-bold text-white disabled:opacity-50">
                  {{ returning() ? 'Submitting...' : 'Request return' }}
                </button>
              </div>

              <p *ngIf="returns()!.delivered && !returnableLines().length && !returns()!.requests.length" class="mt-3 text-xs text-muted-ink">
                No delivered quantity remains available to return.
              </p>
            </div>
          </div>

          <!-- Totals and Delivery -->
          <div class="space-y-6">
            <div class="rounded-2xl border border-hairline bg-white p-6">
              <h3 class="text-sm font-extrabold uppercase tracking-widest border-b border-hairline pb-4 mb-4">Payment Summary</h3>
              <dl class="space-y-2 text-sm">
                <div class="flex justify-between text-muted-ink"><dt>Subtotal</dt><dd>{{ formatPrice(order()?.subtotalMinorUnits ?? 0) }}</dd></div>
                <div class="flex justify-between text-muted-ink"><dt>Shipping</dt><dd>{{ formatPrice(order()?.shippingMinorUnits ?? 0) }}</dd></div>
                <div class="flex justify-between text-muted-ink"><dt>Tax</dt><dd>{{ formatPrice(order()?.taxMinorUnits ?? 0) }}</dd></div>
                <div class="mt-4 border-t border-hairline pt-4 flex justify-between text-base font-extrabold text-ink">
                  <dt>Total Paid</dt><dd>{{ formatPrice(order()?.totalMinorUnits ?? 0) }}</dd>
                </div>
              </dl>
            </div>
            
            <div class="rounded-2xl border border-hairline bg-white p-6">
              <h3 class="text-sm font-extrabold uppercase tracking-widest border-b border-hairline pb-4 mb-4">Delivery Address</h3>
              <p class="text-sm font-bold text-ink">{{ order()?.buyerContact?.fullName }}</p>
              <p class="mt-1 text-xs text-muted-ink leading-relaxed">
                {{ order()?.deliveryAddress?.line1 }}<br/>
                {{ order()?.deliveryAddress?.city }}, {{ order()?.deliveryAddress?.state }} {{ order()?.deliveryAddress?.postalCode }}<br/>
                {{ order()?.deliveryAddress?.countryCode }}
              </p>
              <p *ngIf="order()?.buyerContact?.phone" class="mt-2 text-xs text-muted-ink">
                Phone: {{ order()?.buyerContact?.phone }}
              </p>
            </div>
          </div>
        </div>
      </ng-container>
    </div>
  `
})
export class OrderDetailComponent implements OnInit {
  private orderSvc = inject(OrderService);
  private destroyRef = inject(DestroyRef);
  route = inject(ActivatedRoute);

  readonly order = signal<BackendOrder | null>(null);
  readonly tracking = signal<OrderTracking | null>(null);
  readonly returns = signal<OrderReturns | null>(null);
  readonly loading = signal(true);
  readonly cancelling = signal(false);
  readonly returning = signal(false);
  returnLineId = '';
  returnQuantity = 1;
  returnReason = '';

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get("id") ?? this.route.snapshot.paramMap.get("orderId") ?? "";
    if (id) {
      timer(5000, 5000).pipe(
        take(120),
        filter(() => !!this.order()?.cancellation),
        switchMap(() => this.orderSvc.getOrder(id).pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(this.destroyRef)
      ).subscribe(o => this.order.set(o));
      this.orderSvc.getOrder(id).subscribe({
        next: o => {
          this.order.set(o);
          this.loading.set(false);
          this.scrollToTracking();
          this.loadReturns(o.orderId || id);
          this.orderSvc.getTracking(id).subscribe(t => {
            if (t) this.tracking.set(t);
          });
        },
        error: () => this.loading.set(false)
      });
    }
  }

  formatPrice(minorUnits: number) { return this.orderSvc.formatPrice(minorUnits); }  statusLabel(status: string) { return this.orderSvc.statusLabel(status); }
  statusColor(status: string) { return this.orderSvc.statusColor(status); }
  formatDate(d: string) {
    if (!d) return '-';
    return new Date(d).toLocaleDateString("en-NG", { day: "numeric", month: "short", year: "numeric" });
  }

  cancelOrder() {
    const o = this.order();
    if (!o) return;
    if (!this.canCancel() || !confirm('Request cancellation? Delivery cancellation must be confirmed and any refund requires review.')) return;
    this.cancelling.set(true);
    this.orderSvc.cancelOrder(o.orderId).subscribe(updated => {
      this.cancelling.set(false);
      if (updated) this.order.set(updated);
    });
  }

  private loadReturns(orderId: string) {
    this.orderSvc.getReturns(orderId).subscribe(view => {
      this.returns.set(view);
      const lines = view?.lines.filter(line => line.availableQuantity > 0) ?? [];
      if (!lines.some(line => line.orderLineId === this.returnLineId)) {
        this.returnLineId = lines[0]?.orderLineId ?? '';
        this.returnQuantity = Math.min(this.scaleStep(), lines[0]?.availableQuantity ?? this.scaleStep());
      }
    });
  }

  returnableLines() {
    return this.returns()?.lines.filter(line => line.availableQuantity > 0) ?? [];
  }

  selectedReturnLine() {
    return this.returnableLines().find(line => line.orderLineId === this.returnLineId) ?? null;
  }

  scaleStep(): number {
    const scale = this.selectedReturnLine()?.quantityScale ?? 0;
    return 1 / Math.pow(10, scale);
  }

  submitReturn() {
    const order = this.order();
    const line = this.selectedReturnLine();
    const quantity = Number(this.returnQuantity);
    if (!order || !line || !Number.isFinite(quantity) || quantity <= 0 || quantity > line.availableQuantity) return;
    this.returning.set(true);
    this.orderSvc.requestReturn(order.orderId, line.orderLineId, quantity, this.returnReason.trim()).subscribe(item => {
      this.returning.set(false);
      if (item) {
        this.returnReason = '';
        this.returnQuantity = this.scaleStep();
        this.loadReturns(order.orderId);
      }
    });
  }

  returnError() { return this.orderSvc.returnError(); }

  returnStateLabel(state: string): string {
    return ({ REQUESTED: 'Under review', APPROVED: 'Approved', REJECTED: 'Not approved', RECEIVED: 'Received' } as Record<string, string>)[state] ?? state;
  }

  returnMessage(item: OrderReturn): string {
    if (item.state === 'REQUESTED') return 'Operations are reviewing this request against the returns policy recorded with your order.';
    if (item.state === 'APPROVED') return 'This return is approved. Follow the return instructions recorded by Operations.';
    if (item.state === 'REJECTED') return item.decisionReason ? `Not approved: ${item.decisionReason}` : 'This return was not approved.';
    return item.restock === false
      ? 'Received and inspected. This item was not returned to saleable stock.'
      : 'Received and inspected. Your refund remains a separate Finance decision.';
  }

  returnRefundMessage(item: OrderReturn): string {
    const state = item.refundState;
    if (state === 'SUCCEEDED') return 'Refund completed.';
    if (state === 'REJECTED' || state === 'FAILED') return 'Refund needs attention. Please contact support.';
    if (state === 'REQUESTED') return 'A refund is awaiting Finance approval.';
    return 'A refund is being processed. We will update this request when it is confirmed.';
  }

  getTimeline(): string[] {
    return ['PENDING', 'PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED'];
  }

  isStepDone(step: string, status: string): boolean {
    const order = this.getTimeline();
    const current = order.indexOf(status);
    return current >= 0 && current >= order.indexOf(step);
  }

  canCancel(): boolean {
    const order = this.order();
    return order?.cancellationAllowed === true && !order.cancellation;
  }

  orderSvcError() { return this.orderSvc.cancellationError(); }

  cancellationMessage(): string {
    const state = this.order()?.cancellation?.state;
    return ({ REQUESTED: 'Cancellation requested. We are checking your order.',
      WAITING_PAYMENT: 'Cancellation requested. We are checking the payment outcome.',
      WAITING_CARRIER: 'Cancellation requested. We are waiting for delivery cancellation confirmation.',
      CANCELLED: 'Your order has been cancelled.',
      REJECTED: 'We could not cancel this order automatically. Please contact support.' } as Record<string, string>)[state ?? ''] ?? '';
  }

  refundMessage(): string {
    const state = this.order()?.cancellation?.refundState;
    if (!state) return '';
    if (state === 'SUCCEEDED') return 'Your refund has been processed.';
    if (['FAILED', 'REJECTED'].includes(state)) return 'Your refund needs attention. Please contact support.';
    if (state === 'REQUESTED') return 'Your refund is awaiting Finance review.';
    return 'Your refund is being processed. We will update this order when it is confirmed.';
  }

  private scrollToTracking() {
    if (this.route.snapshot.fragment !== 'tracking') return;
    setTimeout(() => document.getElementById('tracking')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 0);
  }
}
