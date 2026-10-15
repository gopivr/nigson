package com.nigson.integration.provider;

import java.time.Duration;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.*;
import com.nigson.shared.problem.*;

/** Durable claim first, bounded adapter HTTP second, fenced owned outcome last. */
public final class JobWorker {
 private static final Logger LOG=LoggerFactory.getLogger(JobWorker.class);private final JobExecution execution;private final Map<JobValues.Kind,JobHandler> handlers;private final int batch;
 public JobWorker(JobExecution execution,List<JobHandler> handlers,int batch){if(batch<1||batch>100||handlers.isEmpty()||handlers.size()>5)throw new IllegalArgumentException("APPROVED_JOB_HANDLERS_REQUIRED");this.execution=execution;this.batch=batch;var registry=new EnumMap<JobValues.Kind,JobHandler>(JobValues.Kind.class);for(var handler:handlers){if(handler==null||handler.kind()==null||handler.provider()==null||registry.putIfAbsent(handler.kind(),handler)!=null)throw new IllegalArgumentException("JOB_HANDLER_CONFLICT");if(handler.kind()==JobValues.Kind.LOGISTICS_CREATE)throw new IllegalArgumentException("CARRIER_OWNED_WORKER_REQUIRED");}this.handlers=Map.copyOf(registry);}
 @Scheduled(fixedDelayString="${NIGSON_PROVIDER_WORKER_DELAY_MS:30000}")public void scheduled(){poll();}
 public int poll(){
  long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();int inspected=0;
  // Claim one item at a time so budget exhaustion does not strand an untouched batch.
  for(int n=0;n<batch&&System.nanoTime()<deadline;n++){
   List<JobExecution.Claim> claims;try{claims=execution.claim(handlers.keySet(),1,Duration.ofSeconds(30));}catch(RuntimeException database){LOG.warn("JOB_CLAIM_RETRY_REQUIRED");break;}if(claims.isEmpty())break;var claim=claims.getFirst();var handler=handlers.get(claim.job().kind());
   try{var outcome=claim.uncertain()?handler.reconcile(claim):handler.execute(claim);execution.finish(claim,handler.provider(),outcome,UUID.randomUUID());}catch(RuntimeException unavailable){
    LOG.warn("JOB_OUTCOME_RECONCILIATION_REQUIRED");try{execution.finish(claim,handler.provider(),new JobHandler.Outcome(JobValues.State.UNKNOWN,null,ProblemCode.PROVIDER_OUTCOME_UNKNOWN),UUID.randomUUID());}catch(RuntimeException deferred){LOG.warn("JOB_DURABLE_LEASE_RECOVERY_REQUIRED");}
   }inspected++;
  }return inspected;
 }
}
