package com.nigson.commerce.fulfillment;
import java.util.*;
import com.nigson.commerce.pricing.CarrierPacking;
import com.nigson.commerce.order.OrderValues;
/** Private immutable logistics values; never current mutable party/address identifiers. */
public record CarrierShipmentContext(UUID shipmentId,UUID orderId,OrderValues.Address address,CarrierPacking.Address shipper,String serviceCode,List<CarrierPacking.Parcel> packages,String bookingReference,String mode,String accountHash,String paymentMethod,java.time.Instant preorderedFor,String amount,String description,String deliveryAmount){
 public CarrierShipmentContext(UUID shipmentId,UUID orderId,OrderValues.Address address,CarrierPacking.Address shipper,String serviceCode,List<CarrierPacking.Parcel> packages,String bookingReference,String mode,String accountHash){this(shipmentId,orderId,address,shipper,serviceCode,packages,bookingReference,mode,accountHash,"PAID",null,"0.00","Nigson delivery","0.00");}
 public CarrierShipmentContext{packages=List.copyOf(packages);}
 @Override public String toString(){return "CarrierShipmentContext[protected]";}
}
