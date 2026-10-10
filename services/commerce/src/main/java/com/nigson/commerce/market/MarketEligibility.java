package com.nigson.commerce.market;
import java.util.*;
import java.time.Instant;
/** Local commerce checkout port; provider proof comes from the qualified owning adapters, never a browser DTO. */
public interface MarketEligibility {
 record ProviderProof(String currency,String country,String mode,String paymentApprovalReference,String carrierApprovalReference,Instant validUntil){}
 record Sale(UUID marketId,String currency,int minorUnit,long policyRevision,UUID retailPriceBookId,MarketValues.TaxPolicy taxPolicy,String returnsPolicyRevision){}
 Sale require(UUID market,String country,Set<UUID> products,tools.jackson.databind.JsonNode address,ProviderProof providers);
}
