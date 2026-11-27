import '@angular/compiler';
import {signal} from '@angular/core';
import {of,Subject} from 'rxjs';
import {describe,it,expect,vi} from 'vitest';
import {WishlistComponent} from './wishlist.component';
function setup(result:any){const c:any=Object.create(WishlistComponent.prototype);c.cart={addItem:vi.fn(()=>result)};c.shop={showToast:vi.fn()};c.wishlist={set:vi.fn()};c.cartPending=signal(new Set());c.cartMessages=signal({});return c;}
const item={productId:'product',skuId:'variant',name:'Product',code:'CODE',category:'Audio',priceMinorUnits:173900,inStock:true,available:true,image:''};
describe('wishlist cart integration',()=>{
 it('uses actual SKU/minor units and keeps the saved entry after success',()=>{const c=setup(of({cartId:'cart'}));c.addToCart(item);expect(c.cart.addItem).toHaveBeenCalledWith('variant',1,expect.objectContaining({productId:'product',priceMinor:173900}));expect(c.wishlist.set).not.toHaveBeenCalled();expect(c.cartMessages().variant).toContain('remains');expect(c.shop.showToast).toHaveBeenCalledOnce();});
 it('does not claim success or remove an item on unconfirmed cart update',()=>{const c=setup(of(null));c.addToCart(item);expect(c.shop.showToast).not.toHaveBeenCalled();expect(c.wishlist.set).not.toHaveBeenCalled();expect(c.cartMessages().variant).toContain('Could not confirm');});
 it('prevents duplicate in-flight cart additions',()=>{const response=new Subject();const c=setup(response);c.addToCart(item);c.addToCart(item);expect(c.cart.addItem).toHaveBeenCalledOnce();response.next(null);});
 it('blocks unavailable, out of stock and unpriced products',()=>{const c=setup(of(null));for(const invalid of [{...item,available:false},{...item,inStock:false},{...item,priceMinorUnits:null}])c.addToCart(invalid);expect(c.cart.addItem).not.toHaveBeenCalled();});
 it('removes only through explicit remove action',()=>{const c=setup(of(null));c.remove(item);expect(c.wishlist.set).toHaveBeenCalledWith('product','variant',false);});
});
