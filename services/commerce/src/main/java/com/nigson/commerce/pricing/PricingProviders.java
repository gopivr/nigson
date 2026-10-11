package com.nigson.commerce.pricing;
import java.time.*;
import java.util.*;
import com.nigson.commerce.market.MarketEligibility;
import com.nigson.shared.problem.*;
/** Reviewed server configuration, never request values. No default provider or selling approval. */
public final class PricingProviders {
 private final boolean enabled;private final String environment,mode,paymentReference,carrierReference;private final Instant validUntil;private final Clock clock;
 public PricingProviders(boolean enabled,String environment,String mode,String paymentReference,String carrierReference,Instant validUntil,Clock clock){this.enabled=enabled;this.environment=environment;this.mode=mode;this.paymentReference=paymentReference;this.carrierReference=carrierReference;this.validUntil=validUntil;this.clock=clock;if(enabled&&(!Set.of("MOCK","TEST","LIVE").contains(mode)||!ref(paymentReference)||!ref(carrierReference)||validUntil==null||!validUntil.isAfter(clock.instant())||!Set.of("local","test").contains(environment)&&mode.equals("MOCK")||environment.equals("prod")&&!mode.equals("LIVE")))throw new IllegalArgumentException("PRICING_PROVIDER_APPROVAL_REQUIRED");}
 private static boolean ref(String value){return value!=null&&value.matches("[A-Za-z0-9][A-Za-z0-9_./:-]{0,119}");}
 public MarketEligibility.ProviderProof require(String carrierMode){if(!enabled)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);if(!validUntil.isAfter(clock.instant()))throw PricingValues.rule();String expected=switch(mode){case "TEST"->"SANDBOX";default->mode;};if(!expected.equals(carrierMode)||environment.equals("prod")&&!carrierMode.equals("LIVE"))throw new Problem.Failure(ProblemCode.PROVIDER_MODE_MISMATCH);return new MarketEligibility.ProviderProof("NGN","NG",mode,paymentReference,carrierReference,validUntil);}
}
