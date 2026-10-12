package com.nigson.integration.provider;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.integration.platform.AuditWriter;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;

/** Leases fence local outcomes; expired external execution always becomes UNKNOWN. */
public final class JobExecution {
 public record Claim(JobRepository.Record job,long generation,boolean uncertain,String nextCursor){
  public String commandKey(){return "pj-"+job.id().toString().replace("-","")+"-"+EventCodec.hash(EventCodec.JSON.valueToTree(Map.of("cursor",nextCursor==null?"":nextCursor)));}
  @Override public String toString(){return "JobClaim[protected]";}
 }
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final JobRepository repository;private final AuditWriter audit;private final Clock clock;private final io.micrometer.core.instrument.MeterRegistry metrics;
 public JobExecution(JdbcTemplate jdbc,PlatformTransactionManager manager,JobRepository repository,AuditWriter audit,Clock clock,io.micrometer.core.instrument.MeterRegistry metrics){this.jdbc=jdbc;this.repository=repository;this.audit=audit;this.clock=clock;this.metrics=Objects.requireNonNull(metrics);tx=new TransactionTemplate(manager);tx.setTimeout(5);}
 private java.sql.Timestamp now(){return java.sql.Timestamp.from(clock.instant());}
 private void audit(JobRepository.Record before,JobRepository.Record after,UUID correlation){audit.append("integration-job-worker",AuditValues.ActorKind.SYSTEM,AuditValues.Action.QUEUE_REPLAY,AuditValues.Resource.QUEUE,before.id(),correlation,AuditValues.Reason.RECONCILIATION,Map.of("state",before.state().name(),"version",before.version()),Map.of("state",after.state().name(),"version",after.version()),null);org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){public void afterCommit(){metrics.counter("nigson.integration.jobs","owner","integration","kind",after.kind().name(),"state",after.state().name()).increment();}});}
 private String cursor(UUID id){
  var value=EventCodec.JSON.readTree(jdbc.queryForObject("SELECT cursor::text FROM integration.integration_job WHERE id=?",String.class,id));
  if(!value.isObject()||value.size()>1||!Set.of("nextCursor").containsAll(value.propertyNames()))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);
  var next=value.path("nextCursor");if(next.isMissingNode()||next.isNull())return null;if(!next.isString()||!next.asString().matches("[A-Za-z0-9_-]{1,256}"))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);return next.asString();
 }
 public List<Claim> claim(Set<JobValues.Kind> kinds,int limit,Duration lease){
  if(kinds==null||kinds.isEmpty()||kinds.size()>5||kinds.stream().anyMatch(Objects::isNull)||limit<1||limit>100||lease==null||lease.toSeconds()<30||lease.toSeconds()>120)throw ApiValues.invalid();
  String slots=String.join(",",Collections.nCopies(kinds.size(),"?"));List<Object> parameters=new ArrayList<>(kinds.stream().map(Enum::name).sorted().toList());
  return tx.execute(status->{
   jdbc.execute("SET LOCAL lock_timeout='350ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");var expired=new ArrayList<Object>(parameters);expired.add(now());expired.add(limit);
   var stale=jdbc.query("SELECT id FROM integration.integration_job WHERE kind IN ("+slots+") AND state='CLAIMED' AND lease_until<=? ORDER BY lease_until,id FOR UPDATE SKIP LOCKED LIMIT ?",(r,n)->r.getObject(1,UUID.class),expired.toArray());
   for(UUID id:stale){var before=repository.find(id,false);jdbc.update("UPDATE integration.integration_job SET state='UNKNOWN',lease_until=NULL,error_code='PROVIDER_OUTCOME_UNKNOWN',next_attempt_at=?,version=version+1,updated_at=?,updated_by='integration-job-worker' WHERE id=?",now(),now(),id);audit(before,repository.find(id,false),UUID.randomUUID());}
   var due=new ArrayList<Object>(parameters);due.add(now());due.add(limit);var ids=jdbc.query("SELECT id FROM integration.integration_job WHERE kind IN ("+slots+") AND state IN ('PENDING','UNKNOWN') AND next_attempt_at<=? ORDER BY next_attempt_at,id FOR UPDATE SKIP LOCKED LIMIT ?",(r,n)->r.getObject(1,UUID.class),due.toArray());var result=new ArrayList<Claim>();
   for(UUID id:ids){var before=repository.find(id,false);String next=cursor(id);jdbc.update("UPDATE integration.integration_job SET state='CLAIMED',lease_until=?,attempt_count=attempt_count+1,version=version+1,updated_at=?,updated_by='integration-job-worker' WHERE id=?",java.sql.Timestamp.from(clock.instant().plus(lease)),now(),id);var after=repository.find(id,false);audit(before,after,UUID.randomUUID());result.add(new Claim(after,after.version(),before.state()==JobValues.State.UNKNOWN,next));}
   return List.copyOf(result);
  });
 }
 public boolean finish(Claim claim,JobHandler.Provider provider,JobHandler.Outcome outcome,UUID correlation){
  Objects.requireNonNull(claim);Objects.requireNonNull(provider);Objects.requireNonNull(outcome);JobHandler.Provider expected=switch(claim.job().kind()){case PAYMENT_RECONCILE->JobHandler.Provider.COMMERCE_PAYMENT;case LOGISTICS_CREATE->JobHandler.Provider.FEDEX;case SHEETS_SYNC->JobHandler.Provider.GOOGLE_SHEETS;case ASSET_SCAN->JobHandler.Provider.S3;case REPORT_EXPORT->JobHandler.Provider.COMMERCE_REPORTS;};if(provider!=expected)throw new Problem.Failure(ProblemCode.PROVIDER_MODE_MISMATCH);if(correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();
  return tx.execute(status->{
   var before=repository.find(claim.job().id(),true);if(before.version()!=claim.generation()||before.state()!=JobValues.State.CLAIMED)return false;
   if(!before.businessKey().equals(claim.job().businessKey())||before.kind()!=claim.job().kind()||!before.resource().equals(claim.job().resource()))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);
   boolean acknowledged=outcome.state()==JobValues.State.PENDING||outcome.state()==JobValues.State.SUCCEEDED;
   String saved=EventCodec.JSON.writeValueAsString(Map.of("nextCursor",outcome.nextCursor()==null?"":outcome.nextCursor()));
   // Null cursor is explicit only after a completed page; UNKNOWN/FAILED retain the original input.
   if(outcome.state()==JobValues.State.SUCCEEDED)saved="{\"nextCursor\":null}";
   jdbc.update("UPDATE integration.integration_job SET state=?,lease_until=NULL,next_attempt_at=?,error_code=?,cursor=CASE WHEN ? THEN ?::jsonb ELSE cursor END,version=version+1,updated_at=?,updated_by='integration-job-worker' WHERE id=?",outcome.state().name(),java.sql.Timestamp.from(clock.instant().plusSeconds(outcome.state()==JobValues.State.UNKNOWN?30:0)),outcome.error()==null?null:outcome.error().name(),acknowledged,saved,now(),before.id());
   if(acknowledged){var checkpoint=EventCodec.JSON.createObjectNode().put("jobId",before.id().toString()).put("completed",outcome.state()==JobValues.State.SUCCEEDED);if(outcome.nextCursor()==null)checkpoint.putNull("nextCursor");else checkpoint.put("nextCursor",outcome.nextCursor());
    jdbc.update("INSERT INTO integration.provider_cursor(id,created_by,updated_by,provider,job_kind,checkpoint,last_success_at) VALUES (?,'integration-job-worker','integration-job-worker',?,?,?::jsonb,?) ON CONFLICT(provider,job_kind) DO UPDATE SET checkpoint=EXCLUDED.checkpoint,last_success_at=EXCLUDED.last_success_at,version=integration.provider_cursor.version+1,updated_at=EXCLUDED.last_success_at,updated_by='integration-job-worker'",EventCodec.uuid7(clock.instant()),provider.name(),before.kind().name(),EventCodec.JSON.writeValueAsString(checkpoint),now());
   }
   audit(before,repository.find(before.id(),false),correlation);return true;
  });
 }
}
