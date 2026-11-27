import {Component,inject,OnInit,signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {HttpClient,HttpParams} from '@angular/common/http';
import {AffiliateService,affiliateUrl} from 'shared-ui';
import {finalize} from 'rxjs';
import {AffiliateEarningsComponent} from './affiliate-earnings.component';
import {AffiliatePayoutComponent} from './affiliate-payout.component';
interface Product {id:string;name:string;imageUrl?:string;}
@Component({selector:'app-affiliate',standalone:true,imports:[CommonModule,FormsModule,RouterLink,AffiliateEarningsComponent,AffiliatePayoutComponent],template:`
<div class="space-y-6 text-ink">
  <header class="flex flex-wrap items-center justify-between gap-5 rounded-2xl border border-hairline bg-white p-6 md:p-8">
   <div><p class="text-xs font-semibold uppercase tracking-widest text-brand">Partner programme</p><h2 class="mt-2 text-2xl font-extrabold tracking-tight md:text-3xl">Your affiliate account</h2><p class="mt-2 text-sm text-muted-ink">Share products. Earn 5% on qualifying retail purchases.</p></div>
   <button *ngIf="affiliate.state()==='ACTIVE'" type="button" (click)="productsSection.scrollIntoView({behavior:'smooth',block:'start'})" class="rounded-full bg-brand px-6 py-3 text-sm font-semibold text-white transition hover:bg-brand-deep focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand">Share products &rarr;</button>
  </header>
  <app-affiliate-payout *ngIf="affiliate.state()==='ACTIVE' || affiliate.state()==='SUSPENDED'"></app-affiliate-payout>
  <app-affiliate-earnings *ngIf="affiliate.state()==='ACTIVE' || affiliate.state()==='SUSPENDED'"></app-affiliate-earnings>
  <section #productsSection class="scroll-mt-32 rounded-2xl border border-hairline bg-white p-6 md:p-8">
   <p class="text-xs font-semibold uppercase tracking-widest text-brand">Your referral links</p><h3 class="mt-2 text-2xl font-extrabold">Share products</h3><p class="mt-2 text-sm text-muted-ink">Choose a product and copy your link. Referrals stay valid in the same browser for seven days, including through sign-in.</p>
   <p *ngIf="affiliate.loading()" role="status" class="mt-5 text-sm text-muted-ink">Checking affiliate access...</p>
   <p *ngIf="affiliate.error()" role="alert" class="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">{{affiliate.error()}}</p>
   <p *ngIf="!affiliate.loading() && affiliate.state()!=='ACTIVE'" class="mt-5 rounded-xl bg-surface-alt p-4 text-sm">{{affiliate.state()==='SUSPENDED'?'Sharing is paused while your affiliate access is suspended.':'Affiliate approval is required to create links.'}} <a routerLink="/affiliate" class="font-semibold underline">View application</a></p>
   <ng-container *ngIf="affiliate.state()==='ACTIVE'">
    <form (ngSubmit)="search(0)" class="mt-6 flex flex-wrap gap-3"><input type="search" [(ngModel)]="query" name="search" aria-label="Search products" placeholder="Search products..." class="min-w-0 flex-1 rounded-full border border-hairline bg-surface px-5 py-3 text-sm focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/10"><button [disabled]="loading()" class="rounded-full bg-brand px-6 py-3 text-sm font-semibold text-white hover:bg-brand-deep disabled:opacity-50">Search</button></form>
    <p *ngIf="error()" role="alert" class="mt-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">{{error()}}</p><p *ngIf="notice()" role="status" class="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">{{notice()}}</p>
    <label *ngIf="lastLink()" class="mt-4 block text-xs font-semibold uppercase tracking-wide text-muted-ink">Your referral link<input [value]="lastLink()" readonly aria-label="Your referral link" class="mt-2 w-full rounded-xl border border-hairline bg-surface px-4 py-3 text-sm font-normal normal-case tracking-normal text-ink" (click)="$any($event.target).select()"></label>
    <p *ngIf="loading()" role="status" class="mt-6 text-sm text-muted-ink">Loading products...</p>
    <div class="mt-6 grid gap-4 xl:grid-cols-2"><article *ngFor="let product of products()" class="flex flex-col justify-between gap-5 rounded-2xl border border-hairline p-5"><div class="flex items-center gap-4"><div class="flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-surface-alt"><img *ngIf="product.imageUrl" [src]="product.imageUrl" [alt]="product.name" class="h-full w-full object-contain"><span *ngIf="!product.imageUrl" class="text-xs text-muted-ink">Product</span></div><a [routerLink]="['/products',product.id]" class="min-w-0 break-words font-semibold hover:text-brand">{{product.name}}</a></div><div class="flex flex-wrap items-center justify-between gap-3"><a [routerLink]="['/products',product.id]" class="text-sm font-semibold text-muted-ink hover:text-brand">View product</a><button type="button" (click)="copy(product)" [disabled]="!!copying()" [attr.aria-label]="'Copy referral link for '+product.name" class="rounded-full bg-brand px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-deep disabled:opacity-50">{{copying()===product.id?'Preparing...':'Copy link'}}</button></div></article></div>
    <div *ngIf="!loading() && !products().length" class="rounded-2xl bg-surface p-10 text-center"><h4 class="font-semibold">No products found</h4><p class="mt-2 text-sm text-muted-ink">Try a different product name.</p></div>
    <nav *ngIf="pages()>1" aria-label="Product pages" class="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-hairline pt-5 text-sm"><button type="button" (click)="search(page()-1)" [disabled]="loading() || page()===0" class="rounded-full border border-hairline px-4 py-2 font-semibold disabled:opacity-40">Previous</button><span class="text-muted-ink">Page {{page()+1}} of {{pages()}}</span><button type="button" (click)="search(page()+1)" [disabled]="loading() || page()+1>=pages()" class="rounded-full border border-hairline px-4 py-2 font-semibold disabled:opacity-40">Next</button></nav>
   </ng-container>
  </section>
 </div>
`})
export class AffiliateComponent implements OnInit {
 readonly affiliate=inject(AffiliateService);private http=inject(HttpClient);
 readonly products=signal<Product[]>([]);readonly loading=signal(false);readonly error=signal('');readonly notice=signal('');readonly copying=signal('');readonly lastLink=signal('');readonly page=signal(0);readonly pages=signal(0);query='';
 ngOnInit(){this.affiliate.load().subscribe({next:r=>{if(r.state==='ACTIVE')this.search(0);},error:()=>{}});}
 search(page:number){if(this.affiliate.state()!=='ACTIVE'||this.loading()||page<0)return;this.loading.set(true);this.error.set('');this.http.get<{items:Product[];totalPages:number}>('/api/v1/catalogue/products',{params:new HttpParams().set('market','NG').set('page',String(page)).set('size','20').appendAll(this.query.trim()?{q:this.query.trim()}:{})}).pipe(finalize(()=>this.loading.set(false))).subscribe({next:r=>{this.products.set(r.items);this.page.set(page);this.pages.set(r.totalPages);},error:()=>this.error.set('Products could not be loaded. Please try again.')});}
 copy(product:Product){if(this.copying())return;this.copying.set(product.id);this.error.set('');this.notice.set('');this.affiliate.link(product.id).pipe(finalize(()=>this.copying.set(''))).subscribe({next:async link=>{try{const url=affiliateUrl(link.path);this.lastLink.set(url);try{await navigator.clipboard.writeText(url);this.notice.set('Referral link copied.');}catch{this.notice.set('Select and copy your referral link above.');}}catch{this.error.set('The referral link could not be confirmed.');}},error:()=>this.error.set('A link could not be created. Refresh your affiliate access and try again.')});}
}
