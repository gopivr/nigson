import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { EMPTY, switchMap, finalize } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, AdminHeadingComponent, BadgeComponent, statusTone, AdminOrder, OrderReturn, OrderReturns } from 'shared-ui';
import { LucideEye } from '@lucide/angular';

@Component({
  selector: 'app-admin-orders',
  standalone: true,
  imports: [CommonModule, FormsModule, AdminHeadingComponent, BadgeComponent, LucideEye],
  template: `
    <lib-admin-heading title="Orders" subtitle="Every customer order, payment and delivery state"></lib-admin-heading>

    <div class="mb-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
      <button *ngFor="let card of summaryCards()" (click)="activeTab.set(card.tab)" class="rounded-2xl border bg-white p-5 text-left shadow-sm transition hover:border-brand/40" [class.border-brand]="activeTab() === card.tab" [class.ring-2]="activeTab() === card.tab" [class.ring-brand/10]="activeTab() === card.tab">
        <span class="text-[10px] font-bold uppercase tracking-widest text-muted-ink">{{ card.label }}</span>
        <span class="mt-2 block text-2xl font-bold">{{ card.value }}</span>
      </button>
    </div>

    <p *ngIf="admin.errors()['Orders']" role="alert" class="mb-3 text-sm text-rose-700">{{ admin.errors()['Orders'] }}</p>
    <div class="mb-4 flex flex-wrap gap-2 border-b border-hairline">
      <button *ngFor="let tab of tabs" (click)="activeTab.set(tab)" class="border-b-2 px-3 py-2 text-sm font-semibold" [class.border-brand]="activeTab() === tab" [class.text-brand]="activeTab() === tab" [class.border-transparent]="activeTab() !== tab">{{ tab }}</button>
    </div>
    <div class="mb-5 flex flex-wrap gap-3">
      <button (click)="refreshOrders()" [disabled]="reviewingRefund()" class="rounded-xl border border-hairline bg-white px-4 py-2 text-sm">Refresh orders</button>
      <input type="text" [(ngModel)]="searchQuery" placeholder="Search order, customer, product or SKU..." class="min-w-72 flex-1 rounded-xl border border-hairline bg-white px-4 py-2 text-sm focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20">
    </div>

    <div class="rounded-2xl border border-hairline bg-white shadow-sm overflow-x-auto">
      <table class="w-full min-w-[980px] text-left text-sm text-ink">
        <thead class="border-b border-hairline bg-surface-alt/50 text-[10px] font-bold uppercase tracking-widest text-muted-ink">
          <tr>
            <th class="px-5 py-4">Order</th>
            <th class="px-6 py-4">Customer</th>
            <th class="px-6 py-4">Products</th>
            <th class="px-5 py-4">Total</th>
            <th class="px-6 py-4">Payment</th>
            <th class="px-5 py-4">Progress</th>
            <th class="px-5 py-4">Attention</th>
            <th class="px-6 py-4 text-right">Actions</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-hairline">
          <tr *ngFor="let o of filteredOrders()" class="transition-colors hover:bg-surface-alt/30">
            <td class="px-6 py-4 whitespace-nowrap font-semibold">{{ o.number }}
              <span *ngIf="pickupCode(o.number) as code" class="mt-1 block text-xs font-normal text-muted-ink">Pickup code: <span class="font-mono font-semibold">{{ code }}</span></span>
            </td>
            <td class="px-6 py-4">
              {{ o.customer.name }}<br>
              <span class="text-xs text-muted-ink">{{ o.customer.email }}</span>
            </td>
            <td class="px-6 py-4"><p class="font-medium">{{ o.items[0]?.name || 'No product' }}</p><p class="text-xs text-muted-ink">{{ o.items[0]?.qty || 0 }} × {{ o.items[0]?.sku }}<span *ngIf="o.items.length > 1"> · +{{ o.items.length - 1 }} more</span></p></td>
            <td class="px-5 py-4 font-semibold">{{ o.total | currency:'NGN':'symbol-narrow':'1.0-0' }}</td>
            <td class="px-6 py-4"><lib-badge [tone]="getTone(o.paymentStatus)">{{ o.paymentStatus }}</lib-badge></td>
            <td class="px-5 py-4"><lib-badge [tone]="getTone(progress(o))">{{ progress(o) }}</lib-badge></td>
            <td class="px-5 py-4"><span *ngIf="attention(o) as issue" class="inline-flex rounded-full bg-amber-100 px-2.5 py-1 text-xs font-semibold text-amber-800">{{ issue }}</span><span *ngIf="!attention(o)" class="text-xs text-muted-ink">—</span></td>
            <td class="px-6 py-4 text-right">
              <div class="flex items-center justify-end">
                <button (click)="openOrder(o)" class="grid h-8 w-8 place-items-center rounded-lg text-muted-ink transition-colors hover:bg-brand/10 hover:text-brand" title="View order">
                  <svg lucideEye class="h-4 w-4"></svg>
                </button>
              </div>
            </td>
          </tr>
          <tr *ngIf="filteredOrders().length === 0">
            <td colspan="8" class="p-8 text-center text-muted-ink">No orders match this view.</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Order management drawer -->
    <div *ngIf="viewOrder" class="fixed inset-0 z-50 flex justify-end bg-ink/40">
      <div class="h-full w-full max-w-3xl overflow-y-auto bg-white p-6 shadow-2xl sm:p-8">
        <div class="mb-4 flex items-center justify-between">
          <div><p class="text-xs font-bold uppercase tracking-widest text-brand">Order details</p><h2 class="text-xl font-bold">{{ shortOrder(viewOrder.number) }}</h2><p class="text-xs text-muted-ink">{{ viewOrder.number }}</p></div>
          <button (click)="closeOrder()" [disabled]="reviewingRefund()" class="text-muted-ink hover:text-ink">✕</button>
        </div>
        
        <div *ngIf="pickupCode(viewOrder.number) as code" class="mb-4 rounded-lg border border-hairline p-3">
          <p class="text-xs font-bold uppercase text-muted-ink">PandaGo pickup code</p>
          <p class="font-mono text-lg font-semibold">{{ code }}</p>
          <p class="text-xs text-muted-ink">Match this code with the rider when handing over this order.</p>
        </div>
        <div class="mb-4 grid grid-cols-2 gap-4">
          <div>
            <p class="text-xs text-muted-ink uppercase font-bold">Customer</p>
            <p>{{ viewOrder.customer.name }}</p>
            <p class="text-sm text-muted-ink">{{ viewOrder.customer.email }}</p>
          </div>
          <div>
            <p class="text-xs text-muted-ink uppercase font-bold">Total</p>
            <p class="font-bold text-brand">{{ viewOrder.total | currency:'NGN':'symbol-narrow':'1.0-0' }}</p>
          </div>
        </div>
        
        <div class="mb-6">
          <h3 class="mb-2 font-bold">Items</h3>
          <ul class="divide-y divide-hairline rounded-lg border border-hairline">
            <li *ngFor="let it of viewOrder.items" class="flex justify-between p-3 text-sm">
              <span>{{ it.qty }}x {{ it.name }}</span>
              <span class="font-medium">{{ (it.price * it.qty) | currency:'NGN':'symbol-narrow':'1.0-0' }}</span>
            </li>
          </ul>
        </div>

        <div *ngIf="viewOrder.cancellation" class="mb-4 rounded-xl border border-hairline bg-surface-alt p-4 text-sm">
          <p>Cancellation: {{ viewOrder.cancellation.state }}</p>
          <p *ngIf="viewOrder.cancellation.refundState">Refund: {{ viewOrder.cancellation.refundState }}</p>
          <button *ngIf="viewOrder.cancellation.state === 'CANCELLED' && viewOrder.cancellation.refundId && viewOrder.cancellation.refundState === 'REQUESTED' && admin.session.hasRole('FINANCE')"
            (click)="reviewRefund()" [disabled]="reviewingRefund()" class="mt-3 rounded-xl bg-brand px-4 py-2 font-semibold text-white disabled:opacity-50">
            {{ reviewingRefund() ? 'Checking refund...' : 'Review and approve refund' }}
          </button>
          <p *ngIf="refundError()" role="alert" class="mt-2 text-rose-700">{{ refundError() }}</p>
        </div>

        <div class="mb-4 rounded-xl border border-hairline bg-surface-alt/40 p-4 text-sm">
          <div class="mb-2 flex items-center justify-between">
            <h3 class="font-bold">Returns</h3>
            <span *ngIf="returnsLoading()" class="text-xs text-muted-ink">Loading...</span>
          </div>
          <p *ngIf="!returns() && !returnsLoading()" class="text-xs text-muted-ink">No return records were loaded.</p>
          <p *ngIf="returns() && !returns()!.delivered" class="text-xs text-muted-ink">This order has not been delivered, so returns are not requestable.</p>
          <p *ngIf="returns() && returns()!.delivered && !returns()!.requests.length" class="text-xs text-muted-ink">No return requests have been recorded.</p>
          <div *ngIf="returns()?.requests?.length" class="space-y-3">
            <div *ngFor="let item of returns()!.requests" class="rounded-lg border border-hairline bg-white p-3">
              <div class="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p class="font-semibold">{{ item.productName }} &times; {{ item.quantity }}</p>
                  <p class="text-xs text-muted-ink">Policy {{ item.policyRevision }} &bull; {{ item.createdAt | date:'medium' }}</p>
                </div>
                <lib-badge tone="neutral">{{ item.state }}</lib-badge>
              </div>
              <p class="mt-2 text-xs text-muted-ink">Customer reason: {{ item.reason }}</p>
              <p *ngIf="item.decisionReason" class="mt-1 text-xs text-muted-ink">Decision: {{ item.decisionReason }}</p>
              <p *ngIf="item.instructions" class="mt-1 text-xs text-muted-ink">Instructions: {{ item.instructions }}</p>
              <p *ngIf="item.receiptReference" class="mt-1 text-xs text-muted-ink">Receipt: <span class="font-mono">{{ item.receiptReference }}</span> &bull; {{ item.restock ? 'Restocked' : 'Not restocked' }}</p>
              <p *ngIf="item.refundId" class="mt-1 text-xs text-muted-ink">Refund: {{ item.refundState }} ({{ item.refundId }})</p>
              <div class="mt-2 flex flex-wrap gap-2">
                <ng-container *ngIf="item.state === 'REQUESTED' && admin.session.hasRole('OPERATIONS')">
                  <button (click)="beginReturn(item, 'APPROVE')" [disabled]="returnBusy()" class="rounded-lg bg-brand px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50">Approve</button>
                  <button (click)="beginReturn(item, 'REJECT')" [disabled]="returnBusy()" class="rounded-lg border border-rose-200 px-3 py-1.5 text-xs font-semibold text-rose-700 disabled:opacity-50">Reject</button>
                </ng-container>
                <button *ngIf="item.state === 'APPROVED' && admin.session.hasRole('OPERATIONS')" (click)="beginReturn(item, 'RECEIVE')" [disabled]="returnBusy()" class="rounded-lg bg-brand px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50">Record receipt</button>
                <button *ngIf="item.state === 'RECEIVED' && !item.refundId && admin.session.hasRole('FINANCE')" (click)="beginReturn(item, 'REFUND')" [disabled]="returnBusy()" class="rounded-lg bg-brand px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50">Request refund</button>
              </div>
              <p *ngIf="item.state === 'RECEIVED' && !item.refundId && !returns()!.paystackRefundAvailable" class="mt-2 text-xs text-amber-700">
                No verified Paystack settlement is available. Resolve payment and refund outside this automated flow.
              </p>
            </div>
          </div>

          <div *ngIf="returnAction()" class="mt-3 rounded-lg border border-hairline bg-white p-3">
            <p class="mb-2 text-xs font-bold uppercase tracking-widest text-muted-ink">{{ returnActionLabel() }}</p>
            <label class="mb-2 block text-xs font-semibold">Reason
              <textarea rows="2" maxlength="500" [(ngModel)]="returnReason" class="mt-1 w-full rounded-lg border border-hairline px-3 py-2 text-sm font-normal"></textarea>
            </label>
            <label *ngIf="returnAction() === 'APPROVE'" class="mb-2 block text-xs font-semibold">Customer instructions
              <textarea rows="2" maxlength="1000" [(ngModel)]="returnInstructions" class="mt-1 w-full rounded-lg border border-hairline px-3 py-2 text-sm font-normal"></textarea>
            </label>
            <ng-container *ngIf="returnAction() === 'RECEIVE'">
              <label class="mb-2 block text-xs font-semibold">External receipt reference
                <input type="text" maxlength="120" [(ngModel)]="receiptReference" class="mt-1 w-full rounded-lg border border-hairline px-3 py-2 text-sm font-normal">
              </label>
              <label class="mb-2 flex items-center gap-2 text-xs font-semibold">
                <input type="checkbox" [(ngModel)]="restock"> Restock this quantity into saleable stock
              </label>
            </ng-container>
            <label *ngIf="returnAction() === 'REFUND'" class="mb-2 block text-xs font-semibold">Refund amount (NGN)
              <input type="number" min="0.01" step="0.01" [(ngModel)]="refundAmount" class="mt-1 w-full rounded-lg border border-hairline px-3 py-2 text-sm font-normal">
            </label>
            <p *ngIf="returnError()" role="alert" class="mb-2 text-xs text-rose-700">{{ returnError() }}</p>
            <div class="flex gap-2">
              <button (click)="submitReturnAction()" [disabled]="returnBusy()" class="rounded-lg bg-brand px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50">{{ returnBusy() ? 'Saving...' : 'Confirm' }}</button>
              <button (click)="cancelReturnAction()" [disabled]="returnBusy()" class="rounded-lg border border-hairline px-3 py-1.5 text-xs font-semibold disabled:opacity-50">Cancel</button>
            </div>
          </div>
          <p *ngIf="admin.errors()['Returns']" role="alert" class="mt-2 text-xs text-rose-700">{{ admin.errors()['Returns'] }}</p>
        </div>

        <div class="rounded-xl border border-hairline bg-surface-alt/40 p-4">
          <h3 class="font-bold">Fulfilment</h3>
          <p class="mt-1 text-sm text-muted-ink">Create a local test shipment from the order's allocated stock, then dispatch and confirm delivery.</p>
          <p *ngIf="fulfillmentError()" role="alert" class="mt-2 text-sm text-rose-700">{{ fulfillmentError() }}</p>
          <p *ngIf="shipment()" class="mt-2 text-sm font-medium">Shipment: {{ shipment()!.status }}</p>
          <div *ngIf="cancellationBlocksFulfillment()" role="status" class="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
            <p class="font-semibold">Cancellation pending — fulfilment actions are unavailable.</p>
            <p class="mt-1 text-xs">{{ cancellationFulfillmentMessage() }}</p>
          </div>
          <div *ngIf="admin.session.hasRole('OPERATIONS') && !cancellationBlocksFulfillment()" class="mt-3 flex flex-wrap gap-2">
            <button *ngIf="viewOrder.rawState === 'PROCESSING' && !shipment()" (click)="createShipment()" [disabled]="fulfillmentBusy()" class="rounded-xl bg-brand px-4 py-2 text-sm font-bold text-white disabled:opacity-50">{{ fulfillmentBusy() ? 'Creating...' : 'Create shipment' }}</button>
            <button *ngIf="shipment()?.status === 'READY' || shipment()?.status === 'CREATED'" (click)="dispatchShipment()" [disabled]="fulfillmentBusy()" class="rounded-xl bg-brand px-4 py-2 text-sm font-bold text-white disabled:opacity-50">{{ fulfillmentBusy() ? 'Dispatching...' : 'Dispatch shipment' }}</button>
            <button *ngIf="shipment()?.status === 'SHIPPED'" (click)="deliverShipment()" [disabled]="fulfillmentBusy()" class="rounded-xl bg-brand px-4 py-2 text-sm font-bold text-white disabled:opacity-50">{{ fulfillmentBusy() ? 'Recording...' : 'Mark delivered' }}</button>
          </div>
        </div>
        <div class="mt-4 flex items-center gap-2">
          <p class="text-sm">Shipping and delivery update from recorded fulfillment operations.</p>
          <button *ngIf="viewOrder.rawState === 'PAID' && (!viewOrder.cancellation || viewOrder.cancellation.state === 'REJECTED') && admin.session.hasRole('OPERATIONS')" (click)="admin.setOrderStatus(viewOrder.id, 'Processing')" class="rounded-xl bg-brand px-4 py-2 font-bold text-white">Release for processing</button>
        </div>
      </div>
    </div>
  `
})
export class AdminOrdersComponent implements OnInit {
  ngOnInit() {
    this.admin.loadStaffOrders().subscribe();
  }
  admin = inject(AdminService);
  refreshOrders() { this.admin.loadStaffOrders().subscribe({ error: () => {} }); }
  
