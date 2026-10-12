package com.nigson.integration.platform;
import com.nigson.shared.observability.SafeRoutes;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.*;
/** Reject unregistered custom tags and normalize framework HTTP labels. */
public final class MetricPolicy implements MeterFilter {
 public Meter.Id map(Meter.Id id){if(id.getName().equals("http.server.requests"))return id.replaceTags(id.getTags().stream().map(t->switch(t.getKey()){case "uri"->Tag.of("uri",SafeRoutes.route(t.getValue()));case "exception"->Tag.of("exception",t.getValue().equals("none")?"NONE":"ERROR");case "method"->Tag.of("method",SafeRoutes.method(t.getValue()));default->t;}).toList());return id;}
 public io.micrometer.core.instrument.config.MeterFilterReply accept(Meter.Id id){if(!id.getName().startsWith("nigson."))return io.micrometer.core.instrument.config.MeterFilterReply.NEUTRAL;
  if(!Set.of("nigson.integration.jobs","nigson.http.requests","nigson.http.errors","nigson.http.server.requests","nigson.db.query","nigson.db.pool.utilization","nigson.db.pool.waiting","nigson.db.pool.active","nigson.db.pool.timeouts","nigson.outbox.pending","nigson.outbox.oldest.age","nigson.outbox.publish.failures","nigson.outbox.published","nigson.outbox.failure","nigson.event.duplicate","nigson.idempotency.replay","nigson.idempotency.conflicts","nigson.audit.persisted").contains(id.getName()))return io.micrometer.core.instrument.config.MeterFilterReply.DENY;
  for(Tag t:id.getTags()){boolean valid=switch(t.getKey()){
   case "kind"->Set.of("SHEETS_SYNC","LOGISTICS_CREATE","PAYMENT_RECONCILE","ASSET_SCAN","REPORT_EXPORT").contains(t.getValue());
   case "state"->Set.of("PENDING","CLAIMED","SUCCEEDED","FAILED","UNKNOWN").contains(t.getValue());
   case "owner"->Set.of("commerce","experience","integration","ai").contains(t.getValue());
   case "route"->t.getValue().equals("UNKNOWN")||SafeRoutes.route(t.getValue()).equals(t.getValue());
   case "method"->SafeRoutes.method(t.getValue()).equals(t.getValue());
   case "status"->t.getValue().matches("[1-5][0-9]{2}");
   case "operation"->Set.of("SELECT","INSERT","UPDATE","DELETE","OTHER").contains(t.getValue());
   default->false;};if(!valid)return io.micrometer.core.instrument.config.MeterFilterReply.DENY;
  }return io.micrometer.core.instrument.config.MeterFilterReply.NEUTRAL;
 }
}
