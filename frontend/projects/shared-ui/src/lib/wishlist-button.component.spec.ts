import '@angular/compiler';
import {describe,it,expect,vi} from 'vitest';
import {WishlistButtonComponent} from './wishlist-button.component';
describe('wishlist control',()=>{
 it('saves the selected SKU without activating the enclosing product navigation',()=>{
  const control:any=Object.create(WishlistButtonComponent.prototype);control.productId='product';control.skuId='variant';control.wishlist={toggle:vi.fn()};const event={preventDefault:vi.fn(),stopPropagation:vi.fn()};control.toggle(event);expect(control.wishlist.toggle).toHaveBeenCalledWith('product','variant');expect(event.stopPropagation).toHaveBeenCalled();
 });
 it('never substitutes a product ID when no SKU is resolved',()=>{
  const control:any=Object.create(WishlistButtonComponent.prototype);control.productId='product';control.wishlist={toggle:vi.fn()};control.toggle({preventDefault(){},stopPropagation(){}});expect(control.wishlist.toggle).not.toHaveBeenCalled();
 });
});
