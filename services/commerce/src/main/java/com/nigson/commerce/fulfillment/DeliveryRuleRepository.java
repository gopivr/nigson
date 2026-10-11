package com.nigson.commerce.fulfillment;
import java.util.UUID;
public interface DeliveryRuleRepository {
 FulfillmentValues.Rule find(UUID id,boolean lock);
 FulfillmentValues.Rule create(FulfillmentValues.RuleInput input,String actor);
 FulfillmentValues.Rule update(UUID id,long expected,FulfillmentValues.RuleInput input,String actor);
}
