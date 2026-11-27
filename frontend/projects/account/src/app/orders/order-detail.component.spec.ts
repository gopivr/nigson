import '@angular/compiler';
import { signal } from '@angular/core';
import { describe, it, expect } from 'vitest';
import { OrderDetailComponent } from './order-detail.component';
function component(order: any) {
 const c: any = Object.create(OrderDetailComponent.prototype);
 c.order = signal(order);
 return c;
}
describe('Order cancellation presentation', () => {
 it('uses server eligibility and excludes existing cancellation requests', () => {
  expect(component({ status: 'PAID', cancellationAllowed: true }).canCancel()).toBe(true);
  expect(component({ status: 'SHIPPED', cancellationAllowed: false }).canCancel()).toBe(false);
  expect(component({ status: 'PAID' }).canCancel()).toBe(false);
  expect(component({ cancellationAllowed: true, cancellation: { state: 'REQUESTED' } }).canCancel()).toBe(false);
 });
 it('distinguishes a pending carrier outcome from confirmed cancellation', () => {
  expect(component({ cancellation: { state: 'WAITING_CARRIER' } }).cancellationMessage()).toContain('waiting for delivery');
  expect(component({ cancellation: { state: 'CANCELLED' } }).cancellationMessage()).toContain('has been cancelled');
 });
 it('shows refund review, success and attention independently', () => {
  const c = component({ cancellation: { state: 'CANCELLED', refundState: 'REQUESTED' } });
  expect(c.refundMessage()).toContain('Finance review');
  c.order.set({ cancellation: { state: 'CANCELLED', refundState: 'FAILED' } });
  expect(c.refundMessage()).toContain('needs attention');
  expect(c.cancellationMessage()).toContain('has been cancelled');
 });
});

function returnComponent(view: any) {
 const c: any = component({});
 c.returns = signal(view);
 c.returnLineId = view.lines.find((line: any) => line.availableQuantity > 0)?.orderLineId ?? '';
 return c;
}

describe('Order return presentation', () => {
 it('offers only lines with remaining delivered quantity and respects the SKU scale', () => {
  const c = returnComponent({ delivered: true, lines: [
   { orderLineId: 'line-1', name: 'Whole item', orderedQuantity: 3, availableQuantity: 2, quantityScale: 0 },
   { orderLineId: 'line-2', name: 'Measured item', orderedQuantity: '1.5', availableQuantity: '0.5', quantityScale: 3 },
   { orderLineId: 'line-3', name: 'Fully returned', orderedQuantity: 1, availableQuantity: 0, quantityScale: 0 }
  ] });
  expect(c.returnableLines().map((line: any) => line.orderLineId)).toEqual(['line-1', 'line-2']);
  expect(c.scaleStep()).toBe(1);
  c.returnLineId = 'line-2';
  expect(c.scaleStep()).toBe(0.001);
 });

 it('keeps review, approval, rejection and refund language separate', () => {
  const c = returnComponent({ delivered: true, lines: [] });
  expect(c.returnStateLabel('REQUESTED')).toBe('Under review');
  expect(c.returnMessage({ state: 'REQUESTED' })).toContain('Operations are reviewing');
  expect(c.returnMessage({ state: 'APPROVED' })).toContain('approved');
  expect(c.returnMessage({ state: 'REJECTED', decisionReason: 'Outside the recorded policy' })).toContain('Outside the recorded policy');
  expect(c.returnMessage({ state: 'RECEIVED', restock: false })).toContain('not returned to saleable stock');
  expect(c.returnRefundMessage({ refundState: 'REQUESTED' })).toContain('Finance approval');
  expect(c.returnRefundMessage({ refundState: 'SUCCEEDED' })).toContain('completed');
 });
});
