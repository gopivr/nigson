import '@angular/compiler';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { describe, it, expect, vi } from 'vitest';
import { OrderService } from './order.service';

function service() {
  const instance: any = Object.create(OrderService.prototype);
  instance.http = { get: vi.fn(), post: vi.fn() };
  instance.orders = signal([]);
  instance.cancellationError = signal(null);
  instance.cancellationKeys = new Map();
  instance.returnError = signal(null);
  instance.returnKeys = new Map();
  return instance;
}

const rawOrder = {
  id: '01a1e1f3-7f84-7c1c-9790-6a048b3b651e',
  orderNumber: 'NIG_01a1e1f37f847c1c97906a048b3b651e',
  status: 'PAID',
  paymentStatus: 'PAID',
  fulfillmentStatus: 'UNFULFILLED',
  currency: 'NGN',
  totals: { subtotal: '23254.88', discount: '0.00', tax: '1744.12', delivery: '33.00', grandTotal: '25032.00', refunded: '0.00' },
  lines: [{ id: 'line-1', skuId: 'sku-1', skuCode: 'NX-PB20-BLK', name: 'Nexa Power Bank 20000 mAh', quantity: '1', unitAmount: '24999.00', lineTotal: '24999.00' }],
  address: { label: 'Checkout snapshot', recipient: 'Gopi Ravi', phone: '+2341234123412', line1: '12 Adeola Odeku Street', line2: null, city: 'Eti-Osa', region: 'Lagos', postalCode: null, countryCode: 'NG', isDefault: false },
  version: 2,
  createdAt: '2026-11-16T01:23:13.924524Z'
};

describe('OrderService backend mapping', () => {
  it('maps the commerce order DTO onto the storefront view model', () => {
    const order = service().mapOrder(rawOrder);
    expect(order.orderId).toBe(rawOrder.id);
    expect(order.status).toBe('PAID');
    expect(order.items[0]).toMatchObject({
      sku: 'NX-PB20-BLK', name: 'Nexa Power Bank 20000 mAh', quantity: 1, unitPriceMinorUnits: 2499900
    });
    expect(order.subtotalMinorUnits).toBe(2325488);
    expect(order.discountMinorUnits).toBe(0);
    expect(order.shippingMinorUnits).toBe(3300);
    expect(order.taxMinorUnits).toBe(174412);
    expect(order.totalMinorUnits).toBe(2503200);
    expect(order.refundedMinorUnits).toBe(0);
    expect(order.deliveryAddress).toMatchObject({ line1: '12 Adeola Odeku Street', city: 'Eti-Osa', state: 'Lagos', countryCode: 'NG' });
    expect(order.buyerContact).toMatchObject({ fullName: 'Gopi Ravi', phone: '+2341234123412' });
    expect(order.version).toBe(2);
  });

  it('getOrder resolves the same view model used by the order detail page', () => {
    const s = service();
    s.http.get.mockReturnValue(of(rawOrder));
    let resolved: any;
    s.getOrder(rawOrder.id).subscribe((value: any) => resolved = value);
    expect(s.http.get).toHaveBeenCalledWith('/api/v1/orders/' + rawOrder.id);
    expect(resolved.items).toHaveLength(1);
    expect(resolved.totalMinorUnits).toBe(2503200);
  });

  it('maps tracking shipments and events instead of exposing the raw envelope', () => {
    const s = service();
    s.http.get.mockReturnValue(of({
      orderId: 'order-1',
      shipments: [{ id: 'shipment-1', status: 'SHIPPED', providerOrderId: 'panda-123' }],
      events: [{ status: 'PICKED_UP', occurredAt: '2026-11-16T03:00:00Z', note: 'Collected from warehouse' }]
    }));
    let resolved: any;
    s.getTracking('order-1').subscribe((value: any) => resolved = value);
    expect(resolved.orderId).toBe('order-1');
    expect(resolved.status).toBe('SHIPPED');
    expect(resolved.trackingNumber).toBe('panda-123');
    expect(resolved.carrier).toBe('PandaGo');
    expect(resolved.scans[0]).toMatchObject({ status: 'PICKED_UP', description: 'Collected from warehouse' });
  });

  it('keeps an unfulfilled order trackable with an explicit processing state', () => {
    const s = service();
    s.http.get.mockReturnValue(of({ orderId: 'order-2', shipments: [], events: [] }));
    let resolved: any;
    s.getTracking('order-2').subscribe((value: any) => resolved = value);
    expect(resolved).toMatchObject({ orderId: 'order-2', status: 'PROCESSING', scans: [] });
  });
});

