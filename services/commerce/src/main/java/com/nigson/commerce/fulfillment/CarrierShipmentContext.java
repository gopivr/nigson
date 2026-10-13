package com.nigson.commerce.fulfillment;
import java.util.*;
import com.nigson.commerce.pricing.CarrierPacking;
import com.nigson.commerce.order.OrderValues;
/** Private immutable logistics values; never current mutable party/address identifiers. */
public record CarrierShipmentContext(UUID shipmentId,UUID orderId,OrderValues.Address address,CarrierPacking.Address shipper,String serviceCode,List<CarrierPacking.Parcel> packages,String bookingReference,String mode){
 public CarrierShipmentContext{packages=List.copyOf(packages);}
 @Override public String toString(){return "CarrierShipmentContext[protected]";}
}
