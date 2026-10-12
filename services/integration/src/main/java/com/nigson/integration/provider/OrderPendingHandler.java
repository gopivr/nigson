package com.nigson.integration.provider;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import com.nigson.integration.platform.EventHandler;
import com.nigson.shared.problem.*;

/** The inbox and stable reconciliation intent commit together; no provider HTTP here. */
public final class OrderPendingHandler implements EventHandler {
 private final JobRepository jobs;private final com.nigson.integration.platform.AuditWriter audit;public OrderPendingHandler(JobRepository jobs,com.nigson.integration.platform.AuditWriter audit){this.jobs=jobs;this.audit=audit;}
 public String eventType(){return "order.pending.v1";}
 public String handle(JdbcTemplate jdbc,JsonNode event){
  if(!event.path("event_type").asString().equals(eventType()))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);jdbc.execute("SET LOCAL lock_timeout='500ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");var payload=event.path("payload");UUID id=ApiValues.uuid(payload.path("order_id").asString());if(!event.path("producer").asString().equals("commerce")||!id.toString().equals(event.path("aggregate_id").asString())||event.path("aggregate_version").asLong()<0||!payload.isObject()||payload.size()!=3||!Set.of("order_id","party_id","market_code").containsAll(payload.propertyNames()))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);ApiValues.uuid(payload.path("party_id").asString());if(!payload.path("market_code").isString()||!payload.path("market_code").asString().equals("NG"))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);
  var saved=jobs.enqueueTracked(JobValues.Kind.PAYMENT_RECONCILE,"payment-reconcile:"+id,"commerce",id);if(saved.created())audit.append("integration-event-worker",AuditValues.ActorKind.SYSTEM,AuditValues.Action.QUEUE_REPLAY,AuditValues.Resource.QUEUE,saved.job().id(),ApiValues.uuid(event.path("correlation_id").asString()),AuditValues.Reason.RECONCILIATION,null,Map.of("state","PENDING","version",saved.job().version()),null);return "APPLIED";
 }
}
