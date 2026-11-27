import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CartService, OrderService, ShopService } from 'shared-ui';
import type { BackendOrder } from 'shared-ui';
import { 
  LucidePackage, LucideArrowRight, LucideDownload, 
  LucideRotateCcw, LucideTruck, LucideEye 
} from '@lucide/angular';

@Component({
  selector: 'app-orders-list',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    LucidePackage, LucideArrowRight, LucideDownload, 
    LucideRotateCcw, LucideTruck, LucideEye
  ],
  template: `
    <div *ngIf="!orders().length" class="rounded-2xl border border-hairline bg-surface-alt p-12 text-center">
      <div class="mx-auto grid h-14 w-14 place-items-center rounded-full bg-white">
        <svg lucidePackage class="h-7 w-7 text-brand"></svg>
      </div>
      <h2 class="mt-5 text-2xl font-extrabold">No orders yet</h2>
      <p class="mt-2 text-sm text-muted-ink">Your placed orders will appear here.</p>
      <a routerLink="/products" class="mt-6 inline-flex items-center gap-2 rounded-full bg-brand text-white px-5 py-2.5 text-sm font-semibold hover:bg-brand-deep">
        Shop products <svg lucideArrowRight class="h-4 w-4"></svg>
      </a>
    </div>

    <div *ngIf="orders().length" class="space-y-4">
      <div *ngFor="let o of orders()" class="rounded-2xl border border-hairline bg-white p-5">
        <div class="grid gap-4 lg:grid-cols-[1.5fr_2fr_1fr_auto] items-center">
          <div>
            <p class="text-[10px] font-semibold uppercase tracking-widest text-muted-ink">Order</p>
            <p class="font-bold">{{ o.orderNumber }}</p>
            <p class="text-xs text-muted-ink mt-1">{{ formatDate(o.createdAt) }}</p>
          </div>
          <div class="flex items-center gap-3">
            <img *ngIf="o.items[0]?.imageUrl" [src]="o.items[0].imageUrl" alt="" class="h-14 w-14 rounded-lg object-cover border border-hairline" />
            <div>
              <p class="text-sm font-semibold line-clamp-1">
                {{ o.items[0]?.name }}
                <span *ngIf="o.items?.length > 1"> +{{ o.items?.length - 1 }} more</span>
              </p>
              <p class="text-xs text-muted-ink">Qty: {{ getTotalQty(o) }} &bull; {{ formatPrice(o.totalMinorUnits) }}</p>
            </div>
          </div>
          <div class="space-y-1.5">
            <span 
              class="inline-block rounded-full px-2.5 py-0.5 text-[10px] font-semibold"
              [ngClass]="getStatusColor(o.status)"
            >
              {{ o.status }}
            </span>
            <span class="block text-[10px] font-semibold text-emerald-700">Payment: {{ o.paymentStatus ?? 'Paid' }}</span>
          </div>
          <div class="flex flex-wrap gap-2 justify-end">
            <a [routerLink]="['/account/orders', o.orderId]" class="inline-flex items-center gap-1.5 rounded-full border border-hairline px-3 py-2 text-xs font-semibold hover:border-brand hover:text-brand">
              <svg lucideEye class="h-3.5 w-3.5"></svg> Details
            </a>
            <a [routerLink]="['/account/orders', o.orderId]" fragment="tracking" class="inline-flex items-center gap-1.5 rounded-full border border-hairline px-3 py-2 text-xs font-semibold hover:border-brand hover:text-brand">
              <svg lucideTruck class="h-3.5 w-3.5"></svg> Track
            </a>
            <button (click)="downloadInvoice(o)" class="inline-flex items-center gap-1.5 rounded-full border border-hairline px-3 py-2 text-xs font-semibold hover:border-brand hover:text-brand">
              <svg lucideDownload class="h-3.5 w-3.5"></svg> Invoice
            </button>
            <button (click)="buyAgain(o)" class="inline-flex items-center gap-1.5 rounded-full bg-brand text-white px-3 py-2 text-xs font-semibold hover:bg-brand-deep">
              <svg lucideRotateCcw class="h-3.5 w-3.5"></svg> Buy Again
            </button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class OrdersListComponent implements OnInit {
  private orderSvc = inject(OrderService);
  shop = inject(ShopService);
  cart = inject(CartService);

  orders = this.orderSvc.orders;
  loading = this.orderSvc.loading;

  ngOnInit() {
    this.orderSvc.loadOrders().subscribe();
  }

  formatDate(dateStr: string) {
    return new Date(dateStr).toLocaleDateString("en-NG", { day: "numeric", month: "short", year: "numeric" });
  }

  getTotalQty(order: any) {
    return order.items.reduce((n: number, i: any) => n + (i.quantity ?? i.qty ?? 0), 0);
  }

  formatPrice(minorUnits: number) {
    return this.orderSvc.formatPrice(minorUnits);
  }

  getStatusColor(status: string) {
    return this.orderSvc.statusColor(status);
  }

  getStatusLabel(status: string) {
    return this.orderSvc.statusLabel(status);
  }

  /** Generate a printable invoice from the verified order record; no financial value is invented. */
  downloadInvoice(order: BackendOrder) {
    const html = this.invoiceHtml(order);
    const printWindow = window.open('', '_blank', 'width=900,height=720');
    if (!printWindow) {
      this.saveInvoice(html, order);
      return;
    }
    printWindow.document.open();
    printWindow.document.write(html);
    printWindow.document.close();
    setTimeout(() => {
      try {
        printWindow.focus();
        printWindow.print();
      } catch { /* the invoice remains open for manual printing */ }
    }, 400);
  }

  private saveInvoice(html: string, order: BackendOrder) {
    const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `invoice-${order.orderNumber || order.orderId}.html`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  private invoiceHtml(order: BackendOrder): string {
    const rows = order.items.map(item => `
      <tr>
        <td>${this.escapeHtml(item.name)}<div class="sku">${this.escapeHtml(item.sku)}</div></td>
        <td class="num">${item.quantity}</td>
        <td class="num">${this.formatPrice(item.unitPriceMinorUnits)}</td>
        <td class="num">${this.formatPrice(item.unitPriceMinorUnits * item.quantity)}</td>
      </tr>`).join('');
    const discount = order.discountMinorUnits > 0
      ? `<div class="row"><span>Discount</span><span>-${this.formatPrice(order.discountMinorUnits)}</span></div>` : '';
    const refunded = order.refundedMinorUnits > 0
      ? `<div class="row"><span>Refunded</span><span>-${this.formatPrice(order.refundedMinorUnits)}</span></div>` : '';
    const paid = order.paymentStatus === 'PAID';
    const address = order.deliveryAddress;
    return `<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<title>Invoice ${this.escapeHtml(order.orderNumber)}</title>
<style>
  *{box-sizing:border-box}
  body{font-family:Arial,Helvetica,sans-serif;color:#1d1d1f;margin:0;padding:40px;font-size:13px}
  .head{display:flex;justify-content:space-between;align-items:flex-start;border-bottom:3px solid #D62828;padding-bottom:18px}
  .brand{font-size:30px;font-weight:800;letter-spacing:.08em;color:#D62828}
  .title{text-align:right}
  .title h1{margin:0;font-size:22px}
  .title p{margin:4px 0 0;color:#666}
  .grid{display:grid;grid-template-columns:1fr 1fr;gap:24px;margin:26px 0}
  .label{font-size:10px;text-transform:uppercase;letter-spacing:.14em;color:#777;margin-bottom:5px}
  .value{font-weight:700;font-size:14px}
  .muted{color:#555;line-height:1.6}
  .badge{display:inline-block;padding:3px 10px;border-radius:999px;font-size:10px;font-weight:700;letter-spacing:.08em;background:${paid ? '#dcfce7;color:#166534' : '#fef3c7;color:#92400e'}}
  table{width:100%;border-collapse:collapse;margin-top:12px}
  th{text-align:left;font-size:10px;text-transform:uppercase;letter-spacing:.12em;color:#777;border-bottom:1px solid #ddd;padding:8px 6px}
  td{padding:10px 6px;border-bottom:1px solid #eee;vertical-align:top}
  .num{text-align:right;white-space:nowrap}
  .sku{font-size:10px;color:#888;margin-top:2px}
  .totals{margin:18px 0 0 auto;width:280px}
  .row{display:flex;justify-content:space-between;padding:5px 0;color:#444}
  .row.total{border-top:2px solid #1d1d1f;margin-top:8px;padding-top:10px;font-size:16px;font-weight:800;color:#1d1d1f}
  footer{margin-top:34px;border-top:1px solid #ddd;padding-top:14px;color:#777;font-size:11px;line-height:1.6}
  @media print{body{padding:20px}.no-print{display:none}}
</style></head>
<body>
  <div class="head">
    <div><div class="brand">NIGSON</div><div class="muted">Order invoice</div></div>
    <div class="title">
      <h1>${this.escapeHtml(order.orderNumber)}</h1>
      <p>Placed ${this.formatDate(order.createdAt)}</p>
      <p><span class="badge">PAYMENT: ${this.escapeHtml(order.paymentStatus)}</span></p>
    </div>
  </div>
  <div class="grid">
    <div>
      <div class="label">Billed to</div>
      <div class="value">${this.escapeHtml(order.buyerContact.fullName)}</div>
      <div class="muted">${this.escapeHtml(order.buyerContact.email)}${order.buyerContact.phone ? `<br>${this.escapeHtml(order.buyerContact.phone)}` : ''}</div>
    </div>
    <div>
      <div class="label">Deliver to</div>
      <div class="muted">${this.escapeHtml(address.line1)}<br>${this.escapeHtml(address.city)}, ${this.escapeHtml(address.state)} ${this.escapeHtml(address.postalCode)}<br>${this.escapeHtml(address.countryCode)}</div>
    </div>
  </div>
  <table>
    <thead><tr><th>Item</th><th class="num">Qty</th><th class="num">Unit price</th><th class="num">Amount</th></tr></thead>
    <tbody>${rows}</tbody>
  </table>
  <div class="totals">
    <div class="row"><span>Subtotal</span><span>${this.formatPrice(order.subtotalMinorUnits)}</span></div>
    ${discount}
    <div class="row"><span>Delivery</span><span>${this.formatPrice(order.shippingMinorUnits)}</span></div>
    <div class="row"><span>Tax</span><span>${this.formatPrice(order.taxMinorUnits)}</span></div>
    ${refunded}
    <div class="row total"><span>Total</span><span>${this.formatPrice(order.totalMinorUnits)}</span></div>
  </div>
  <footer>
    Generated from the verified order record (version ${order.version}) on ${this.escapeHtml(new Date().toLocaleString('en-NG'))}.<br>
    Order status: ${this.escapeHtml(order.status)} &bull; Fulfillment: ${this.escapeHtml(order.deliveryMethod)}.
  </footer>
</body></html>`;
  }

  private escapeHtml(value: unknown): string {
    const entities: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
    return String(value ?? '').replace(/[&<>"']/g, ch => entities[ch]);
  }

  /** TASK-36: Buy again — adds each order item back to server cart */
  buyAgain(order: any) {
    (order.items as import('shared-ui').OrderItem[]).forEach((i) => {
      this.cart.addItem(i.skuId, i.quantity ?? 1).subscribe();
    });
  }
}
