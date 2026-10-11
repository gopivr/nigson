package com.nigson.commerce.pricing;
import java.util.UUID;
public interface PriceBookRepository {
 PricingValues.Book create(PricingValues.Input value,String actor);
 PricingValues.Book revise(UUID source,long expected,PricingValues.Input value,String actor);
 PricingValues.Book activate(UUID id,long expected,String actor);
 PricingValues.Page list(PricingValues.Query query);
}
