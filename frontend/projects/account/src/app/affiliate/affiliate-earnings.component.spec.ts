import '@angular/compiler';
import {Injector} from '@angular/core';
import {of,Subject,throwError} from 'rxjs';
import {describe,it,expect,vi} from 'vitest';
import {AffiliateService} from 'shared-ui';
import {AffiliateEarningsComponent} from './affiliate-earnings.component';
const result={currency:'NGN',pending:'45.00',available:'22.50',held:'0.00',reversed:'22.50',items:[],page:0,size:20,totalElements:0,totalPages:0};
function fixture(earnings:any){return Injector.create({providers:[{provide:AffiliateService,useValue:{earnings}},{provide:AffiliateEarningsComponent,useFactory:()=>new AffiliateEarningsComponent()}]}).get(AffiliateEarningsComponent);}
describe('Affiliate earnings',()=>{
 it('uses server amounts and requests the selected page',()=>{const get=vi.fn(()=>of({...result,page:2}));const c=fixture(get);c.load(2);expect(get).toHaveBeenCalledWith(2);expect(c.result()?.available).toBe('22.50');expect(c.result()?.reversed).toBe('22.50');expect(c.loading()).toBe(false);});
 it('clears old balances when refresh fails',()=>{const get=vi.fn().mockReturnValueOnce(of(result)).mockReturnValueOnce(throwError(()=>new Error('offline')));const c=fixture(get);c.ngOnInit();expect(c.result()).not.toBeNull();c.load(0);expect(c.result()).toBeNull();expect(c.error()).toContain('could not be loaded');expect(c.loading()).toBe(false);});
 it('does not overlap refresh requests',()=>{const pending=new Subject<any>();const get=vi.fn(()=>pending);const c=fixture(get);c.load(0);c.load(1);expect(get).toHaveBeenCalledTimes(1);pending.next(result);pending.complete();expect(c.loading()).toBe(false);});
});
