package com.nigson.integration.carrier;

import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ProviderHttp;

/** Integration-owned wire values from API-119; no commerce entities or current-address fallback. */
public record CarrierCreationContext(UUID shipmentId,UUID orderId,CarrierValues.Address destination,CarrierValues.Address shipper,String serviceCode,List<CarrierValues.Parcel> packages,UUID bookingReference,String mode){
 public CarrierCreationContext{
  id(shipmentId);id(orderId);id(bookingReference);if(destination==null||shipper==null||packages==null||packages.isEmpty()||packages.size()>100||mode==null||!Set.of("MOCK","SANDBOX","LIVE").contains(mode))throw CarrierValues.rule();CarrierValues.service(serviceCode);packages=List.copyOf(packages);
 }
 @Override public String toString(){return "CarrierCreationContext[protected]";}
 private static void id(UUID value){if(value==null||value.equals(new UUID(0,0)))throw CarrierValues.rule();}
 private static String string(JsonNode node,String field){var value=node.get(field);if(value==null||!value.isString())throw CarrierValues.rule();return value.asString();}
 public static CarrierCreationContext parse(JsonNode node,UUID shipment,UUID reference,ProviderHttp.Mode providerMode){
  id(shipment);id(reference);if(providerMode==null)throw CarrierValues.rule();var fields=Set.of("shipmentId","orderId","address","shipper","serviceCode","packages","bookingReference","mode");if(node==null||!node.isObject()||!node.propertyNames().equals(fields))throw CarrierValues.rule();
  var address=node.get("address");var addressFields=Set.of("label","recipient","phone","line1","line2","city","region","postalCode","countryCode","isDefault");if(address==null||!address.isObject()||!address.propertyNames().equals(addressFields)||!address.path("label").isString()||address.path("label").asString().length()>160||!address.path("isDefault").isBoolean())throw CarrierValues.rule();
  var minimal=((tools.jackson.databind.node.ObjectNode)address).deepCopy();minimal.remove("label");minimal.remove("isDefault");var parcels=node.get("packages");if(!parcels.isArray()||parcels.isEmpty()||parcels.size()>100)throw CarrierValues.rule();var result=new ArrayList<CarrierValues.Parcel>();var parcelFields=Set.of("skuId","quantity","weightKg","lengthCm","widthCm","heightCm");for(var parcel:parcels){if(!parcel.isObject()||!parcel.propertyNames().equals(parcelFields))throw CarrierValues.rule();result.add(new CarrierValues.Parcel(ApiValues.uuid(string(parcel,"skuId")),string(parcel,"quantity"),string(parcel,"weightKg"),string(parcel,"lengthCm"),string(parcel,"widthCm"),string(parcel,"heightCm")));}
  var context=new CarrierCreationContext(ApiValues.uuid(string(node,"shipmentId")),ApiValues.uuid(string(node,"orderId")),CarrierValues.address(minimal),CarrierValues.address(node.get("shipper")),string(node,"serviceCode"),result,ApiValues.uuid(string(node,"bookingReference")),string(node,"mode"));
  if(!context.shipmentId().equals(shipment)||!context.bookingReference().equals(reference))throw CarrierValues.rule();if(!context.mode().equals(providerMode==ProviderHttp.Mode.TEST?"SANDBOX":providerMode.name()))throw new Problem.Failure(ProblemCode.PROVIDER_MODE_MISMATCH);return context;
 }
}
