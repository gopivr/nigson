package com.nigson.commerce.wishlist;
import java.time.Instant;
import java.util.UUID;
public final class WishlistValues {
 private WishlistValues(){}
 public record Item(UUID skuId, UUID productId, Instant savedAt, boolean available) {}
}
