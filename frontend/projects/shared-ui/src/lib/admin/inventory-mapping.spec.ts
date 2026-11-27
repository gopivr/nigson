import { describe, it, expect } from 'vitest';
import { mapStaffProducts, applyInventory } from './inventory-mapping';

const product = {
  id: 'product-1', name: 'Demo Laptop', status: 'DRAFT',
  brand: { id: 'brand-1', name: 'Demo Electronics' },
  categories: [{ id: 'cat-1', name: 'Laptops' }],
  skus: [{ id: 'sku-1', code: 'DEMO-EL-01-01' }], assets: []
};

describe('Staff inventory mapping', () => {
  it('uses staff category and SKU IDs rather than inferring them from the slug', () => {
    const [row] = mapStaffProducts([product], [{ skuId: 'sku-1', onHand: '18', reserved: '2' }]);
    expect(row).toMatchObject({ id: 'product-1', skuId: 'sku-1', sku: 'DEMO-EL-01-01', category: 'Laptops', stock: 18, reserved: 2, status: 'DRAFT' });
  });
  it('preserves zero stock and never invents a default quantity', () => {
    expect(mapStaffProducts([product], [{ skuId: 'sku-1', onHand: '0', reserved: '0' }])[0].stock).toBe(0);
    expect(mapStaffProducts([product], [])[0].stock).toBe(0);
  });
  it('sums multiple warehouses and ignores unrelated SKU balances', () => {
    const rows = [{ skuId: 'sku-1', onHand: '5', reserved: '1' }, { skuId: 'sku-1', onHand: '7', reserved: '2' }, { skuId: 'other-sku', onHand: '1000' }];
    expect(mapStaffProducts([product], rows)[0]).toMatchObject({ stock: 12, reserved: 3 });
  });
  it('clears old stock after the authoritative balances become empty', () => {
    const products = mapStaffProducts([product], [{ skuId: 'sku-1', onHand: '18' }]);
    expect(applyInventory(products, [])[0].stock).toBe(0);
  });
  it('does not fabricate a retail price or product rating', () => {
    expect(mapStaffProducts([product], [])[0]).toMatchObject({ price: 0, rating: 0 });
  });
});

describe('Available inventory after checkout and payment', () => {
 it('keeps physical stock distinct from available stock after paid allocation', () => {
  const [row] = mapStaffProducts([product], [{skuId:'sku-1',onHand:'14',reserved:'0',allocated:'2',available:'12'}]);
  expect(row).toMatchObject({stock:14,onHand:14,reserved:0,allocated:2,available:12});
 });
 it('does not subtract the same item twice when reservation becomes allocation', () => {
  const before=mapStaffProducts([product],[{skuId:'sku-1',onHand:'14',reserved:'1',allocated:'0'}])[0];
  const after=mapStaffProducts([product],[{skuId:'sku-1',onHand:'14',reserved:'0',allocated:'1'}])[0];
  expect(before.available).toBe(13);expect(after.available).toBe(13);expect(after.stock).toBe(14);
 });
 it('uses backend availability and sums decimal allocations across warehouses', () => {
  const [row]=mapStaffProducts([product],[{skuId:'sku-1',onHand:'2.5',reserved:'0.5',allocated:'1',available:'1'},{skuId:'sku-1',onHand:'3',reserved:'1',allocated:'0.5',available:'1.5'}]);
  expect(row).toMatchObject({onHand:5.5,reserved:1.5,allocated:1.5,available:2.5});
 });
 it('reports zero available even while all physical units remain in the warehouse', () => {
  expect(mapStaffProducts([product],[{skuId:'sku-1',onHand:'14',reserved:'4',allocated:'10'}])[0]).toMatchObject({stock:14,available:0});
 });
 it('clears allocated and available values on an empty authoritative refresh', () => {
  const current=mapStaffProducts([product],[{skuId:'sku-1',onHand:'14',allocated:'2'}]);
  expect(applyInventory(current,[])[0]).toMatchObject({onHand:0,reserved:0,allocated:0,available:0});
 });
});
