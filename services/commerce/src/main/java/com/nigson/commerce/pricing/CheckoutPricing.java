package com.nigson.commerce.pricing;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.security.ActorAssertion;
/** Commerce-local T1 pricing guard. Caller holds one transaction; no provider HTTP. */
public interface CheckoutPricing {
 record Snapshot(PricingValues.Quote quote,JsonNode commercial,CarrierPacking.Address address){}
 Snapshot require(ActorAssertion.Actor actor,UUID quote,String hash);
}
