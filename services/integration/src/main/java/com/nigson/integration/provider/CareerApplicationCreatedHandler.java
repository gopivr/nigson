package com.nigson.integration.provider;

import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import tools.jackson.databind.JsonNode;
import com.nigson.integration.platform.*;import com.nigson.shared.problem.*;

/** EVT-024 creates one durable, owner-local Sheet job; event intake performs no remote work. */
public final class CareerApplicationCreatedHandler implements EventHandler {
 private final JobRepository jobs;private final AuditWriter audit;private final String recipientGroup;
 public CareerApplicationCreatedHandler(JobRepository jobs,AuditWriter audit,String recipientGroup){if(recipientGroup==null||!recipientGroup.matches("[A-Za-z0-9._:-]{1,80}"))throw new IllegalArgumentException("CAREER_SHEET_RECIPIENT_GROUP_REQUIRED");this.jobs=jobs;this.audit=audit;this.recipientGroup=recipientGroup;}
 public String eventType(){return "career.application.created.v1";}
 public String handle(JdbcTemplate jdbc,JsonNode event){if(!event.path("event_type").isString()||!event.path("event_type").asString().equals(eventType())||!event.path("producer").isString()||!event.path("producer").asString().equals("experience"))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);var p=event.path("payload");if(!p.isObject()||!p.propertyNames().equals(Set.of("application_id","opportunity_id","recipient_group")))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);UUID app=ApiValues.uuid(p.path("application_id").asString());ApiValues.uuid(p.path("opportunity_id").asString());if(!app.toString().equals(event.path("aggregate_id").asString())||!p.path("recipient_group").isString()||!recipientGroup.equals(p.path("recipient_group").asString())||event.path("aggregate_version").asLong()!=0)throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);var saved=jobs.enqueueTracked(JobValues.Kind.SHEETS_SYNC,"career-sheet:"+app+":"+recipientGroup,"experience",app);if(saved.created())audit.append("integration-career-sheet-intake",AuditValues.ActorKind.SYSTEM,AuditValues.Action.QUEUE_REPLAY,AuditValues.Resource.QUEUE,saved.job().id(),ApiValues.uuid(event.path("correlation_id").asString()),AuditValues.Reason.RECONCILIATION,null,Map.of("kind","SHEETS_SYNC","state","PENDING"),null);return "APPLIED";}
}
