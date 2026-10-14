package com.nigson.integration.carrier;

import java.math.*;
import java.time.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;

/** Pure server-owned FedEx Ship request. The execution owner must persist its hash before HTTP. */
public final class CarrierShipmentRequest {
 private static final Set<String> PAPER=Set.of("PAPER_4X6","PAPER_4X675","PAPER_4X8","PAPER_4X9","PAPER_7X475","PAPER_85X11_BOTTOM_HALF_LABEL","PAPER_85X11_TOP_HALF_LABEL","PAPER_LETTER");
 private final Set<String> services;private final String pickup,stock,billing;private final Clock clock;private final Instant approvedUntil;
 public CarrierShipmentRequest(Set<String> services,String pickup,String labelStock,String billing,String approval,Instant until,Clock clock){
  if(services==null||services.isEmpty()||services.size()>100||pickup==null||!Set.of("DROPOFF_AT_FEDEX_LOCATION","USE_SCHEDULED_PICKUP","CONTACT_FEDEX_TO_SCHEDULE").contains(pickup)||labelStock==null||!PAPER.contains(labelStock)||!"SENDER".equals(billing)||approval==null||!approval.matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,119}")||until==null||clock==null)throw new IllegalArgumentException("CARRIER_SHIP_APPROVAL_REQUIRED");for(String service:services)CarrierValues.service(service);this.services=Set.copyOf(services);this.pickup=pickup;stock=labelStock;this.billing=billing;this.clock=clock;approvedUntil=until;
 }
 public JsonNode build(CarrierCreationContext context,LocalDate shippingDate){
  if(!approvedUntil.isAfter(clock.instant()))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);if(context==null||shippingDate==null||shippingDate.getYear()<2000||shippingDate.getYear()>9999||!services.contains(context.serviceCode()))throw CarrierValues.rule();
  var boxes=new ArrayList<Map<String,Object>>();BigDecimal total=BigDecimal.ZERO;var lines=CarrierValues.packageLines(context.packages());for(int i=0;i<lines.size();i++){var box=new LinkedHashMap<String,Object>(lines.get(i));box.put("sequenceNumber",i+1);box.put("customerReferences",List.of(Map.of("customerReferenceType","CUSTOMER_REFERENCE","value",context.bookingReference().toString())));boxes.add(box);total=total.add(new BigDecimal(context.packages().get(i).weightKg()));}
  var shipment=new LinkedHashMap<String,Object>();shipment.put("shipper",CarrierValues.shippingParty(context.shipper()));shipment.put("recipients",List.of(CarrierValues.shippingParty(context.destination())));shipment.put("serviceType",context.serviceCode());shipment.put("pickupType",pickup);shipment.put("packagingType","YOUR_PACKAGING");shipment.put("shipDatestamp",shippingDate.toString());shipment.put("shippingChargesPayment",Map.of("paymentType",billing));shipment.put("labelSpecification",Map.of("imageType","PDF","labelStockType",stock));shipment.put("totalWeight",total.setScale(1,RoundingMode.CEILING));shipment.put("totalPackageCount",boxes.size());shipment.put("requestedPackageLineItems",boxes);
  var request=EventCodec.JSON.valueToTree(Map.of("requestedShipment",shipment,"labelResponseOptions","LABEL","processingOptionType",boxes.size()>40?"ALLOW_ASYNCHRONOUS":"SYNCHRONOUS_ONLY"));if(EventCodec.JSON.writeValueAsBytes(request).length>65536)throw CarrierValues.rule();return request;
 }
}
