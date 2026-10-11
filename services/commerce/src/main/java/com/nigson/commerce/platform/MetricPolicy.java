package com.nigson.commerce.platform;
import com.nigson.shared.observability.SafeRoutes;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.*;
/** Reject unregistered custom tags and normalize framework HTTP labels. */
public final class MetricPolicy implements MeterFilter {
 public Meter.Id map(Meter.Id id){if(id.getName().equals("http.server.requests"))return id.replaceTags(id.getTags().stream().map(t->switch(t.getKey()){case "uri"->Tag.of("uri",SafeRoutes.route(t.getValue()));case "exception"->Tag.of("exception",t.getValue().equals("none")?"NONE":"ERROR");case "method"->Tag.of("method",SafeRoutes.method(t.getValue()));default->t;}).toList());return id;}
 public io.micrometer.core.instrument.config.MeterFilterReply accept(Meter.Id id){if(!id.getName().startsWith("nigson."))return io.micrometer.core.instrument.config.MeterFilterReply.NEUTRAL;
  if(!Set.of("nigson.http.requests","nigson.http.errors","nigson.http.server.requests","nigson.db.query","nigson.db.pool.utilization","nigson.db.pool.waiting","nigson.db.pool.active","nigson.db.pool.timeouts","nigson.outbox.pending","nigson.outbox.oldest.age","nigson.outbox.publish.failures","nigson.outbox.published","nigson.outbox.failure","nigson.event.duplicate","nigson.idempotency.replay","nigson.idempotency.conflicts","nigson.audit.persisted","nigson.security.login","nigson.security.logout","nigson.security.denied","nigson.provider.commands","nigson.files.transfer","nigson.notification.outcomes","nigson.notification.templates","nigson.inventory.movements","nigson.market.commands","nigson.pricing.commands","nigson.cart.commands","nigson.fulfillment.commands","nigson.order.commands","nigson.payment.outcomes","nigson.refund.outcomes","nigson.refund.commands","nigson.payment.receipts").contains(id.getName()))return io.micrometer.core.instrument.config.MeterFilterReply.DENY;
  for(Tag t:id.getTags()){boolean valid=switch(t.getKey()){
   case "owner"->Set.of("commerce","experience","integration","ai").contains(t.getValue());
   case "route"->t.getValue().equals("UNKNOWN")||SafeRoutes.route(t.getValue()).equals(t.getValue());
   case "method"->SafeRoutes.method(t.getValue()).equals(t.getValue());
   case "status"->t.getValue().matches("[1-5][0-9]{2}");
   case "operation"->id.getName().equals("nigson.refund.commands")?Set.of("REQUEST","APPROVAL").contains(t.getValue()):id.getName().equals("nigson.order.commands")?Set.of("CHECKOUT","PROCESS","CANCEL","SETTLE","HOLD").contains(t.getValue()):id.getName().equals("nigson.fulfillment.commands")?Set.of("RULE_CREATE","RULE_UPDATE","SHIPMENT","DISPATCH","DELIVERY","RETURN").contains(t.getValue()):id.getName().equals("nigson.cart.commands")?Set.of("CREATE","SET","REMOVE","TRANSITION").contains(t.getValue()):id.getName().equals("nigson.pricing.commands")?Set.of("CREATE","ACTIVATE","REVISE","QUOTE").contains(t.getValue()):id.getName().equals("nigson.market.commands")?Set.of("CREATE","BOOK","ELIGIBILITY").contains(t.getValue()):Set.of("SELECT","INSERT","UPDATE","DELETE","OTHER").contains(t.getValue());
   case "movement_type"->id.getName().equals("nigson.inventory.movements")&&Set.of("ADJUST","RESERVE","SETTLE","RELEASE","DISPATCH","RETURN").contains(t.getValue());
   case "state"->id.getName().equals("nigson.payment.receipts")?Set.of("RECEIVED","REJECTED","APPLIED","RETRY").contains(t.getValue()):id.getName().equals("nigson.refund.outcomes")?Set.of("SUBMITTING","PENDING","UNKNOWN","SUCCEEDED","FAILED").contains(t.getValue()):id.getName().equals("nigson.payment.outcomes")?Set.of("INITIATING","PENDING","UNKNOWN","SUCCEEDED","FAILED","CANCELLED").contains(t.getValue()):id.getName().equals("nigson.notification.outcomes")&&Set.of("SENT","FAILED","SUPPRESSED","UNKNOWN").contains(t.getValue());
   case "outcome"->switch(id.getName()){case "nigson.files.transfer"->Set.of("CLEAN","REJECTED","UNKNOWN").contains(t.getValue());case "nigson.provider.commands","nigson.notification.templates"->Set.of("ACKNOWLEDGED","UNKNOWN").contains(t.getValue());default->false;};
   default->false;};if(!valid)return io.micrometer.core.instrument.config.MeterFilterReply.DENY;
  }return io.micrometer.core.instrument.config.MeterFilterReply.NEUTRAL;
 }
}
