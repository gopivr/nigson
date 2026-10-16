package com.nigson.commerce.fulfillment;

import java.time.Instant;
import java.util.*;
import com.nigson.commerce.pricing.CarrierPacking;

/** Private rate protocol; commerce owns cart, packing, address and the persisted bound quote. */
public interface CarrierRates {
 record Request(UUID commandId,UUID cartId,String market,String currency,CarrierPacking.Address shipper,CarrierPacking.Address destination,List<CarrierPacking.Parcel> packages,String serviceCode){
  public Request{packages=List.copyOf(packages);}
  @Override public String toString(){return "CarrierRateRequest[protected]";}
 }
 record Rate(String provider,String serviceCode,String currency,String amount,Instant expiresAt,String providerReference,@com.fasterxml.jackson.annotation.JsonIgnore String accountHash){@Override public String toString(){return "CarrierRate[protected]";}}
 Rate quote(Request request,String actorAssertion,UUID correlation);
}
