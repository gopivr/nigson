import '@angular/compiler';
import { describe, it, expect } from 'vitest';
import { resolveCategory } from './product-list.component';

describe('resolveCategory helper', () => {
  const mockCategories = [
    { id: 'cat-laptops', name: 'Laptops', slug: 'laptops' },
    { id: 'cat-smartphones', name: 'Smartphones', slug: 'smartphones' },
    { id: 'cat-tablets', name: 'Tablets', slug: 'tablets' },
    { id: 'cat-audio', name: 'Audio', slug: 'audio' },
    { id: 'cat-gaming', name: 'Gaming', slug: 'gaming' },
    { id: 'cat-networking', name: 'Networking', slug: 'networking' },
    { id: 'cat-storage', name: 'Storage', slug: 'storage' },
    { id: 'cat-power-accessories', name: 'Power and Accessories', slug: 'power-and-accessories' },
    { id: 'cat-power-banks', name: 'Power Banks', slug: 'power-banks' },
    { id: 'cat-earbuds', name: 'Earbuds', slug: 'earbuds' },
    { id: 'cat-cables', name: 'Cables', slug: 'cables' },
    { id: 'cat-chargers', name: 'Chargers', slug: 'chargers' },
    { id: 'cat-sockets', name: 'Extension Sockets', slug: 'extension-sockets' },
    { id: 'cat-wireless', name: 'Wireless Chargers', slug: 'wireless-chargers' }
  ];

  it('matches direct UUIDs', () => {
    const uuid = '902c6045-6c27-5d01-ab37-ba50c25267d3';
    expect(resolveCategory(uuid, mockCategories)).toBe(uuid);
  });

  it('matches exact category name case-insensitively', () => {
    expect(resolveCategory('Earbuds', mockCategories)).toBe('cat-earbuds');
    expect(resolveCategory('earbuds', mockCategories)).toBe('cat-earbuds');
    expect(resolveCategory('Power Banks', mockCategories)).toBe('cat-power-banks');
  });

  it('matches typo or trailing characters such as Earbudsj', () => {
    expect(resolveCategory('Earbudsj', mockCategories)).toBe('cat-earbuds');
    expect(resolveCategory('earbudsj', mockCategories)).toBe('cat-earbuds');
  });

  it('matches semantic storefront aliases (Home Chargers, Power Strips, Mobile Accessories)', () => {
    expect(resolveCategory('Home Chargers', mockCategories)).toBe('cat-chargers');
    expect(resolveCategory('Car Chargers', mockCategories)).toBe('cat-chargers');
    expect(resolveCategory('Power Strips', mockCategories)).toBe('cat-sockets');
    expect(resolveCategory('Mobile Accessories', mockCategories)).toBe('cat-cables');
    expect(resolveCategory('Audio', mockCategories)).toBe('cat-audio');
  });

  it('returns null for empty or non-matching string', () => {
    expect(resolveCategory('', mockCategories)).toBeNull();
    expect(resolveCategory('NonExistentCategoryXYZ', mockCategories)).toBeNull();
  });
});
