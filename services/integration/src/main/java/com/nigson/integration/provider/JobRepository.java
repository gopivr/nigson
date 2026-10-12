package com.nigson.integration.provider;
import java.time.Instant;
import java.util.*;

/** Integration owns all execution state; callers never supply another owner's JDBC objects. */
public interface JobRepository {
 record Record(UUID id,long version,JobValues.Kind kind,String businessKey,String owner,UUID resource,JobValues.State state,int attempts,Instant nextAttempt,Instant lease,String error){
  public JobValues.Job view(){String safe=error;if(safe!=null)try{com.nigson.shared.problem.ProblemCode.valueOf(safe);}catch(IllegalArgumentException unknown){safe="PROVIDER_RULE_VIOLATION";}return new JobValues.Job(id,state.name(),attempts,nextAttempt,safe);}
  @Override public String toString(){return "IntegrationJob[protected]";}
 }
 record Created(Record job,boolean created){}
 Created enqueueTracked(JobValues.Kind kind,String businessKey,String owner,UUID resource);
 JobValues.Page list(JobValues.Query query);
 Record find(UUID id,boolean lock);
 Record enqueue(JobValues.Kind kind,String businessKey,String owner,UUID resource);
 Record retry(UUID id,long expected,String actor);
}
