package com.nigson.commerce.fulfillment;
import java.util.UUID;
public interface ShipmentRepository {
 UUID orderId(UUID shipment);
 UUID organization(UUID order);
 void lockOrder(UUID order);
 java.util.List<com.nigson.commerce.inventory.StockLedger.Line> stockLines(UUID shipment);
 void progress(UUID order,String actor);
 void event(UUID shipment,UUID command,String status,java.time.Instant occurred,String actor);
 FulfillmentValues.Shipment find(UUID shipment,boolean lock);
 FulfillmentValues.Shipment create(UUID order,FulfillmentValues.ShipmentInput input,String actor);
 FulfillmentValues.Shipment dispatch(UUID shipment,long expected,String actor);
 FulfillmentValues.Shipment dispatch(UUID shipment,long expected,String actor,java.time.Instant dispatchedAt);
 FulfillmentValues.Shipment delivered(UUID shipment,long expected,FulfillmentValues.Delivery input,String actor);
}
