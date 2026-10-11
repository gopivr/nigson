package com.nigson.commerce.order;
import java.util.*;
import com.nigson.shared.problem.*;
/** No invented merchant account or stock priority. Defaults keep checkout disabled. */
public record OrderPolicy(boolean enabled,String environment,String mode,String merchantAccount,List<UUID> locations,int reservationSeconds){
 public OrderPolicy{locations=List.copyOf(locations);if(reservationSeconds<60||reservationSeconds>3600||locations.size()>10||locations.stream().distinct().count()!=locations.size()||locations.stream().anyMatch(x->x==null||x.equals(new UUID(0,0))))throw new IllegalArgumentException("ORDER_POLICY_INVALID");if(enabled&&(!Set.of("MOCK","TEST","LIVE").contains(mode)||merchantAccount==null||!merchantAccount.matches("[A-Za-z0-9][A-Za-z0-9_./:-]{0,99}")||locations.isEmpty()||mode.equals("MOCK")&&!Set.of("local","test").contains(environment)||environment.equals("prod")&&!mode.equals("LIVE")))throw new IllegalArgumentException("ORDER_ACTIVATION_REQUIRED");}
 public void require(String carrierMode){if(!enabled)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);if(!Objects.equals(carrierMode,mode.equals("TEST")?"SANDBOX":mode))throw new Problem.Failure(ProblemCode.PROVIDER_MODE_MISMATCH);}
}
