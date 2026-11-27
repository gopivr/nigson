import {Injectable,inject} from '@angular/core';
import {HttpClient,HttpHeaders} from '@angular/common/http';
import {firstValueFrom} from 'rxjs';

export interface RetailEntry { skuId:string; minimumQuantity:string; unitAmount:string; }
export interface RetailBook { id:string; code:string; currency:string; segment:string; active:boolean; effectiveUntil?:string|null; entries:RetailEntry[]; }
export interface RetailBinding { marketId:string; code:string; priceBookId:string; version:number; }
export interface RetailPriceContext { binding:RetailBinding; book:RetailBook; entry?:RetailEntry; }

export function canEditRetailPrice(roles:readonly string[]):boolean {
 const current=roles.map(role=>role.toUpperCase());
 return current.includes('ADMIN') || (current.includes('SALES') && current.includes('FINANCE'));
}

export function retailAmount(value:unknown):string {
 const text=String(value??'').trim();
 if(!/^(0|[1-9][0-9]{0,14})(\.[0-9]{1,2})?$/.test(text))throw new Error('Enter a retail price in naira with at most two decimal places.');
 const [whole,fraction='']=text.split('.');
 return whole+'.'+fraction.padEnd(2,'0');
}

@Injectable({providedIn:'root'})
export class RetailPricingService {
 private http=inject(HttpClient);
 private headers(version?:number) {
  let headers=new HttpHeaders({'Idempotency-Key':crypto.randomUUID()});
  if(version!==undefined)headers=headers.set('If-Match','"'+version+'"');
  return headers;
 }
 async load(skuId:string):Promise<RetailPriceContext> {
  let binding:RetailBinding|undefined;
  for(let page=0;page<100;page++) {
   const result=await firstValueFrom(this.http.get<any>('/api/v1/staff/price-books',{params:{segment:'RETAIL',currency:'NGN',page:String(page),size:'100',sort:'code,asc'}}));
   const current=result.retailBindings?.find((row:RetailBinding)=>row.code==='NG');
   if(!current?.priceBookId||!Number.isSafeInteger(current.version))throw new Error('The backend must provide the configured NG retail price-book binding before prices can be edited.');
   if(binding&&(binding.version!==current.version||binding.priceBookId!==current.priceBookId))throw new Error('Retail pricing changed while loading. Reload the product before editing its price.');
   binding=current;
   const book:RetailBook|undefined=result.items?.find((row:RetailBook)=>row.id===binding!.priceBookId);
   if(book) {
    if(!book.active||book.currency!=='NGN'||book.segment!=='RETAIL'||!Array.isArray(book.entries)||book.entries.length>100)throw new Error('The configured retail price book is not available for editing.');
    const entry=book.entries.filter(row=>row.skuId===skuId).sort((a,b)=>Number(a.minimumQuantity)-Number(b.minimumQuantity))[0];
    return {binding:current,book,entry};
   }
   if(page+1>=result.totalPages)break;
  }
  throw new Error('The configured retail price book could not be found. No selling price was changed.');
 }
 async save(context:RetailPriceContext,skuId:string,amount:unknown,minimumQuantity:string):Promise<void> {
  const unitAmount=retailAmount(amount);
  // Each replacement preserves the published book and every other SKU/tier.
  const entries=context.book.entries.map(entry=>({...entry}));
  if(context.entry)entries.find(entry=>entry.skuId===skuId&&entry.minimumQuantity===context.entry!.minimumQuantity)!.unitAmount=unitAmount;
  else entries.push({skuId,minimumQuantity,unitAmount});
  if(entries.length>100)throw new Error('This price book has reached its entry limit. No selling price was changed.');
  const effectiveFrom=new Date().toISOString();
  if(context.book.effectiveUntil&&Date.parse(context.book.effectiveUntil)<=Date.now())throw new Error('The retail price book has expired. Reload pricing before saving.');
  const draft=await firstValueFrom(this.http.post<any>('/api/v1/staff/price-books',{
   code:'NG-RETAIL-'+crypto.randomUUID(),currency:'NGN',segment:'RETAIL',effectiveFrom,
   ...(context.book.effectiveUntil?{effectiveUntil:context.book.effectiveUntil}:{}),entries
  },{headers:this.headers()}));
  try {
   await firstValueFrom(this.http.post('/api/v1/staff/price-books/'+draft.id+'/activation',{reason:'Product editor retail price update'},{headers:this.headers(draft.version)}));
   await firstValueFrom(this.http.put('/api/v1/staff/markets/'+context.binding.marketId+'/retail-price-book',{priceBookId:draft.id,reason:'Product editor retail price update'},{headers:this.headers(context.binding.version)}));
  } catch(error:any) {
   const detail=error?.error?.detail||error?.message||'Pricing request failed.';
   throw new Error('Selling price was not confirmed. Replacement price book '+draft.id+' was created; reload pricing before retrying. '+detail);
  }
 }
}
