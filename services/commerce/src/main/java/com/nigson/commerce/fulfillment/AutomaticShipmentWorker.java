package com.nigson.commerce.fulfillment;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.pricing.PricingValues;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.AuditValues;

/** No preparation buffer: persist intent/outbox as soon as authoritative stock/payment permit it. */
public final class AutomaticShipmentWorker {
 private final JdbcTemplate jdbc;private final JdbcShipmentRepository shipments;private final PlatformStore events;private final AuditWriter audit;private final TransactionTemplate tx;private final Clock clock;private final Instant notBefore;
 public AutomaticShipmentWorker(JdbcTemplate jdbc,JdbcShipmentRepository shipments,PlatformStore events,AuditWriter audit,PlatformTransactionManager manager,Clock clock,Instant notBefore){this.jdbc=jdbc;this.shipments=shipments;this.events=events;this.audit=audit;this.clock=clock;this.notBefore=Objects.requireNonNull(notBefore);tx=new TransactionTemplate(manager);tx.setTimeout(5);}
 @Scheduled(fixedDelayString="${NIGSON_PANDAGO_INTAKE_DELAY_MS:1000}") public void scheduled(){try{poll();}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("PANDAGO_SHIPMENT_INTAKE_RETRY_REQUIRED");}}
 public int poll(){int count=0;var skipped=new HashSet<UUID>();for(int n=0;n<20;n++){UUID[] current={null};try{Boolean created=tx.execute(status->{
  jdbc.execute("SET LOCAL lock_timeout='350ms'");jdbc.execute("SET LOCAL statement_timeout='3s'");
  var rows=jdbc.query("SELECT o.id FROM commerce.sales_order o WHERE o.created_at>=? AND o.hold_reason IS NULL AND o.status IN ('PAID','PROCESSING') AND o.carrier_snapshot->>'provider'='PANDAGO' AND (o.payment_status='PAID' OR o.payment_status='UNPAID' AND o.carrier_snapshot->>'paymentMethod'='CASH_ON_DELIVERY') AND NOT EXISTS(SELECT 1 FROM commerce.shipment s WHERE s.order_id=o.id) AND NOT(o.id=ANY(?::uuid[])) ORDER BY o.created_at,o.id FOR UPDATE OF o SKIP LOCKED LIMIT 1",(r,i)->r.getObject(1,UUID.class),java.sql.Timestamp.from(notBefore),"{"+String.join(",",skipped.stream().map(UUID::toString).toList())+"}");
  if(rows.isEmpty())return false;UUID order=rows.getFirst();current[0]=order;String subject="pandago-shipment-intake";UUID correlation=UUID.randomUUID();
  var lines=jdbc.query("SELECT ol.id,r.balance_id,ol.quantity FROM commerce.order_line ol JOIN commerce.inventory_reservation r ON r.order_id=ol.order_id JOIN commerce.inventory_balance b ON b.id=r.balance_id AND b.sku_id=ol.sku_id WHERE ol.order_id=? AND r.status='ALLOCATED' ORDER BY ol.id LIMIT 101",(r,i)->new FulfillmentValues.LineInput(r.getObject(1,UUID.class),r.getObject(2,UUID.class),PricingValues.decimal(r.getBigDecimal(3))),order);
  if(lines.isEmpty()||lines.size()>100)throw FulfillmentValues.rule();
  var shipment=shipments.create(order,new FulfillmentValues.ShipmentInput(lines,"PANDAGO",null,null),subject);
  UUID reference=jdbc.queryForObject("SELECT booking_request_id FROM commerce.shipment WHERE id=?",UUID.class,shipment.id());
  var event=new LinkedHashMap<String,Object>();event.put("event_id",EventCodec.uuid7(clock.instant()));event.put("event_type","shipment.booking.requested.v1");event.put("schema_version",1);event.put("producer","commerce");event.put("occurred_at",clock.instant());event.put("aggregate_id",shipment.id());event.put("aggregate_version",shipment.version());event.put("organization_id",shipments.organization(order));event.put("correlation_id",correlation);event.put("traceparent",null);event.put("payload",Map.of("shipment_id",shipment.id(),"booking_request_id",reference,"provider","PANDAGO"));events.append(EventCodec.JSON.writeValueAsString(event),subject);
  audit.append(subject,AuditValues.ActorKind.SYSTEM,AuditValues.Action.SHIPMENT_CREATE,AuditValues.Resource.SHIPMENT,shipment.id(),correlation,AuditValues.Reason.RECONCILIATION,null,Map.of("state",shipment.status(),"version",shipment.version()),null);return true;
 });if(!Boolean.TRUE.equals(created))break;count++;}catch(RuntimeException failure){if(current[0]==null)throw failure;skipped.add(current[0]);org.slf4j.LoggerFactory.getLogger(getClass()).warn("PANDAGO_ORDER_INTAKE_REQUIRES_REVIEW");}}return count;}
}
