package com.nigson.commerce.pricing;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public interface FrozenPrices {
 record Line(UUID skuId,BigDecimal quantity,BigDecimal unitAmount,BigDecimal discount,BigDecimal tax){}
 record Offer(UUID quoteId,UUID revisionId,long revision,Instant validUntil,List<Line> lines){}
 Offer require(UUID revision,UUID party,UUID organization,UUID market,Map<UUID,BigDecimal> quantities,Instant now);
}
