import type { AdminProduct } from './admin.service';

/** Stock remains the physical count for existing adjustment consumers. */
export function inventoryTotals(rows: any[]) {
  const amount = (value: any): number => {
    const result = Number(value ?? 0);
    if (!Number.isFinite(result)) throw new Error('Inventory contains an invalid quantity. Refresh inventory.');
    return result;
  };
  const round = (value: number) => Math.round(value * 1000) / 1000;
  const onHand = round(rows.reduce((sum, row) => sum + amount(row.onHand), 0));
  const reserved = round(rows.reduce((sum, row) => sum + amount(row.reserved), 0));
  const allocated = round(rows.reduce((sum, row) => sum + amount(row.allocated), 0));
  const available = round(rows.reduce((sum, row) => sum + (row.available == null
    ? amount(row.onHand) - amount(row.reserved) - amount(row.allocated)
    : amount(row.available)), 0));
  return { stock: onHand, onHand, reserved, allocated, available };
}

/** Join stock to the catalogue's actual SKU identifiers, summing warehouse balances. */
export function applyInventory(products: AdminProduct[], balances: any[]): AdminProduct[] {
  return products.map(product => {
    const rows = balances.filter(balance => balance.skuId === product.skuId);
    return {
      ...product,
      ...inventoryTotals(rows)
    };
  });
}

export function mapStaffProducts(products: any[], balances: any[]): AdminProduct[] {
  return applyInventory(products.map(product => {
    const sku = product.skus?.[0];
    const images = (product.assets ?? []).map((asset: any) => asset.url).filter(Boolean);
    const skuCode = sku?.code ?? '';
    const specs = product.specifications || {};
    return {
      id: product.id,
      version: product.version,
      slug: product.slug,
      brandId: product.brand?.id,
      categoryIds: product.categories?.map((category: any) => category.id) || [],
      packSize: sku?.packSize,
      skuId: sku?.id,
      sku: skuCode,
      name: product.name,
      category: product.categories?.map((category: any) => category.name).join(', ') || 'Uncategorised',
      brand: product.brand?.name ?? '',
      price: 0,
      retail: undefined,
      wholesale: undefined,
      distributor: undefined,
      priceSubtitle: specs['price_subtitle'] || undefined,
      desc: product.description ?? '',
      image: images[0] ?? '',
      images,
      status: product.status,
      stock: 0,
      reserved: 0,
      rating: 0,
      lowLevel: 10,
      specifications: product.specifications
    };
  }), balances);
}