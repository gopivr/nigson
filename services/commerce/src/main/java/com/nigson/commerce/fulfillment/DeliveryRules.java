package com.nigson.commerce.fulfillment;
import java.util.UUID;
import java.math.BigDecimal;
/** Commerce-local caller transaction; fees never replace a live provider quote implicitly. */
public interface DeliveryRules {
 record Fee(UUID ruleId,long version,String amount){}
 Fee resolve(UUID market,String country,String region,String postal,BigDecimal subtotal);
}