describe('Order cancellation', () => {
  it('preserves cancellation progress and authoritative eligibility', () => {
    const s = service();
    const c = { id: 'cancel-1', state: 'WAITING_CARRIER', refundState: null, errorCode: null };
    expect(s.mapOrder({ ...rawOrder, cancellation: c, cancellationAllowed: false })).toMatchObject({ cancellation: c, cancellationAllowed: false });
    expect(s.mapOrder(rawOrder).cancellationAllowed).toBe(false);
  });
  it('reuses the original key and version after response loss', () => {
    const s = service();
    s.http.get.mockReturnValueOnce(of({ ...rawOrder, cancellationAllowed: true })).mockReturnValueOnce(of({ ...rawOrder, version: 3, cancellationAllowed: true }));
    s.http.post.mockReturnValue(throwError(() => ({ status: 503 })));
    s.cancelOrder(rawOrder.id).subscribe();
    s.cancelOrder(rawOrder.id).subscribe();
    const first = s.http.post.mock.calls[0][2].headers;
    const second = s.http.post.mock.calls[1][2].headers;
    expect(second.get('Idempotency-Key')).toBe(first.get('Idempotency-Key'));
    expect(second.get('If-Match')).toBe(first.get('If-Match'));
    expect(s.cancellationError()).toContain('Retry');
  });
  it('returns the persisted cancellation without sending another command', () => {
    const s = service();
    s.http.get.mockReturnValue(of({ ...rawOrder, cancellation: { id: 'cancel-1', state: 'REQUESTED' } }));
    s.cancelOrder(rawOrder.id).subscribe();
    expect(s.http.post).not.toHaveBeenCalled();
  });
});

const rawReturns = {
  orderId: rawOrder.id, orderVersion: 2, delivered: true,
  lines: [{ orderLineId: 'line-1', name: 'Nexa Power Bank 20000 mAh', orderedQuantity: '5', availableQuantity: '3', quantityScale: 0 }],
  requests: [{ id: 'return-1', orderId: rawOrder.id, orderLineId: 'line-1', productName: 'Nexa Power Bank 20000 mAh', quantity: '2', reason: 'Synthetic damaged product return', policyRevision: 'POLICY_1', state: 'REQUESTED', decisionReason: null, instructions: null, receiptReference: null, restock: null, receivedAt: null, refundId: null, refundState: null, version: 0, createdAt: '2026-11-22T10:00:00Z' }],
  refundableAmount: '100.00', paystackRefundAvailable: true
};

describe('Order returns', () => {
  it('maps bounded return lines and requests without provider detail', () => {
    const view = service().mapReturns(rawReturns);
    expect(view).toMatchObject({ orderId: rawOrder.id, orderVersion: 2, delivered: true, refundableAmount: 100, paystackRefundAvailable: true });
    expect(view.lines[0]).toMatchObject({ orderLineId: 'line-1', orderedQuantity: 5, availableQuantity: 3, quantityScale: 0 });
    expect(view.requests[0]).toMatchObject({ id: 'return-1', quantity: 2, state: 'REQUESTED' });
  });

  it('reuses the original key and order version after response loss', () => {
    const s = service();
    s.http.get.mockReturnValue(of(rawReturns));
    s.http.post.mockReturnValue(throwError(() => ({ status: 503 })));
    s.requestReturn(rawOrder.id, 'line-1', 2, 'Synthetic damaged product return').subscribe();
    s.requestReturn(rawOrder.id, 'line-1', 2, 'Synthetic damaged product return').subscribe();
    const first = s.http.post.mock.calls[0][2].headers;
    const second = s.http.post.mock.calls[1][2].headers;
    expect(second.get('Idempotency-Key')).toBe(first.get('Idempotency-Key'));
    expect(second.get('If-Match')).toBe('"2"');
    expect(s.returnError()).toContain('Retry');
  });

  it('does not send a return command before delivery', () => {
    const s = service();
    s.http.get.mockReturnValue(of({ ...rawReturns, delivered: false }));
    s.requestReturn(rawOrder.id, 'line-1', 1, 'Synthetic undelivered product return').subscribe();
    expect(s.http.post).not.toHaveBeenCalled();
    expect(s.returnError()).toContain('after the order has been delivered');
  });
});
