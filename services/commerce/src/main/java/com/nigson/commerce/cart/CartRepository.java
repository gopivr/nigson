package com.nigson.commerce.cart;
import java.util.UUID;
public interface CartRepository {
 CartValues.Cart find(UUID party,UUID id,boolean lock);
 record Created(CartValues.Cart cart,boolean created){}
 Created create(UUID party,CartValues.Create input,String actor);
 CartValues.Cart set(UUID party,UUID id,UUID sku,long expected,CartValues.Quantity input,String actor);
 CartValues.Removed remove(UUID party,UUID id,UUID sku,long expected,String actor);
}
