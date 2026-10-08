package com.nigson.integration.platform;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.*;
/** Owned append-only evidence. A write failure aborts the owner's mutation transaction. */
public final class AuditWriter {
 private final JdbcTemplate jdbc;private final Clock clock;private final MeterRegistry metrics;
 public AuditWriter(JdbcTemplate jdbc,Clock clock,MeterRegistry metrics){this.jdbc=jdbc;this.clock=clock;this.metrics=metrics;}
 public UUID append(String subject,AuditValues.ActorKind kind,AuditValues.Action action,AuditValues.Resource resource,UUID resourceId,UUID correlation,AuditValues.Reason reason,Map<String,?> oldValues,Map<String,?> newValues,String ipHash){
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");
  AuditValues.subject(subject);Objects.requireNonNull(kind);Objects.requireNonNull(action);Objects.requireNonNull(resource);
  if(resourceId==null||correlation==null||resourceId.equals(new UUID(0,0))||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();
  if(ipHash!=null&&!ipHash.matches("[a-f0-9]{64}"))throw ApiValues.invalid();
  var old=AuditValues.redact(oldValues);var updated=AuditValues.redact(newValues);UUID id=EventCodec.uuid7(clock.instant());
  jdbc.update("INSERT INTO integration.audit_record(id,created_by,actor_subject,actor_kind,action,resource_type,resource_id,occurred_at,correlation_id,ip_hash,reason,old_value,new_value) VALUES (?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb)",id,subject,subject,kind.name(),action.name(),resource.name(),resourceId,java.sql.Timestamp.from(clock.instant()),correlation,ipHash,reason==null?null:reason.name(),old==null?null:EventCodec.JSON.writeValueAsString(old),updated==null?null:EventCodec.JSON.writeValueAsString(updated));
  org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){public void afterCommit(){metrics.counter("nigson.audit.persisted","owner","integration").increment();}});return id;
 }
}
