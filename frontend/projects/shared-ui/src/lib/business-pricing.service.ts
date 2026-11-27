import {Injectable,inject} from '@angular/core';
import {HttpClient,HttpHeaders} from '@angular/common/http';
import {firstValueFrom} from 'rxjs';
export interface BusinessEntry{skuId:string;minimumQuantity:string;unitAmount:string;}
export interface BusinessBook{id:string;code:string;segment:string;active:boolean;version:number;entries:BusinessEntry[];effectiveFrom:string;effectiveUntil?:string|null;}
@Injectable({providedIn:'root'}) export class BusinessPricingService{
 private http=inject(HttpClient);
 headers(key:string,version?:number){let h=new HttpHeaders({'Idempotency-Key':key});return version==null?h:h.set('If-Match','"'+version+'"');}
 books(segment:string){return firstValueFrom(this.http.get<{items:BusinessBook[]}>('/api/v1/staff/price-books?segment='+segment+'&currency=NGN&page=0&size=100&sort=effectiveFrom,desc'));}
 create(body:unknown,key:string){return firstValueFrom(this.http.post<BusinessBook>('/api/v1/staff/price-books',body,{headers:this.headers(key)}));}
 publish(book:BusinessBook,key:string){return firstValueFrom(this.http.post<BusinessBook>('/api/v1/staff/price-books/'+book.id+'/activation',{reason:'Explicit Admin business pricing publication'},{headers:this.headers(key,book.version)}));}
 async assignment(org:string){const markets=await firstValueFrom(this.http.get<any[]>('/api/v1/markets'));const market=markets.find(m=>m.code==='NG'&&m.currency==='NGN');if(!market)throw Error('NG/NGN market is not available.');return firstValueFrom(this.http.get<any>('/api/v1/staff/organizations/'+org+'/price-assignment?marketId='+market.id));}
 assign(current:any,bookId:string,key:string){const minimumRules=current.minimumRules.map((r:any)=>Object.fromEntries(Object.entries(r).filter(([,v])=>v!==null)));return firstValueFrom(this.http.put<any>('/api/v1/staff/organizations/'+current.organizationId+'/price-assignment',{marketId:current.marketId,priceBookId:bookId,minimumRules},{headers:this.headers(key,current.version)}));}
}
