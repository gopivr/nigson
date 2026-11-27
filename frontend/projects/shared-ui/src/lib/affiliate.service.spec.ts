import '@angular/compiler';
import {Injector,signal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of,firstValueFrom,Subject} from 'rxjs';
import {describe,it,expect,vi} from 'vitest';
import {AffiliateService,affiliateUrl} from './affiliate.service';
import {SessionService} from './session.service';
describe('Affiliate product links',()=>{
 it('uses a same-origin product redirect link and matches the local BFF login host',()=>{const path='/api/v1/affiliate-links/'+'a'.repeat(32);expect(affiliateUrl(path,'http://127.0.0.1:4200')).toBe('http://localhost:4200'+path);expect(affiliateUrl(path,'https://shop.example')).toBe('https://shop.example'+path);expect(()=>affiliateUrl('https://attacker.example/path')).toThrow();expect(()=>affiliateUrl('//attacker.example')).toThrow();});
 it('loads actual access and creates links only through the protected API',async()=>{const http={get:vi.fn(()=>of({state:'ACTIVE'})),put:vi.fn(()=>of({code:'a'.repeat(32),productId:'product',path:'/api/v1/affiliate-links/'+'a'.repeat(32)}))};const injector=Injector.create({providers:[{provide:HttpClient,useValue:http},{provide:SessionService,useValue:{authenticated:()=>true}},{provide:AffiliateService,useFactory:()=>new AffiliateService()}]});const service=injector.get(AffiliateService);await firstValueFrom(service.load());expect(service.state()).toBe('ACTIVE');await firstValueFrom(service.link('product'));expect(http.put.mock.calls[0][0]).toBe('/api/v1/me/affiliate/links/product');expect(http.put.mock.calls[0][2].headers.get('Idempotency-Key')).toBeTruthy();});
 it('does not restore old-user access after a newer refresh',()=>{const old=new Subject<{state:string}>();const http={get:vi.fn().mockReturnValueOnce(old).mockReturnValueOnce(of({state:'SUSPENDED'}))};const injector=Injector.create({providers:[{provide:HttpClient,useValue:http},{provide:SessionService,useValue:{authenticated:()=>true}},{provide:AffiliateService,useFactory:()=>new AffiliateService()}]});const service=injector.get(AffiliateService);service.load().subscribe();service.load().subscribe();old.next({state:'ACTIVE'});old.complete();expect(service.state()).toBe('SUSPENDED');});
 it('returns to the product page using the existing login return flow',()=>{sessionStorage.setItem('nigson_login_return','/products/product-id');const session:any=Object.create(SessionService.prototype);session.roles=()=>['CUSTOMER'];expect(session.consumeLoginReturn()).toBe('/products/product-id');expect(sessionStorage.getItem('nigson_login_return')).toBeNull();});
});
