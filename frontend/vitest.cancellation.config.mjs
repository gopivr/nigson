import { fileURLToPath } from 'node:url';
export default {
 resolve: { alias: { 'shared-ui': fileURLToPath(new URL('./projects/shared-ui/src/public-api.ts', import.meta.url)) } },
 test: { environment: 'jsdom', include: ['projects/shared-ui/src/lib/order.service.spec.ts', 'projects/account/src/app/orders/order-detail.component.spec.ts', 'projects/account/src/app/orders/orders-list.component.spec.ts', 'projects/admin-shop/src/app/sales/orders.component.spec.ts'] }
};
