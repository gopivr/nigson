package com.nigson.commerce.fulfillment;
import java.time.*;
import java.util.*;
import com.nigson.shared.problem.*;
/** Explicit server qualification metadata; never derived from a browser provider reference. */
public final class FulfillmentPolicy {
 private final boolean enabled;private final String mode,approval,manual;private final Instant until;private final Set<String> trackingHosts;private final Clock clock;
 public FulfillmentPolicy(boolean enabled,String environment,String mode,String approval,Instant until,String manual,Set<String> trackingHosts,Clock clock){this.enabled=enabled;this.mode=mode;this.approval=approval;this.until=until;this.manual=manual;this.trackingHosts=Set.copyOf(trackingHosts);this.clock=clock;if(enabled&&(!Set.of("MOCK","SANDBOX","LIVE").contains(mode)||approval==null||!approval.matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,119}")||until==null||environment.equals("prod")&&!mode.equals("LIVE")||mode.equals("MOCK")&&!Set.of("local","test").contains(environment)))throw new IllegalArgumentException("FULFILLMENT_PROVIDER_CONFIGURATION_INVALID");if(manual!=null&&!manual.isEmpty()&&!manual.matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,119}"))throw new IllegalArgumentException("FULFILLMENT_CONTINGENCY_CONFIGURATION_INVALID");for(String host:trackingHosts)if(!host.matches("[a-z0-9][a-z0-9.-]{0,252}")||host.contains(".."))throw new IllegalArgumentException("FULFILLMENT_TRACKING_CONFIGURATION_INVALID");}
 public String require(String carrierMode,String provider){if(!enabled||until==null||!until.isAfter(clock.instant()))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);if(!mode.equals(carrierMode))throw new Problem.Failure(ProblemCode.PROVIDER_MODE_MISMATCH);if(provider.equals("MANUAL")&&(manual==null||manual.isEmpty()))throw FulfillmentValues.rule();return provider.equals("MANUAL")?manual:approval;}
 public void tracking(String url){if(url==null)return;FulfillmentValues.https(url);String host=java.net.URI.create(url).getHost().toLowerCase(Locale.ROOT);if(!trackingHosts.contains(host))throw FulfillmentValues.rule();}
 public Instant qualifiedUntil(String carrierMode){require(carrierMode,"FEDEX");return until;}
}
