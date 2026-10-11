package com.nigson.commerce.pricing;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
/** Caller-owned local transaction port. Resolution neither reserves stock nor invokes a provider. */
public interface Pricing {
 record Resolved(UUID skuId,UUID productId,UUID bookId,long bookRevision,BigDecimal quantity,BigDecimal unitAmount,BigDecimal minimumQuantity,BigDecimal packMultiple,BigDecimal minimumOrderAmount,Instant validUntil,tools.jackson.databind.JsonNode pricingContext){}
 Resolved resolve(UUID sku,BigDecimal quantity,UUID market,UUID organization,Instant now,Instant maximumValidity,boolean lock);
}
