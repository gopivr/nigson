package com.nigson.commerce.fulfillment;
import java.util.*;
/** Deterministic area specificity, then priority; ambiguous activation fails closed. */
public final class DeliverySpecificity {
 private DeliverySpecificity(){}
 private static int dimensions(FulfillmentValues.Rule r){return (r.regionCode()==null?0:1)+(r.postalPrefix()==null?0:1);}
 private static int length(FulfillmentValues.Rule r){return r.postalPrefix()==null?0:r.postalPrefix().length();}
 public static final Comparator<FulfillmentValues.Rule> ORDER=Comparator.comparingInt(DeliverySpecificity::dimensions).thenComparingInt(DeliverySpecificity::length).thenComparingInt(FulfillmentValues.Rule::priority).reversed();
 public static boolean matches(FulfillmentValues.Rule r,String country,String region,String postal){return r.active()&&r.countryCode().equals(country)&&(r.regionCode()==null||r.regionCode().equals(region))&&(r.postalPrefix()==null||postal!=null&&postal.startsWith(r.postalPrefix()));}
 public static boolean ambiguous(FulfillmentValues.Rule a,FulfillmentValues.Rule b){return a.active()&&b.active()&&a.marketId().equals(b.marketId())&&a.countryCode().equals(b.countryCode())&&ORDER.compare(a,b)==0&&(a.regionCode()==null||b.regionCode()==null||a.regionCode().equals(b.regionCode()))&&(a.postalPrefix()==null||b.postalPrefix()==null||a.postalPrefix().startsWith(b.postalPrefix())||b.postalPrefix().startsWith(a.postalPrefix()));}
 public static FulfillmentValues.Rule select(List<FulfillmentValues.Rule> rules,String country,String region,String postal){var candidates=rules.stream().filter(r->matches(r,country,region,postal)).sorted(ORDER).toList();if(candidates.isEmpty()||candidates.size()>1&&ORDER.compare(candidates.get(0),candidates.get(1))==0)throw FulfillmentValues.rule();return candidates.getFirst();}
}
