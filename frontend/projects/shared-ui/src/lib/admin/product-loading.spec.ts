import '@angular/compiler';
import {signal} from '@angular/core';
import {of,Subject} from 'rxjs';
import {describe,it,expect,vi} from 'vitest';
import {AdminService} from './admin.service';
import {LOCAL_API_FAILURE_CODES} from '../api-errors.service';

function setup() {
 const service=Object.create(AdminService.prototype) as AdminService;
 const catalogue=new Subject<any>();
 const http={get:vi.fn((url:string)=>url.startsWith('/api/v1/staff/products')?catalogue:url.startsWith('/api/v1/staff/inventory')?of({items:[],totalPages:1}):of({unitAmount:'100'}))};
 Object.assign(service,{session:{hasRole:()=>true},http,products:signal([]),errors:signal({})});
 return {service,http,catalogue};
}
describe('Admin product loading',()=>{
 it('shares concurrent loads, skips unpublished prices, and refreshes after completion',()=>{
  const {service,http,catalogue}=setup();
  service.loadStaffProducts().subscribe();
  service.loadStaffProducts().subscribe();
  expect(http.get.mock.calls.filter(([url])=>url.startsWith('/api/v1/staff/products'))).toHaveLength(1);
  catalogue.next({items:[{id:'draft',status:'DRAFT',skus:[{id:'draft-sku'}]},{id:'active',status:'ACTIVE',skus:[{id:'active-sku',packSize:'1'}]}],totalPages:1});
  catalogue.complete();
  expect(http.get.mock.calls.filter(([url])=>url.includes('/price'))).toHaveLength(1);
  expect(service.products().find(p=>p.id==='draft')?.priceSubtitle).toContain('after publication');
  expect(service.products().find(p=>p.id==='active')?.price).toBe(100);
  service.loadStaffProducts().subscribe();
  expect(http.get.mock.calls.filter(([url])=>url.startsWith('/api/v1/staff/products'))).toHaveLength(2);
 });
});

describe('Owner-scoped staff inventory reads',()=>{
 it('loads Operations inventory without requesting Content Editor data',()=>{
  const {service,http}=setup();Object.assign(service,{session:{hasRole:(role:string)=>role==='OPERATIONS'}});
  http.get.mockImplementation((url:string)=>{
   expect(url).toContain('/api/v1/staff/inventory');
   return of({items:[{skuId:'actual-sku',onHand:'8',reserved:'2',allocated:'1',available:'5'}],totalPages:1});
  });
  service.loadStaffProducts().subscribe();expect(service.products()[0]).toMatchObject({skuId:'actual-sku',stock:8,reserved:2,allocated:1,available:5,status:'UNKNOWN'});
  expect(http.get).toHaveBeenCalledTimes(1);
 });
 it('loads Content Editor products without requesting Operations stock',()=>{
  const {service,http,catalogue}=setup();Object.assign(service,{session:{hasRole:(role:string)=>role==='CONTENT_EDITOR'}});
  service.loadStaffProducts().subscribe();catalogue.next({items:[{id:'draft',status:'DRAFT',skus:[{id:'sku'}]}],totalPages:1});catalogue.complete();
  expect(http.get.mock.calls.some(([url])=>url.includes('/staff/inventory'))).toBe(false);expect(service.products()).toHaveLength(1);
 });
});
