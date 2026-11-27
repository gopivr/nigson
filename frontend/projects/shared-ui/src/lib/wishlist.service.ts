import { Injectable, inject, signal, effect, untracked, DestroyRef } from '@angular/core';
import { HttpClient, HttpContext, HttpHeaders } from '@angular/common/http';
import { Observable, Subscription, of, from } from 'rxjs';
import { catchError, map, mergeMap, toArray } from 'rxjs/operators';
import { SessionService } from './session.service';
import { CatalogService } from './catalog.service';
import { LOCAL_API_FAILURE_CODES, apiFailureDetail } from './api-errors.service';

export interface WishlistItem { skuId: string; productId: string; savedAt: string | null; available: boolean; }
export interface WishlistProduct extends WishlistItem { name: string; code: string; category: string; image: string; priceMinorUnits: number | null; inStock: boolean; detailError: string | null; }
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
@Injectable({ providedIn: 'root' })
export class WishlistService {
 private http = inject(HttpClient);
 private session = inject(SessionService);
 private catalog = inject(CatalogService);
 readonly items = signal<WishlistItem[]>([]);
 readonly loading = signal(false);
 readonly error = signal<string | null>(null);
 readonly pending = signal<ReadonlySet<string>>(new Set());
 readonly itemErrors = signal<Record<string,string>>({});
 private account: string | null = null;
 private generation = 0;
 private read = new Subscription();
 private writes = new Subscription();
 constructor() {
  effect(() => {
   const account = this.session.authenticated() ? this.session.userId() : null;
   untracked(() => {
    if (account === this.account) return;
    this.account = account; this.generation++;
    this.read.unsubscribe(); this.writes.unsubscribe(); this.writes = new Subscription();
    this.items.set([]); this.pending.set(new Set()); this.itemErrors.set({}); this.error.set(null); this.loading.set(false);
    if (account) this.load();
   });
  });
  inject(DestroyRef).onDestroy(() => { this.read.unsubscribe(); this.writes.unsubscribe(); });
 }
 private options() { return { withCredentials: true, context: new HttpContext().set(LOCAL_API_FAILURE_CODES, ['0:undefined','400:VALIDATION_ERROR','429:RATE_LIMITED','422:PRICING_RULE_VIOLATION','422:CATALOGUE_RULE_VIOLATION','502:undefined','504:undefined','401:AUTH_REQUIRED','403:ACCESS_DENIED','404:NOT_FOUND','409:IDEMPOTENCY_IN_PROGRESS','409:IDEMPOTENCY_HASH_MISMATCH','422:BUSINESS_RULE_VIOLATION','422:MARKET_RULE_VIOLATION','422:VALIDATION_ERROR','500:INTERNAL_ERROR','503:PROVIDER_UNAVAILABLE']) }; }
 isSaved(sku: string | undefined): boolean { return !!sku && this.items().some(item => item.skuId === sku); }
 isPending(sku: string | undefined): boolean { return !!sku && this.pending().has(sku); }
 load(): void {
  if (!this.session.authenticated() || !this.session.userId()) return;
  this.read.unsubscribe(); const generation = ++this.generation; const account = this.session.userId();
  this.loading.set(true); this.error.set(null);
  this.read = this.http.get<WishlistItem[]>('/api/v1/me/wishlist', this.options()).subscribe({
   next: items => { if(generation===this.generation && account===this.session.userId()) { this.items.set(items); this.loading.set(false); } },
   error: err => { if(generation===this.generation && account===this.session.userId()) { this.loading.set(false); this.error.set(apiFailureDetail(err,'Unable to load wishlist.')); } }
  });
 }
 toggle(productId: string, skuId: string): void { this.set(productId,skuId,!this.isSaved(skuId)); }
 set(productId: string, skuId: string, saved: boolean): void {
  if (!this.session.authenticated()) { this.session.signIn(window.location.pathname); return; }
  if (!UUID.test(skuId) || !UUID.test(productId) || this.isPending(skuId) || this.loading()) return;
  const account=this.session.userId();
  this.read.unsubscribe(); ++this.generation;
  this.pending.update(items=>new Set([...items,skuId]));
  this.itemErrors.update(errors=>{const next={...errors};delete next[skuId];return next;});
  const options={...this.options(),headers:new HttpHeaders({'Idempotency-Key':crypto.randomUUID()})};
  const url='/api/v1/me/wishlist/items/'+encodeURIComponent(skuId);
  const request=saved?this.http.put<void>(url,null,options):this.http.delete<void>(url,options);
  this.writes.add(request.subscribe({
   next:()=>{
    if(account!==this.session.userId() || !this.session.authenticated())return;
    this.items.update(items=>saved?(items.some(i=>i.skuId===skuId)?items:[{productId,skuId,savedAt:null,available:true},...items]):items.filter(i=>i.skuId!==skuId));
    this.pending.update(items=>new Set([...items].filter(id=>id!==skuId)));
    if(!this.pending().size)this.load();
   },
   error:err=>{
    if(account!==this.session.userId() || !this.session.authenticated())return;
    this.pending.update(items=>new Set([...items].filter(id=>id!==skuId)));
    const message=err?.error?.code==='BUSINESS_RULE_VIOLATION'?'You can save up to 100 items. Remove an item before saving another.':apiFailureDetail(err,'Unable to update wishlist. Refresh before retrying if the outcome is uncertain.');
    this.itemErrors.update(errors=>({...errors,[skuId]:message}));
   }
  }));
 }
 details(items: WishlistItem[]): Observable<WishlistProduct[]> {
  // Keep catalogue traffic bounded, with at most four items being resolved at once.
  return from(items.map((item,index)=>({item,index}))).pipe(mergeMap(({item,index})=>this.detail(item).pipe(map(value=>({value,index}))),4),toArray(),map(rows=>rows.sort((a,b)=>a.index-b.index).map(row=>row.value)));
 }
 private detail(item: WishlistItem): Observable<WishlistProduct> {
  const unavailable:WishlistProduct={...item,name:'Unavailable product',code:'',category:'',image:'',priceMinorUnits:null,inStock:false,detailError:null};
  if(!item.available)return of(unavailable);
  return this.http.get<any>('/api/v1/catalogue/products/'+item.productId+'?market=NG',this.options()).pipe(
   mergeMap(product=>{
    const sku=product.skus?.find((s:any)=>s.id===item.skuId);
    if(!sku)return of({...unavailable,available:false});
    const view:WishlistProduct={...item,name:product.name,code:sku.code,category:product.categories?.[0]?.name||'',image:this.catalog.resolveImageUrl(product.assets?.[0]?.url),priceMinorUnits:null,inStock:sku.available===true,detailError:null};
    return this.http.get<any>('/api/v1/catalogue/skus/'+item.skuId+'/price?market=NG&quantity=1',this.options()).pipe(
     map(price=>{const amount=Math.round(Number(price.unitAmount)*100);return {...view,priceMinorUnits:Number.isSafeInteger(amount)&&amount>=0?amount:null,detailError:Number.isSafeInteger(amount)&&amount>=0?null:'Current price is unavailable. Refresh to retry.'};}),
     catchError(()=>of({...view,detailError:'Current price is unavailable. Refresh to retry.'}))
    );
   }),
   catchError(err=>of({...unavailable,available:err.status===404?false:item.available,detailError:err.status===404?null:'Product details could not be loaded. Refresh to retry.'}))
  );
 }
}