  searchQuery = signal('');
  readonly tabs = ['All', 'New', 'Processing', 'Shipped', 'Delivered', 'Needs attention'];
  readonly activeTab = signal('All');
  readonly progressSteps = ['Placed', 'Paid', 'Processing', 'Shipped', 'Delivered'];
  
  viewOrder: AdminOrder | null = null;
  readonly shipment = signal<{ id: string; status: string; version: number } | null>(null);
  readonly fulfillmentBusy = signal(false);
  readonly fulfillmentError = signal<string | null>(null);
  readonly reviewingRefund = signal(false);
  readonly refundError = signal<string | null>(null);
  readonly returns = signal<OrderReturns | null>(null);
  readonly returnsLoading = signal(false);
  readonly returnAction = signal<'APPROVE' | 'REJECT' | 'RECEIVE' | 'REFUND' | null>(null);
  readonly returnBusy = signal(false);
  readonly returnError = signal<string | null>(null);
  selectedReturnId = '';
  returnReason = '';
  returnInstructions = '';
  receiptReference = '';
  restock = false;
  refundAmount = '';

  openOrder(order: AdminOrder) {
    if (this.reviewingRefund()) return;
    this.refundError.set(null);
    this.viewOrder = order;
    this.shipment.set(null);
    this.fulfillmentError.set(null);
    if (this.admin.session.hasRole('OPERATIONS')) this.loadFulfillment(order.id);
    this.loadReturns(order.id);
  }

