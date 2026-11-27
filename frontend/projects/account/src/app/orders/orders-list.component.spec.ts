import '@angular/compiler';
import { describe, it, expect } from 'vitest';
import { OrdersListComponent } from './orders-list.component';
import type { BackendOrder } from 'shared-ui';

function component() {
  const instance: any = Object.create(OrdersListComponent.prototype);
  instance.orderSvc = { formatPrice: (minor: number) => 'NGN ' + (minor / 100).toFixed(2) };
  return instance;
}

const order: BackendOrder = {
  orderId: '01a1e1f3-7f84-7c1c-9790-6a048b3b651e',
  orderNumber: 'NIG_01a1e1f37f847c1c97906a048b3b651e',
  status: 'PAID',
  paymentStatus: 'PAID',
  items: [{
    lineId: 'line-1', skuId: 'sku-1', sku: 'NX-PB20-BLK',
    name: 'Nexa Power Bank 20000 mAh', quantity: 1, unitPriceMinorUnits: 2499900
  }],
  subtotalMinorUnits: 2325488,
  discountMinorUnits: 100,
  shippingMinorUnits: 3300,
  taxMinorUnits: 174412,
  totalMinorUnits: 2503200,
  refundedMinorUnits: 0,
  currency: 'NGN',
  deliveryAddress: { line1: '12 Adeola Odeku Street', city: 'Eti-Osa', state: 'Lagos', countryCode: 'NG' },
  buyerContact: { fullName: 'Gopi Ravi', email: 'gopi@example.com', phone: '+2341234123412' },
  deliveryMethod: 'UNFULFILLED',
  version: 2,
  createdAt: '2026-11-16T01:23:13.924524Z',
  updatedAt: '2026-11-16T01:30:00Z'
};

describe('Order invoice document', () => {
  it('renders the verified order values', () => {
    const html = component().invoiceHtml(order);
    expect(html).toContain('NIG_01a1e1f37f847c1c97906a048b3b651e');
    expect(html).toContain('Nexa Power Bank 20000 mAh');
    expect(html).toContain('NX-PB20-BLK');
    expect(html).toContain('NGN 25032.00');
    expect(html).toContain('PAYMENT: PAID');
    expect(html).toContain('12 Adeola Odeku Street');
  });

  it('shows a discount only when the order carries one', () => {
    expect(component().invoiceHtml(order)).toContain('Discount');
    expect(component().invoiceHtml({ ...order, discountMinorUnits: 0 })).not.toContain('Discount');
  });

  it('escapes customer-controlled text', () => {
    const html = component().invoiceHtml({ ...order, items: [{ ...order.items[0], name: '<script>alert(1)</script>' }] });
    expect(html).not.toContain('<script>alert(1)</script>');
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;');
  });
});
