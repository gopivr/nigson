const fs = require('fs');
const path = require('path');

function replaceInFile(filePath) {
  let content = fs.readFileSync(filePath, 'utf8');
  let original = content;
  
  // Replace string literals
  content = content.replace(/\/shop\/products/g, '/products');
  content = content.replace(/\/shop\/cart/g, '/cart');
  content = content.replace(/\/shop\/checkout/g, '/checkout');
  content = content.replace(/\/shop\/quote/g, '/quote');
  content = content.replace(/\/shop\/search/g, '/search');
  content = content.replace(/routerLink="\/shop"/g, 'routerLink="/products"');
  
  // Replace array literals used in [routerLink]
  content = content.replace(/'\/shop\/products'/g, "'/products'");
  content = content.replace(/'\/shop\/cart'/g, "'/cart'");
  content = content.replace(/'\/shop\/checkout'/g, "'/checkout'");
  content = content.replace(/'\/shop\/quote'/g, "'/quote'");
  content = content.replace(/'\/shop\/search'/g, "'/search'");

  if (content !== original) {
    fs.writeFileSync(filePath, content, 'utf8');
    console.log('Updated', filePath);
  }
}

const filesToUpdate = [
  'projects/shell/src/app/layout/header/nav-search.component.ts',
  'projects/shell/src/app/layout/header/header.html',
  'projects/shell/src/app/layout/footer/footer.ts',
  'projects/shop/src/app/products/product-detail.component.ts',
  'projects/shop/src/app/checkout/checkout.component.ts',
  'projects/shop/src/app/cart/cart.component.ts',
  'projects/shop/src/app/checkout/order-success.component.ts',
  'projects/shop/src/app/products/product-card.component.ts',
  'projects/marketing/src/app/home/home.html',
  'projects/marketing/src/app/pages/insights.component.ts',
  'projects/marketing/src/app/pages/support.component.ts',
  'projects/marketing/src/app/components/wholesale-cta.component.ts',
  'projects/marketing/src/app/components/new-arrivals.component.ts',
  'projects/marketing/src/app/components/flash-sale.component.ts',
  'projects/marketing/src/app/components/best-sellers.component.ts',
  'projects/account/src/app/orders/orders-list.component.ts'
];

filesToUpdate.forEach(file => replaceInFile(path.join(process.cwd(), file)));
