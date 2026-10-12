package com.nigson.integration.provider;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;

public final class JdbcJobRepository implements JobRepository {
 private final JdbcTemplate jdbc;private final Clock clock;
 public JdbcJobRepository(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
 private void transaction(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");}
 private static java.sql.Timestamp stamp(Instant value){return java.sql.Timestamp.from(value);}
 private Record row(java.sql.ResultSet r,int ignored)throws java.sql.SQLException{
  var lease=r.getTimestamp("lease_until");return new Record(r.getObject("id",UUID.class),r.getLong("version"),JobValues.Kind.valueOf(r.getString("kind")),r.getString("business_key"),r.getString("owner_service"),r.getObject("resource_id",UUID.class),JobValues.State.valueOf(r.getString("state")),r.getInt("attempt_count"),r.getTimestamp("next_attempt_at").toInstant(),lease==null?null:lease.toInstant(),r.getString("error_code"));
 }
 public JobValues.Page list(JobValues.Query query){
  transaction();
  String filter=" WHERE (?::text IS NULL OR kind=?::text) AND (?::text IS NULL OR state=?::text)";
  String kind=query.kind()==null?null:query.kind().name(),state=query.state()==null?null:query.state().name();
  String order=switch(query.sort()){case "id,asc"->"id ASC";case "id,desc"->"id DESC";case "nextAttemptAt,asc"->"next_attempt_at ASC,id ASC";case "nextAttemptAt,desc"->"next_attempt_at DESC,id DESC";default->throw ApiValues.invalid();};
  long total=jdbc.queryForObject("SELECT count(*) FROM integration.integration_job"+filter,Long.class,kind,kind,state,state);
  var items=jdbc.query("SELECT * FROM integration.integration_job"+filter+" ORDER BY "+order+" LIMIT ? OFFSET ?",this::row,kind,kind,state,state,query.size(),(long)query.page()*query.size()).stream().map(Record::view).toList();
  return new JobValues.Page(items,query.page(),query.size(),total,total/query.size()+(total%query.size()==0?0:1));
 }
 public Record find(UUID id,boolean lock){if(lock)transaction();var rows=jdbc.query("SELECT * FROM integration.integration_job WHERE id=?"+(lock?" FOR UPDATE":""),this::row,id);if(rows.isEmpty())throw new Problem.Failure(ProblemCode.NOT_FOUND);return rows.getFirst();}
 public Record enqueue(JobValues.Kind kind,String businessKey,String owner,UUID resource){return enqueueTracked(kind,businessKey,owner,resource).job();}
 public Created enqueueTracked(JobValues.Kind kind,String businessKey,String owner,UUID resource){
  transaction();if(kind==null||businessKey==null||!businessKey.matches("[A-Za-z0-9:._-]{1,180}")||!Set.of("commerce","experience","integration").contains(owner)||resource==null||resource.equals(new UUID(0,0)))throw ApiValues.invalid();
  UUID id=EventCodec.uuid7(clock.instant());int inserted=jdbc.update("INSERT INTO integration.integration_job(id,created_by,updated_by,kind,business_key,owner_service,resource_id,state,next_attempt_at) VALUES (?,'job-owner','job-owner',?,?,?,?,'PENDING',?) ON CONFLICT(business_key) DO NOTHING",id,kind.name(),businessKey,owner,resource,stamp(clock.instant()));
  var saved=jdbc.queryForObject("SELECT * FROM integration.integration_job WHERE business_key=? FOR UPDATE",this::row,businessKey);
  if(saved.kind()!=kind||!saved.owner().equals(owner)||!saved.resource().equals(resource))throw new Problem.Failure(ProblemCode.IDEMPOTENCY_HASH_MISMATCH);return new Created(saved,inserted==1);
 }
 public Record retry(UUID id,long expected,String actor){
  transaction();AuditValues.subject(actor);var saved=find(id,true);if(saved.version()!=expected)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
  if(saved.state()==JobValues.State.UNKNOWN)throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);
  if(saved.state()!=JobValues.State.FAILED||saved.lease()!=null)throw new Problem.Failure(ProblemCode.INVALID_TRANSITION);
  jdbc.update("UPDATE integration.integration_job SET state='PENDING',next_attempt_at=?,lease_until=NULL,error_code=NULL,version=version+1,updated_by=?,updated_at=? WHERE id=? AND version=?",stamp(clock.instant()),actor,stamp(clock.instant()),id,expected);return find(id,false);
 }
}
