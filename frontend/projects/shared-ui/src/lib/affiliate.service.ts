import {inject,Injectable,signal} from '@angular/core';
import {HttpClient,HttpHeaders} from '@angular/common/http';
import {of,tap,catchError,throwError,finalize} from 'rxjs';
import {SessionService} from './session.service';
export interface AffiliateLink {code:string;productId:string;path:string;}
export interface AffiliateEarnings {currency:string;pending:string;available:string;held:string;reversed:string;items:{orderId:string;orderNumber:string;earned:string;reversed:string;net:string;state:string;availableAt:string|null}[];page:number;size:number;totalElements:number;totalPages:number;}
export function affiliateUrl(path:string,origin=window.location.origin):string {
 if(!/^\/api\/v1\/affiliate-links\/[a-f0-9]{32}$/.test(path))throw new Error('Invalid referral link.');
 const url=new URL(path,origin);
 // Match the existing local BFF login hostname so host-only cookies survive login.
 if(url.protocol==='http:'&&url.hostname==='127.0.0.1'&&url.port==='4200')url.hostname='localhost';
 return url.toString();
}
@Injectable({providedIn:'root'})
export class AffiliateService {
 private http=inject(HttpClient);private session=inject(SessionService);private generation=0;
 readonly state=signal('NONE');readonly loading=signal(false);readonly error=signal('');
 load(){const generation=++this.generation;this.error.set('');this.state.set('NONE');if(!this.session.authenticated()){this.loading.set(false);return of({state:'NONE'});}this.loading.set(true);return this.http.get<{state:string}>('/api/v1/me/affiliate').pipe(tap(result=>{if(generation===this.generation)this.state.set(result.state);}),catchError(error=>{if(generation===this.generation)this.error.set('Unable to confirm affiliate access. Please refresh.');return throwError(()=>error);}),finalize(()=>{if(generation===this.generation)this.loading.set(false);}));}
 link(productId:string){return this.http.put<AffiliateLink>('/api/v1/me/affiliate/links/'+encodeURIComponent(productId),null,{headers:new HttpHeaders({'Idempotency-Key':crypto.randomUUID()})});}
 earnings(page=0){return this.http.get<AffiliateEarnings>('/api/v1/me/affiliate/earnings',{params:{page:String(page)}});}
}