  closeOrder() {
    if (this.reviewingRefund() || this.returnBusy()) return;
    this.viewOrder = null;
    this.shipment.set(null);
    this.fulfillmentError.set(null);
    this.resetReturnAction();
    this.returns.set(null);
  }

  private loadFulfillment(orderId: string) {
    this.fulfillmentBusy.set(true);
    this.admin.getOrderFulfillment(orderId).pipe(finalize(() => this.fulfillmentBusy.set(false))).subscribe({
      next: view => this.shipment.set(view.shipment),
      error: () => this.fulfillmentError.set(this.admin.errors()['Fulfillment'] || 'Fulfilment status could not be loaded.')
    });
  }

  createShipment() {
    if (!this.viewOrder || this.fulfillmentBusy() || this.cancellationBlocksFulfillment()) return;
    this.fulfillmentBusy.set(true); this.fulfillmentError.set(null);
    this.admin.getOrderFulfillment(this.viewOrder.id).pipe(
      switchMap(view => this.admin.createManualShipment(this.viewOrder!.id, view.lines)),
      finalize(() => this.fulfillmentBusy.set(false))
    ).subscribe({ next: shipment => this.shipment.set(shipment), error: () => { this.loadFulfillment(this.viewOrder!.id); this.fulfillmentError.set(this.admin.errors()['Fulfillment'] || 'Shipment could not be created.'); } });
  }

