package com.nigson.commerce.fulfillment;
import java.util.*;
import com.nigson.commerce.pricing.CarrierPacking;
import com.nigson.commerce.order.OrderValues;
/** Private immutable logistics values; never current mutable party/address identifiers. */
public record CarrierShipmentContext(UUID shipmentId,UUID orderId,OrderValues.Address address,CarrierPacking.Address shipper,String serviceCode,List<CarrierPacking.Parcel> packages,String bookingReference,String mode,String accountHash,String paymentMethod,java.time.Instant preorderedFor,String amount,String description,String deliveryAmount,String pickupCode){
 public CarrierShipmentContext{packages=List.copyOf(packages);if(pickupCode==null||!pickupCode.matches("[A-Za-z0-9]{1,30}"))throw new IllegalArgumentException("CARRIER_PICKUP_CODE_INVALID");}
 @Override public String toString(){return "CarrierShipmentContext[protected]";}
}
