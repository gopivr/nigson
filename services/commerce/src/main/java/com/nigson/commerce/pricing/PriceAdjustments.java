package com.nigson.commerce.pricing;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
/** Promotion module replaces this port at BP2. Calculation does not consume a coupon. */
public interface PriceAdjustments {
 record Result(Map<UUID,BigDecimal> lineDiscounts,BigDecimal deliveryDiscount,tools.jackson.databind.JsonNode revisions,Instant validUntil){}
 Result quote(UUID party,UUID organization,UUID market,String coupon,Map<UUID,BigDecimal> amounts,BigDecimal delivery,Instant now,Instant expires);
}