  dispatchShipment() {
    const shipment = this.shipment(); if (!shipment || this.fulfillmentBusy() || this.cancellationBlocksFulfillment()) return;
    this.fulfillmentBusy.set(true); this.fulfillmentError.set(null);
    this.admin.dispatchShipment(shipment).pipe(finalize(() => this.fulfillmentBusy.set(false))).subscribe({ next: result => { this.shipment.set(result); this.refreshOrders(); }, error: () => this.fulfillmentError.set(this.admin.errors()['Fulfillment'] || 'Shipment could not be dispatched.') });
  }

  deliverShipment() {
    const shipment = this.shipment(); if (!shipment || this.fulfillmentBusy() || this.cancellationBlocksFulfillment()) return;
    this.fulfillmentBusy.set(true); this.fulfillmentError.set(null);
    this.admin.deliverShipment(shipment).pipe(finalize(() => this.fulfillmentBusy.set(false))).subscribe({ next: result => { this.shipment.set(result); this.refreshOrders(); }, error: () => this.fulfillmentError.set(this.admin.errors()['Fulfillment'] || 'Delivery could not be recorded.') });
  }

  private loadReturns(orderId: string) {
    this.returnsLoading.set(true);
    this.returns.set(null);
    this.resetReturnAction();
    this.admin.getOrderReturns(orderId).subscribe({
      next: view => { this.returns.set(view); this.returnsLoading.set(false); },
      error: () => this.returnsLoading.set(false)
    });
  }

