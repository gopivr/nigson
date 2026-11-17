package com.nigson.commerce.wishlist;
import java.util.*;
public interface WishlistRepository {
 List<WishlistValues.Item> list(UUID party);
 boolean eligible(UUID sku);
 boolean contains(UUID party, UUID sku);
 int count(UUID party);
 boolean add(UUID party, UUID sku);
 boolean remove(UUID party, UUID sku);
}
