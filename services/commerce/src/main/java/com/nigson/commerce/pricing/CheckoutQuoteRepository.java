package com.nigson.commerce.pricing;
import java.util.UUID;
public interface CheckoutQuoteRepository {
 void requireOwner(com.nigson.shared.security.ActorAssertion.Actor actor,PricingValues.QuoteInput input);
 void requireReplay(com.nigson.shared.security.ActorAssertion.Actor actor,UUID quote);
 PricingValues.Quote create(com.nigson.shared.security.ActorAssertion.Actor actor,PricingValues.QuoteInput input);
}
