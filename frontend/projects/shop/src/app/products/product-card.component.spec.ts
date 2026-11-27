import '@angular/compiler';
import {Injector} from '@angular/core';
import {CatalogService,ShopService} from 'shared-ui';
import {of,throwError} from 'rxjs';
import {describe,it,expect,vi} from 'vitest';
import {ProductCardComponent} from './product-card.component';
import {QtyStepperComponent} from '../../../../shared-ui/src/lib/qty-stepper/qty-stepper.component';

function setup(fail=false){
 const catalog={getPrice:vi.fn(()=>fail?throwError(()=>({status:403})):of({priceMinorUnits:55555500,minimumQuantity:30,packMultiple:2})),formatPrice:(value:number)=>String(value),primaryImage:()=>'/asset.png'};
 const injector=Injector.create({providers:[{provide:CatalogService,useValue:catalog},{provide:ShopService,useValue:{}},{provide:ProductCardComponent,useFactory:()=>new ProductCardComponent()}]});
 const card=injector.get(ProductCardComponent);card.p={id:'product',skuId:'sku',sku:'SKU',name:'Product',description:'',categoryId:'category',stock:100,stockStatus:'IN_STOCK',priceMinorUnits:66666700,minimumQuantity:1,packMultiple:1};
 return card;
}
describe('Catalogue card account pricing',()=>{
 it('uses current price response minimums when adding from a card',()=>{
  const card=setup();card.ngOnInit();expect(card.displayPrice()).toBe('55555500');expect(card.minimumQuantity()).toBe(30);expect(card.packMultiple()).toBe(2);
  const stepper:any=Object.create(QtyStepperComponent.prototype);stepper.product=card.productForStepper();stepper.shop={cart:()=>[],addToCart:vi.fn()};stepper.add();expect(stepper.shop.addToCart.mock.calls[0][1]).toBe(30);
 });
 it('does not retain a cached retail amount if current account pricing is denied',()=>{const card=setup(true);card.ngOnInit();expect(card.displayPrice()).toBe('Contact for price');expect(card.priceMinorUnits()).toBeNull();expect(card.priceLoading()).toBe(false);});
});
