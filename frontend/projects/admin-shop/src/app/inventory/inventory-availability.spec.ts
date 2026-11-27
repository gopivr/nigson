import '@angular/compiler';
import {Injector,runInInjectionContext,signal} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {Subject} from 'rxjs';
import {describe,it,expect,vi} from 'vitest';
import {AdminService} from 'shared-ui';
import {ProductListComponent} from './product-list.component';
import {InventoryDashboardComponent} from './inventory-dashboard.component';
import {StockReportComponent} from './stock-report.component';
const rows=[{name:'Committed',category:'Audio',stock:14,available:0},{name:'Low',category:'Audio',stock:100,available:5},{name:'Ready',category:'Audio',stock:30,available:20}];
describe('Inventory availability indicators',()=>{
 it('classifies allocated stock as unavailable and totals only available quantities',()=>{
  const injector=Injector.create({providers:[{provide:AdminService,useValue:{products:signal(rows)}}]});const c=runInInjectionContext(injector,()=>new InventoryDashboardComponent());expect(c.outOfStock()).toBe(1);expect(c.lowStock().map(p=>p.name)).toEqual(['Low']);expect(c.inStock()).toBe(1);expect(c.categories()[0].stock).toBe(25);
 });
 it('updates low/out-of-stock results when switching report routes',()=>{
  const data=new Subject<any>();const injector=Injector.create({providers:[{provide:AdminService,useValue:{products:signal(rows)}},{provide:ActivatedRoute,useValue:{data}}]});const c=runInInjectionContext(injector,()=>new StockReportComponent());expect(c.filteredProducts().map(p=>p.name)).toEqual(['Low']);data.next({type:'out-of-stock'});expect(c.filteredProducts().map(p=>p.name)).toEqual(['Committed']);data.complete();
 });
 it('refreshes on page entry and prevents repeat refreshes while loading',()=>{
  const response=new Subject<any>();const c:any=Object.create(ProductListComponent.prototype);c.refreshing=signal(false);c.admin={loadStaffProducts:vi.fn(()=>response)};c.ngOnInit();c.refresh();expect(c.admin.loadStaffProducts).toHaveBeenCalledOnce();expect(c.refreshing()).toBe(true);response.complete();expect(c.refreshing()).toBe(false);c.refresh();expect(c.admin.loadStaffProducts).toHaveBeenCalledTimes(2);
 });
 it('releases the refresh control after an error',()=>{
  const response=new Subject<any>();const c:any=Object.create(ProductListComponent.prototype);c.refreshing=signal(false);c.admin={loadStaffProducts:()=>response};c.refresh();response.error(new Error('unavailable'));expect(c.refreshing()).toBe(false);
 });
});
