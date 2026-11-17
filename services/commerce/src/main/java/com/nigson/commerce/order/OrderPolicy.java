package com.nigson.commerce.order;
import java.util.*;
import com.nigson.shared.problem.*;
/** No invented merchant account or stock priority. Defaults keep checkout disabled. */
public record OrderPolicy(boolean enabled,String environment,String mode,String merchantAccount,List<UUID> locations,int reservationSeconds){
 public OrderPolicy{locations=List.copyOf(locations);if(reservationSeconds<60||reservationSeconds>3600||locations.size()>10||locations.stream().distinct().count()!=locations.size()||locations.stream().anyMatch(x->x==null||x.equals(new UUID(0,0))))throw new IllegalArgumentException("ORDER_POLICY_INVALID");if(enabled&&(!Set.of("MOCK","TEST","LIVE").contains(mode)||merchantAccount==null||!merchantAccount.matches("[A-Za-z0-9][A-Za-z0-9_./:-]{0,99}")||locations.isEmpty()||mode.equals("MOCK")&&!Set.of("local","test").contains(environment)||environment.equals("prod")&&!mode.equals("LIVE")))throw new IllegalArgumentException("ORDER_ACTIVATION_REQUIRED");}
 public void require(String carrierMode){if(!enabled)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);if(!Objects.equals(carrierMode,mode.equals("TEST")?"SANDBOX":mode))throw new Problem.Failure(ProblemCode.PROVIDER_MODE_MISMATCH);}
 /** Hosted checkout must return the browser to our own storefront route; MOCK has no browser. */
 public String checkoutReturnUrl(String url){
  if(url==null||url.isBlank()){if(enabled&&!mode.equals("MOCK"))throw new IllegalArgumentException("CHECKOUT_RETURN_URL_REQUIRED");return null;}
  if(url.length()>1000)throw new IllegalArgumentException("CHECKOUT_RETURN_URL_REQUIRED");
  java.net.URI value;try{value=java.net.URI.create(url);}catch(IllegalArgumentException invalid){throw new IllegalArgumentException("CHECKOUT_RETURN_URL_REQUIRED");}
  boolean secure="https".equals(value.getScheme());
  boolean localHttp="http".equals(value.getScheme())&&value.getHost()!=null&&Set.of("local","test").contains(environment)&&Set.of("localhost","127.0.0.1").contains(value.getHost());
  if(value.getHost()==null||value.getUserInfo()!=null||value.getFragment()!=null||value.getRawQuery()!=null||!(secure||localHttp))throw new IllegalArgumentException("CHECKOUT_RETURN_URL_REQUIRED");
  return url;
 }
}
