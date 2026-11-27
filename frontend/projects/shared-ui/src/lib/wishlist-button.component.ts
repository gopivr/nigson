import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WishlistService } from './wishlist.service';
@Component({selector:'ui-wishlist-button',standalone:true,imports:[CommonModule],template:`
 <div>
  <button type="button" [disabled]="!skuId || skuId===productId || wishlist.loading() || wishlist.isPending(skuId)" [attr.aria-pressed]="wishlist.isSaved(skuId)" [attr.aria-label]="wishlist.isSaved(skuId)?'Remove from wishlist':'Save to wishlist'" (click)="toggle($event)" class="inline-flex items-center gap-2 rounded-full border border-hairline bg-white px-4 py-3 text-sm font-semibold hover:text-brand disabled:opacity-50">
   <svg viewBox="0 0 24 24" class="h-5 w-5" [attr.fill]="wishlist.isSaved(skuId)?'currentColor':'none'" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/></svg>
   {{wishlist.isPending(skuId)?'Saving...':wishlist.isSaved(skuId)?'Saved':'Save'}}
  </button>
  <p *ngIf="skuId && wishlist.itemErrors()[skuId]" role="alert" class="mt-2 text-xs text-red-700">{{wishlist.itemErrors()[skuId]}}</p>
  <p *ngIf="wishlist.error()" role="alert" class="mt-2 text-xs text-red-700">{{wishlist.error()}} <button type="button" (click)="wishlist.load()" class="underline">Retry</button></p>
 </div>`})
export class WishlistButtonComponent {
 @Input({required:true}) productId='';
 @Input() skuId: string | undefined;
 readonly wishlist=inject(WishlistService);
 toggle(event:Event){event.preventDefault();event.stopPropagation();if(this.skuId && this.skuId!==this.productId)this.wishlist.toggle(this.productId,this.skuId);}
}
