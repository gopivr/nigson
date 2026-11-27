import '@angular/compiler';
import { TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { beforeAll,beforeEach,afterEach,describe,it,expect,vi } from 'vitest';
import { WishlistService } from './wishlist.service';
import { SessionService } from './session.service';
import { CatalogService } from './catalog.service';
const sku='11111111-1111-4111-8111-111111111111',product='22222222-2222-4222-8222-222222222222';
let service:WishlistService,http:HttpTestingController,session:any;
beforeAll(()=>TestBed.initTestEnvironment(BrowserTestingModule,platformBrowserTesting()));
beforeEach(()=>{
 session={authenticated:signal(true),userId:signal('account-a'),signIn:vi.fn()};
 TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting(),{provide:SessionService,useValue:session},{provide:CatalogService,useValue:{resolveImageUrl:(url:string)=>url||''}}]});
 service=TestBed.inject(WishlistService);http=TestBed.inject(HttpTestingController);TestBed.tick();http.expectOne('/api/v1/me/wishlist').flush([]);
});
afterEach(()=>{http.verify();TestBed.resetTestingModule();});
describe('account wishlist',()=>{
 it('saves actual SKU with idempotency and reloads server membership',()=>{
  service.set(product,sku,true);const req=http.expectOne('/api/v1/me/wishlist/items/'+sku);expect(req.request.method).toBe('PUT');expect(req.request.body).toBeNull();expect(req.request.headers.has('Idempotency-Key')).toBe(true);expect(service.isSaved(sku)).toBe(false);
  service.set(product,sku,true);http.expectNone('/api/v1/me/wishlist/items/'+sku);req.flush(null,{status:204,statusText:'No Content'});
  http.expectOne('/api/v1/me/wishlist').flush([{skuId:sku,productId:product,savedAt:'2026-11-17T00:00:00Z',available:true}]);expect(service.isSaved(sku)).toBe(true);
 });
 it('retains an item on failed removal and permits removal of unavailable items',()=>{
  service.items.set([{skuId:sku,productId:product,savedAt:null,available:false}]);service.set(product,sku,false);
  http.expectOne('/api/v1/me/wishlist/items/'+sku).flush({detail:'Try again'},{status:503,statusText:'Unavailable'});
  expect(service.isSaved(sku)).toBe(true);expect(service.itemErrors()[sku]).toContain('Try again');expect(service.isPending(sku)).toBe(false);
 });
 it('clears account data and cancels writes when the account changes',()=>{
  service.items.set([{skuId:sku,productId:product,savedAt:null,available:true}]);service.set(product,sku,false);const old=http.expectOne('/api/v1/me/wishlist/items/'+sku);
  session.userId.set('account-b');TestBed.tick();expect(old.cancelled).toBe(true);expect(service.items()).toEqual([]);expect(service.pending().size).toBe(0);http.expectOne('/api/v1/me/wishlist').flush([]);
 });
 it('clears saved data on logout and does not fetch anonymous wishlist',()=>{
  service.items.set([{skuId:sku,productId:product,savedAt:null,available:true}]);session.authenticated.set(false);TestBed.tick();expect(service.items()).toEqual([]);service.set(product,sku,true);expect(session.signIn).toHaveBeenCalled();http.expectNone('/api/v1/me/wishlist');
 });
 it('ignores stale list responses cancelled by a newer refresh',()=>{
  service.load();const old=http.expectOne('/api/v1/me/wishlist');service.load();expect(old.cancelled).toBe(true);http.expectOne('/api/v1/me/wishlist').flush([]);expect(service.loading()).toBe(false);
 });
 it('loads the saved variant and converts current price to minor units',async()=>{
  const result=firstValueFrom(service.details([{skuId:sku,productId:product,savedAt:null,available:true}]));
  http.expectOne('/api/v1/catalogue/products/'+product+'?market=NG').flush({name:'Current name',skus:[{id:'other',code:'WRONG'},{id:sku,code:'RIGHT',available:true}],categories:[{name:'Audio'}],assets:[]});
  http.expectOne('/api/v1/catalogue/skus/'+sku+'/price?market=NG&quantity=1').flush({unitAmount:'1739.00'});
  const [row]=await result;expect(row.code).toBe('RIGHT');expect(row.priceMinorUnits).toBe(173900);expect(row.inStock).toBe(true);
 });
 it('keeps unavailable saved entries removable without loading private metadata',async()=>{
  const [row]=await firstValueFrom(service.details([{skuId:sku,productId:product,savedAt:null,available:false}]));expect(row.available).toBe(false);expect(row.priceMinorUnits).toBeNull();http.expectNone('/api/v1/catalogue/products/'+product+'?market=NG');
 });
 it('does not invent a zero price after pricing failure',async()=>{
  const result=firstValueFrom(service.details([{skuId:sku,productId:product,savedAt:null,available:true}]));http.expectOne('/api/v1/catalogue/products/'+product+'?market=NG').flush({name:'Product',skus:[{id:sku,available:true}]});http.expectOne('/api/v1/catalogue/skus/'+sku+'/price?market=NG&quantity=1').flush({},{status:404,statusText:'Not Found'});expect((await result)[0].priceMinorUnits).toBeNull();
 });
});
