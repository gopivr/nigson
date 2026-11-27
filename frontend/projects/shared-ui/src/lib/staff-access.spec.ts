import '@angular/compiler';
import {Injector,runInInjectionContext} from '@angular/core';
import {Router} from '@angular/router';
import {of,isObservable,firstValueFrom,tap} from 'rxjs';
import {describe,it,expect,vi,beforeEach} from 'vitest';
import {SessionService} from './session.service';
import {staffGuard,staffSectionGuard,adminGuard} from './auth.guard';
import {canAccessSection,staffLanding,staffPathSection,safeReturnPath,loginDestination} from './staff-access';

async function guard(roles:string[],url:string,options:{authenticated?:boolean;loaded?:boolean;adminOnly?:boolean;entry?:boolean}={}) {
 let ready=options.loaded??true;
 const session={authenticated:()=>options.authenticated??true,sessionLoaded:()=>ready,roles:()=>roles,isAdmin:()=>roles.includes('ADMIN'),isStaff:()=>roles.some(role=>['ADMIN','SALES','OPERATIONS','FINANCE','CONTENT_EDITOR','CONTENT_PUBLISHER','ANALYST'].includes(role)),loadSession:vi.fn(()=>of({}).pipe(tap(()=>ready=true)))};
 const router={parseUrl:(url:string)=>url};
 const injector=Injector.create({providers:[{provide:SessionService,useValue:session},{provide:Router,useValue:router}]});
 const fn=options.adminOnly?adminGuard:options.entry?staffGuard:staffSectionGuard;
 const result=runInInjectionContext(injector,()=>fn({} as any,{url} as any));
 return {result:isObservable(result)?await firstValueFrom(result):await result,session};
}
describe('Staff route access',()=>{
 it('allows category management only with Content Editor access',async()=>{expect((await guard(['CONTENT_EDITOR'],'/admin/categories')).result).toBe(true);for(const role of ['ADMIN','SALES','OPERATIONS','FINANCE','CONTENT_PUBLISHER','ANALYST','CUSTOMER'])expect((await guard([role],'/admin/categories')).result).not.toBe(true);});
 it('allows affiliate payout records only to Admin and Finance',async()=>{for(const role of ['ADMIN','FINANCE'])expect((await guard([role],'/admin/affiliate-payouts')).result).toBe(true);for(const role of ['SALES','OPERATIONS','CONTENT_EDITOR','CONTENT_PUBLISHER','ANALYST','CUSTOMER'])expect((await guard([role],'/admin/affiliate-payouts')).result).not.toBe(true);});
 it('admits every recognized staff role through the portal entrance',async()=>{
  for(const role of ['ADMIN','SALES','OPERATIONS','FINANCE','CONTENT_EDITOR','CONTENT_PUBLISHER','ANALYST'])expect((await guard([role],'/admin',{entry:true})).result).toBe(true);
 });
 it('allows the corresponding owner-authorized pages',async()=>{
  for(const [role,path] of [['ADMIN','/admin/users'],['SALES','/admin/quotes'],['OPERATIONS','/admin/inventory/stock-in'],['FINANCE','/admin/orders'],['CONTENT_EDITOR','/admin/pages/home'],['CONTENT_PUBLISHER','/admin/reviews'],['ANALYST','/admin/analytics']])expect((await guard([role],path)).result).toBe(true);
  expect((await guard(['SALES'],'/admin/orders')).result).toBe(true);
 });
 it('protects user and role administration from every non-admin staff role',async()=>{
  for(const role of ['SALES','OPERATIONS','FINANCE','CONTENT_EDITOR','CONTENT_PUBLISHER','ANALYST'])for(const path of ['/admin/users','/admin/roles']){expect((await guard([role],path)).result).not.toBe(true);expect((await guard([role],path,{adminOnly:true})).result).toBe('/account');}
 });
 it('denies direct URLs that the owner APIs do not authorize',async()=>{
  for(const [role,path] of [['OPERATIONS','/admin/inventory/products/new'],['FINANCE','/admin/analytics'],['CONTENT_PUBLISHER','/admin/pages'],['CONTENT_EDITOR','/admin/inventory'],['ADMIN','/admin/inventory']])expect((await guard([role],path)).result).not.toBe(true);
 });
 it('combines grants for multi-role users',async()=>{
  const roles=['SALES','OPERATIONS'];expect((await guard(roles,'/admin/customers')).result).toBe(true);expect((await guard(roles,'/admin/inventory')).result).toBe(true);expect((await guard(roles,'/admin/pages')).result).not.toBe(true);
 });
 it('redirects customers and anonymous callers and resolves delayed sessions',async()=>{
  expect((await guard(['CUSTOMER'],'/admin/orders')).result).toBe('/account');
  expect((await guard([],'/admin/inventory',{authenticated:false})).result).toContain('/auth/login?redirect=');
  const delayed=await guard(['OPERATIONS'],'/admin/inventory',{loaded:false});expect(delayed.result).toBe(true);expect(delayed.session.loadSession).toHaveBeenCalledTimes(1);
 });
 it('checks child navigation again as permissions change',async()=>{
  expect((await guard(['ADMIN'],'/admin/users')).result).toBe(true);
  expect((await guard(['SALES'],'/admin/users')).result).toBe('/admin/quotes');
 });
});
describe('Staff landing and safe login returns',()=>{
 beforeEach(()=>sessionStorage.clear());
 it('chooses role-specific destinations and preserves allowed deep links',()=>{
  expect(staffLanding(['OPERATIONS'])).toBe('/admin/inventory');expect(staffLanding(['CONTENT_PUBLISHER'])).toBe('/admin/reviews');expect(staffLanding(['CUSTOMER'])).toBe('/account');
  expect(loginDestination(['CONTENT_EDITOR'],'/admin/inventory/products/sku/edit')).toBe('/admin/inventory/products/sku/edit');expect(loginDestination(['SALES'],'/admin/users')).toBe('/admin/quotes');expect(loginDestination(['OPERATIONS'],'/checkout')).toBe('/checkout');
 });
 it('rejects unsafe and unknown returns',()=>{
  for(const url of ['https://evil.test','//evil.test','/account/../admin/users','/account/%2e%2e/admin/users','/account\\evil','/admin/not-a-page'])expect(safeReturnPath(url)).toBeUndefined();
  expect(staffPathSection('/admin/inventory/products/sku/edit?tab=price')).toBe('products');expect(staffPathSection('/admin/inventory/movements')).toBe('inventory');
 });
 it('consumes a pending return once and uses current server roles',()=>{
  const session=Object.create(SessionService.prototype) as SessionService;Object.assign(session,{roles:()=>['SALES']});
  sessionStorage.setItem('nigson_login_return','/admin/users');expect(session.consumeLoginReturn()).toBe('/admin/quotes');expect(session.consumeLoginReturn()).toBeNull();
  sessionStorage.setItem('nigson_login_return','DEFAULT');expect(session.consumeLoginReturn()).toBe('/admin/quotes');
 });
});
