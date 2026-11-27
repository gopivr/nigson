import '@angular/compiler';
import {Injector} from '@angular/core';
import {HttpClient,HttpErrorResponse} from '@angular/common/http';
import {of,throwError,firstValueFrom} from 'rxjs';
import {describe,it,expect,vi} from 'vitest';
import {RetailPricingService,retailAmount,canEditRetailPrice} from './retail-pricing.service';
import {CatalogService} from './catalog.service';

const binding={marketId:'market',code:'NG',priceBookId:'book',version:7};
const entries=[{skuId:'sku',minimumQuantity:'1',unitAmount:'26000.00'},{skuId:'sku',minimumQuantity:'10',unitAmount:'1500.00'},{skuId:'other',minimumQuantity:'1',unitAmount:'800.00'}];
const book={id:'book',code:'original',currency:'NGN',segment:'RETAIL',active:true,entries};
function setup() {
 const http={get:vi.fn(()=>of({items:[book],totalPages:1,retailBindings:[binding]})),post:vi.fn((url:string)=>of(url.endsWith('/activation')?{id:'replacement',version:1}:{id:'replacement',version:0})),put:vi.fn(()=>of({version:8}))};
 const injector=Injector.create({providers:[{provide:HttpClient,useValue:http},{provide:RetailPricingService,useFactory:()=>new RetailPricingService()},{provide:CatalogService,useFactory:()=>new CatalogService()}]});
 return {service:injector.get(RetailPricingService),catalog:injector.get(CatalogService),http};
}
describe('Real retail selling price updates',()=>{
 it('preserves other SKUs, quantity tiers and the immutable source before finance activation and market switch',async()=>{
  const {service,http}=setup();const context=await service.load('sku');
  await service.save(context,'sku','1739','1');
  expect(http.post.mock.calls[0][1].entries).toEqual([{...entries[0],unitAmount:'1739.00'},entries[1],entries[2]]);
  expect(entries[0].unitAmount).toBe('26000.00');
  expect(http.post.mock.calls[0][1].code).not.toBe(book.code);
  expect(http.post.mock.calls[1][0]).toBe('/api/v1/staff/price-books/replacement/activation');
  expect(http.put.mock.calls[0][1]).toMatchObject({priceBookId:'replacement'});
  expect(http.put.mock.calls[0][2].headers.get('If-Match')).toBe('"7"');
 });
 it('does not switch the market when finance activation is denied',async()=>{
  const {service,http}=setup();const context=await service.load('sku');
  http.post.mockImplementation((url:string)=>url.endsWith('/activation')?throwError(()=>new HttpErrorResponse({status:403,error:{detail:'Recent MFA required'}})):of({id:'replacement',version:0}));
  await expect(service.save(context,'sku','1739','1')).rejects.toThrow('Recent MFA required');
  expect(http.put).not.toHaveBeenCalled();
 });
 it('reports a concurrent market edit without retrying or claiming a saved price',async()=>{
  const {service,http}=setup();const context=await service.load('sku');
  http.put.mockReturnValue(throwError(()=>new HttpErrorResponse({status:409,error:{detail:'Version mismatch'}})));
  await expect(service.save(context,'sku','1739','1')).rejects.toThrow('Selling price was not confirmed');
  expect(http.put).toHaveBeenCalledTimes(1);
 });
 it('fails closed when an older backend does not expose the market binding',async()=>{
  const {service,http}=setup();http.get.mockReturnValue(of({items:[book],totalPages:1}));
  await expect(service.load('sku')).rejects.toThrow('backend must provide');
  expect(http.post).not.toHaveBeenCalled();
 });
 it('uses decimal strings and rejects invalid or overprecise amounts',()=>{
  expect(retailAmount('1739')).toBe('1739.00');expect(retailAmount('0.10')).toBe('0.10');
  for(const value of ['-1','1.001','NaN','1e3',''])expect(()=>retailAmount(value)).toThrow();
 });
});
describe('Authoritative storefront pricing',()=>{
 it('ignores metadata prices and refetches prices after changes',async()=>{
  const {catalog,http}=setup();let amount='26000.00';
  http.get.mockImplementation((url:string)=>of(url.includes('/price')?{skuId:'sku',unitAmount:amount,currency:'NGN'}:{id:'11111111-2222-3333-4444-555555555501',name:'Earbuds',skus:[{id:'sku',code:'EARBUDS'}],specifications:{starting_price:'1739'}}));
  const first=await firstValueFrom(catalog.getProduct('11111111-2222-3333-4444-555555555501'));
  expect(first.priceMinorUnits).toBe(2600000);
  amount='1739.00';
  const second=await firstValueFrom(catalog.getProduct('11111111-2222-3333-4444-555555555501'));
  expect(second.priceMinorUnits).toBe(173900);
 });
 it('does not fall back to metadata when the selling price is unavailable',async()=>{
  const {catalog,http}=setup();http.get.mockImplementation((url:string)=>url.includes('/price')?throwError(()=>new HttpErrorResponse({status:404})):of({id:'product',name:'Earbuds',skus:[{id:'sku'}],specifications:{starting_price:'1739'}}));
  const product=await firstValueFrom(catalog.getProduct('11111111-2222-3333-4444-555555555501'));
  expect(product.priceMinorUnits).toBeUndefined();expect(product.price).toBeUndefined();
 });
});

describe('Normal admin pricing permission',()=>{
 it('allows ADMIN without needing Sales or Finance roles',()=>{expect(canEditRetailPrice(['ADMIN'])).toBe(true);});
 it('does not grant customer or single-role staff price editing',()=>{for(const roles of [[],['CUSTOMER'],['SALES'],['FINANCE']])expect(canEditRetailPrice(roles)).toBe(false);});
 it('preserves the existing Sales and Finance workflow',()=>{expect(canEditRetailPrice(['SALES','FINANCE'])).toBe(true);});
});