  selectedReturn(): OrderReturn | null {
    return this.returns()?.requests.find(item => item.id === this.selectedReturnId) ?? null;
  }

  beginReturn(item: OrderReturn, action: 'APPROVE' | 'REJECT' | 'RECEIVE' | 'REFUND') {
    this.selectedReturnId = item.id;
    this.returnAction.set(action);
    this.returnError.set(null);
    this.returnReason = '';
    this.returnInstructions = '';
    this.receiptReference = '';
    this.restock = false;
    this.refundAmount = action === 'REFUND' ? (this.returns()?.refundableAmount ?? 0).toFixed(2) : '';
  }

  cancelReturnAction() {
    if (!this.returnBusy()) this.resetReturnAction();
  }

  private resetReturnAction() {
    this.returnAction.set(null);
    this.selectedReturnId = '';
    this.returnError.set(null);
    this.returnReason = '';
    this.returnInstructions = '';
    this.receiptReference = '';
    this.restock = false;
    this.refundAmount = '';
  }

  returnActionLabel(): string {
    return ({ APPROVE: 'Approve return', REJECT: 'Reject return', RECEIVE: 'Record physical receipt', REFUND: 'Request Finance refund' } as Record<string, string>)[this.returnAction() ?? ''] ?? '';
  }

  submitReturnAction() {
    const order = this.viewOrder;
    const item = this.selectedReturn();
    const action = this.returnAction();
    if (!order || !item || !action || this.returnBusy()) return;
    const reason = this.returnReason.trim();
    if (reason.length < 10) { this.returnError.set('Enter a reason of at least 10 characters.'); return; }
    if (action === 'APPROVE' && this.returnInstructions.trim().length < 10) {
      this.returnError.set('Approval requires customer return instructions of at least 10 characters.');
      return;
    }
    if (action === 'RECEIVE' && !/^[A-Za-z0-9][A-Za-z0-9:_-]{0,119}$/.test(this.receiptReference.trim())) {
      this.returnError.set('Enter a valid external receipt reference (letters, digits, colon, underscore or hyphen).');
      return;
    }
    const amount = Number(this.refundAmount);
    if (action === 'REFUND' && (!Number.isFinite(amount) || amount <= 0 || amount > (this.returns()?.refundableAmount ?? 0))) {
      this.returnError.set('Enter a refund amount within the remaining refundable amount.');
      return;
    }
    if (action === 'REFUND') {
      const formatted = new Intl.NumberFormat('en-NG', { style: 'currency', currency: 'NGN' }).format(amount);
      if (!confirm(`Request a ${formatted} refund on the original Paystack payment? Finance approval and payment processing remain separate.`)) return;
    }
    this.returnError.set(null);
    this.returnBusy.set(true);
    const request = action === 'APPROVE' || action === 'REJECT'
      ? this.admin.reviewReturn(order.id, item, action === 'APPROVE', reason, this.returnInstructions.trim())
      : action === 'RECEIVE'
        ? this.admin.receiveReturn(order.id, item, this.receiptReference.trim(), this.restock, reason)
        : this.admin.requestReturnRefund(order.id, item, amount.toFixed(2), reason);
    request.subscribe({
      next: view => { this.returns.set(view); this.returnBusy.set(false); this.resetReturnAction(); },
      error: () => this.returnBusy.set(false)
    });
  }
  reviewRefund() {
    const order = this.viewOrder;
    const id = order?.cancellation?.refundId;
    if (!order || !id || this.reviewingRefund() || !this.admin.session.hasRole('FINANCE')) return;
    this.reviewingRefund.set(true);
    this.refundError.set(null);
    this.admin.getRefund(id).pipe(
      switchMap(refund => {
        if (refund.state !== 'REQUESTED') return this.admin.loadStaffOrders();
        const amount = new Intl.NumberFormat('en-NG', { style: 'currency', currency: refund.currency }).format(Number(refund.amount));
        if (!confirm(`Approve a refund of ${amount} to the original payment for cancelled order ${order.number}? This will submit the approved refund for processing.`)) return EMPTY;
        return this.admin.approveCancellationRefund(refund).pipe(switchMap(() => this.admin.loadStaffOrders()));
      }),
      finalize(() => this.reviewingRefund.set(false))
    ).subscribe({
      next: () => { this.viewOrder = this.admin.orders().find(o => o.id === order.id) ?? order; },
      error: (error: any) => { this.refundError.set(error?.error?.code === 'ACCESS_DENIED'
        ? 'Finance approval requires current permissions, recent MFA and a separate approver when configured.'
        : 'We could not confirm refund approval. Refresh the order or retry to check the same request.'); }
    });
  }

