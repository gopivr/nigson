import base from './vitest.distributor.config.mjs';
export default {...base,test:{...base.test,maxWorkers:2,include:['projects/shared-ui/src/lib/wishlist.service.spec.ts','projects/shared-ui/src/lib/wishlist-button.component.spec.ts','projects/account/src/app/wishlist/wishlist.component.spec.ts']}};
