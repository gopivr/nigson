package com.nigson.commerce.fulfillment;
import java.util.*;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.commerce.order.OrderAuthority;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.problem.*;
/** Owned immutable read projection; no provider HTTP, private object keys or raw internal notes. */
public final class ShipmentTrackingService {
 public record Event(String status,Instant occurredAt,String note){}
 public record Tracking(UUID orderId,List<FulfillmentValues.Shipment> shipments,List<Event> events){public Tracking{shipments=List.copyOf(shipments);events=List.copyOf(events);}@Override public String toString(){return "ShipmentTracking[protected]";}}
 private final JdbcTemplate jdbc;private final ShipmentRepository shipments;private final OrderAuthority authority;private final TransactionTemplate read;
 public ShipmentTrackingService(JdbcTemplate jdbc,ShipmentRepository shipments,OrderAuthority authority,PlatformTransactionManager manager){this.jdbc=jdbc;this.shipments=shipments;this.authority=authority;read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(3);}
 public Tracking tracking(ActorAssertion.Actor actor,UUID order){ApiValues.uuid(order==null?null:order.toString());authority.customer(actor,false);return read.execute(status->{jdbc.execute("SET LOCAL statement_timeout='2s'");authority.owned(actor,order,false);var ids=jdbc.query("SELECT id FROM commerce.shipment WHERE order_id=? ORDER BY created_at,id LIMIT 101",(r,n)->r.getObject(1,UUID.class),order);if(ids.size()>100)throw FulfillmentValues.rule();var projected=ids.stream().map(id->shipments.find(id,false)).toList();for(var shipment:projected)if(shipment.trackingUrl()!=null)FulfillmentValues.https(shipment.trackingUrl());
  var events=jdbc.query("SELECT e.status,e.occurred_at FROM commerce.shipment_event e JOIN commerce.shipment s ON s.id=e.shipment_id WHERE s.order_id=? AND e.source IN ('OPERATIONS','FEDEX','MANUAL') AND e.status IN ('CREATED','READY','SHIPPED','DELIVERED','CANCELLED','EXCEPTION','BOOKED','UNKNOWN','FAILED') ORDER BY e.occurred_at DESC,e.created_at DESC,e.id DESC LIMIT 100",(r,n)->new Event(r.getString(1),r.getTimestamp(2).toInstant(),null),order);return new Tracking(order,projected,events);
 });}
}