  newCount = computed(() => this.admin.orders().filter(o => this.progress(o) === 'New').length);
  processingCount = computed(() => this.admin.orders().filter(o => this.progress(o) === 'Processing').length);
  deliveredCount = computed(() => this.admin.orders().filter(o => this.progress(o) === 'Delivered').length);
  attentionCount = computed(() => this.admin.orders().filter(o => !!this.attention(o)).length);
  revenue = computed(() => this.admin.orders().filter(o => o.paymentStatus === 'Paid' && o.status !== 'Cancelled').reduce((n, o) => n + o.total, 0));
  summaryCards = computed(() => [
    { label: 'New', value: this.newCount(), tab: 'New' },
    { label: 'Processing', value: this.processingCount(), tab: 'Processing' },
    { label: 'Needs attention', value: this.attentionCount(), tab: 'Needs attention' },
    { label: 'Delivered', value: this.deliveredCount(), tab: 'Delivered' },
    { label: 'Net paid revenue', value: new Intl.NumberFormat('en-NG', { style: 'currency', currency: 'NGN', maximumFractionDigits: 0 }).format(this.revenue()), tab: 'All' }
  ]);

  progress(order: AdminOrder): string {
    if (order.status === 'Cancelled') return 'Cancelled';
    if (order.status === 'Delivered' || order.delivery === 'DELIVERED') return 'Delivered';
    if (order.status === 'Shipped' || order.delivery === 'SHIPPED' || order.delivery === 'PARTIALLY_SHIPPED') return 'Shipped';
    if (order.status === 'Processing' || order.delivery === 'PROCESSING') return 'Processing';
    return 'New';
  }

