import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { toObservable, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { switchMap, tap } from 'rxjs/operators';
import { WishlistService, WishlistProduct, CatalogService, CartService, ShopService } from 'shared-ui';
@Component({selector:'app-wishlist',standalone:true,imports:[CommonModule,RouterLink],template:`
 <div class="space-y-6">
  <div class="rounded-2xl border border-hairline bg-white p-6 md:p-8">
   <div class="flex items-center justify-between gap-4"><div><p class="text-xs font-semibold uppercase tracking-widest text-brand">Saved</p><h2 class="text-2xl font-extrabold">My Wishlist</h2><p class="mt-1 text-sm text-muted-ink">Saved to your account. Prices and availability are checked when you open this page.</p></div><span>{{wishlist.items().length}} items</span></div>
   <button type="button" (click)="refresh()" [disabled]="wishlist.loading() || wishlist.pending().size > 0" class="mt-4 text-sm underline disabled:opacity-50">Refresh wishlist</button>
   <p *ngIf="wishlist.error()" role="alert" class="mt-3 text-red-700">{{wishlist.error()}}</p>
  </div>
  <p *ngIf="wishlist.loading() || resolving()" role="status">Loading wishlist...</p>
  <div *ngIf="!wishlist.loading() && !wishlist.error() && wishlist.items().length===0" class="rounded-2xl border border-hairline bg-white p-12 text-center">
   <h3 class="text-lg font-bold">Your wishlist is empty</h3><p class="mt-2 text-muted-ink">Use the heart button on a product to save it here.</p><a routerLink="/products" class="mt-6 inline-flex rounded-full bg-brand px-6 py-3 font-semibold text-white">Browse Products</a>
  </div>
  <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
   <div *ngFor="let item of products(); trackBy: trackSku" class="flex flex-col gap-4 rounded-2xl border border-hairline bg-white p-5">
    <a *ngIf="item.available" [routerLink]="['/products',item.productId]"><img *ngIf="item.image" [src]="item.image" [alt]="item.name" class="aspect-square w-full rounded-xl object-cover"/><h3 class="mt-3 font-bold">{{item.name}}</h3></a>
    <h3 *ngIf="!item.available" class="font-bold">{{item.name}}</h3>
    <p class="text-xs text-muted-ink">{{item.code}} {{item.category}}</p>
    <p *ngIf="item.priceMinorUnits!==null" class="font-bold text-brand">{{catalog.formatPrice(item.priceMinorUnits!)}}</p>
    <p *ngIf="!item.available" class="text-sm text-muted-ink">This saved product is no longer available.</p>
    <p *ngIf="item.available && !item.inStock && !item.detailError" class="text-sm text-muted-ink">Currently out of stock.</p>
    <p *ngIf="item.detailError" role="alert" class="text-sm text-red-700">{{item.detailError}}</p>
    <div class="mt-auto flex flex-wrap gap-2">
     <button type="button" (click)="addToCart(item)" [disabled]="!item.available || !item.inStock || item.priceMinorUnits===null || cartPending().has(item.skuId)" class="rounded-full bg-brand px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{{cartPending().has(item.skuId)?'Adding...':'Add to Cart'}}</button>
     <button type="button" (click)="remove(item)" [disabled]="wishlist.loading() || wishlist.isPending(item.skuId)" class="rounded-full border border-hairline px-4 py-2 text-sm">{{wishlist.isPending(item.skuId)?'Removing...':'Remove'}}</button>
    </div>
    <p *ngIf="wishlist.itemErrors()[item.skuId]" role="alert" class="text-sm text-red-700">{{wishlist.itemErrors()[item.skuId]}}</p>
    <p *ngIf="cartMessages()[item.skuId]" role="status" class="text-sm">{{cartMessages()[item.skuId]}}</p>
   </div>
  </div>
 </div>`})
export class WishlistComponent {
 readonly wishlist=inject(WishlistService);
 readonly catalog=inject(CatalogService);
 private cart=inject(CartService);
 private shop=inject(ShopService);
 readonly products=signal<WishlistProduct[]>([]);
 readonly resolving=signal(false);
 readonly cartPending=signal<ReadonlySet<string>>(new Set());
 readonly cartMessages=signal<Record<string,string>>({});
 constructor(){
  toObservable(this.wishlist.items).pipe(tap(items=>{this.products.set([]);this.resolving.set(items.length>0);}),switchMap(items=>this.wishlist.details(items)),takeUntilDestroyed()).subscribe(items=>{this.products.set(items);this.resolving.set(false);});
  this.wishlist.load();
 }
 trackSku(_index:number,item:WishlistProduct){return item.skuId;}
 refresh(){this.wishlist.load();}
 remove(item:WishlistProduct){this.wishlist.set(item.productId,item.skuId,false);}
 addToCart(item:WishlistProduct){
  if(!item.available||!item.inStock||item.priceMinorUnits===null||this.cartPending().has(item.skuId))return;
  this.cartPending.update(ids=>new Set([...ids,item.skuId]));this.cartMessages.update(messages=>({...messages,[item.skuId]:''}));
  this.cart.addItem(item.skuId,1,{sku:item.code,productId:item.productId,name:item.name,category:item.category,priceMinor:item.priceMinorUnits,image:item.image}).subscribe({
   next:result=>{
    this.cartPending.update(ids=>new Set([...ids].filter(id=>id!==item.skuId)));
    this.cartMessages.update(messages=>({...messages,[item.skuId]:result?'Added to cart. This item remains in your wishlist.':'Could not confirm the cart update. Check your cart before retrying.'}));
    if(result)this.shop.showToast({name:item.name,image:item.image,qty:1});
   },
   error:()=>{this.cartPending.update(ids=>new Set([...ids].filter(id=>id!==item.skuId)));this.cartMessages.update(messages=>({...messages,[item.skuId]:'Could not confirm the cart update. Check your cart before retrying.'}));}
  });
 }
}