  attention(order: AdminOrder): string | null {
    const cancellation = order.cancellation;
    if (cancellation && cancellation.state !== 'REJECTED') {
      if (cancellation.refundState && !['SUCCEEDED', 'REJECTED'].includes(cancellation.refundState)) return 'Refund pending';
      return cancellation.state === 'CANCELLED' ? 'Cancelled' : 'Cancellation requested';
    }
    if (order.afterSales) {
      if (order.afterSales.refundState && !['SUCCEEDED', 'REJECTED'].includes(order.afterSales.refundState)) return 'Refund pending';
      return order.afterSales.returnState === 'REQUESTED' ? 'Return requested' : order.afterSales.returnState === 'APPROVED' ? 'Return approved' : 'Return received';
    }
    return null;
  }

  cancellationBlocksFulfillment(order: AdminOrder | null = this.viewOrder): boolean {
    const state = order?.cancellation?.state;
    return !!state && state !== 'REJECTED';
  }

  cancellationFulfillmentMessage(): string {
    const state = this.viewOrder?.cancellation?.state;
    if (state === 'WAITING_PAYMENT') return 'Waiting for the payment outcome before cancellation can finish.';
    if (state === 'WAITING_CARRIER') return 'Waiting for delivery cancellation confirmation. Do not dispatch this shipment.';
    if (state === 'CANCELLED') return 'This order is cancelled and must not be shipped.';
    return 'The cancellation request must finish or be rejected before fulfilment can continue.';
  }

  shortOrder(value: string): string { return value.length > 18 ? `${value.slice(0, 8)}…${value.slice(-8)}` : value; }

  stepReached(order: AdminOrder, step: string): boolean {
    const levels: Record<string, number> = { Placed: 0, Paid: 1, Processing: 2, Shipped: 3, Delivered: 4 };
    const state = this.progress(order);
    const current = state === 'Delivered' ? 4 : state === 'Shipped' ? 3 : state === 'Processing' ? 2 : order.paymentStatus === 'Paid' ? 1 : 0;
    return current >= levels[step];
  }

  filteredOrders = computed(() => {
    let list = this.admin.orders();
    const q = this.searchQuery().trim().toLowerCase();
    if (q) {
      list = list.filter(o => [o.number, this.pickupCode(o.number) ?? '', o.customer.name, o.customer.email, ...o.items.flatMap(item => [item.name, item.sku])].join(' ').toLowerCase().includes(q));
    }
    const tab = this.activeTab();
    if (tab === 'Needs attention') list = list.filter(o => !!this.attention(o));
    else if (tab !== 'All') list = list.filter(o => this.progress(o) === tab);
    return list;
  });

  /** Must match Commerce JdbcShipmentRepository.pickupCode: last 10 alphanumeric order-number characters. */
  pickupCode(orderNumber: string): string | null {
    const normalized = (orderNumber ?? '').replace(/[^A-Za-z0-9]/g, '').toUpperCase();
    return normalized.length >= 10 ? normalized.slice(-10) : null;
  }

  getTone(status: string) {
    return statusTone(status);
  }
}

