package com.nigson.integration.notification;
import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.integration.providers.TwilioAdapter;
import com.nigson.shared.problem.*;
/** One fenced WhatsApp create; API-205 suppression is read after the durable fence and immediately before provider HTTP. */
public final class StaffReplyWorker {
 private final StaffReplyExecution execution;private final StaffReplySource source;private final StaffReplyMessage messages;private final StaffReplyCrypto crypto;private final TwilioAdapter provider;private final Clock clock;
 public StaffReplyWorker(StaffReplyExecution execution,StaffReplySource source,StaffReplyMessage messages,StaffReplyCrypto crypto,TwilioAdapter provider,Clock clock){this.execution=Objects.requireNonNull(execution);this.source=Objects.requireNonNull(source);this.messages=Objects.requireNonNull(messages);this.crypto=Objects.requireNonNull(crypto);this.provider=Objects.requireNonNull(provider);this.clock=Objects.requireNonNull(clock);}
 @Scheduled(fixedDelayString="${NIGSON_MESSAGING_STAFF_REPLY_WORKER_DELAY_MS:30000}") public void scheduled(){try{poll();}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("STAFF_REPLY_DURABLE_RECOVERY_REQUIRED");}}
 public int poll(){outside();var claim=execution.claim();return claim.isEmpty()?0:process(claim.get());}
 public int process(StaffReplyExecution.Claim claim){outside();StaffReplyExecution.Claim active=claim;try{if(!claim.compatible()){execution.unknown(claim,"PROVIDER_MODE_MISMATCH");return 1;}if(!claim.maySend()){if(claim.sid()==null){execution.unknown(claim,"PROVIDER_OUTCOME_UNKNOWN");return 1;}long start=System.nanoTime();execution.outcome(claim,provider.status(claim.sid()),elapsed(start));return 1;}var request=messages.build(claim);var prepared=execution.prepare(claim,request.hash());if(prepared.isEmpty())return 1;active=prepared.get();try{if(source.suppressed(active.destinationHash(),"WHATSAPP",active.correlation())){execution.suppressed(active);return 1;}}catch(RuntimeException deniedOrUnavailable){execution.beforeWriteFailed(active);return 1;}long start=System.nanoTime();var response=provider.whatsapp(request.phone(),request.contentSid(),request.variables(),request.callback());execution.outcome(active,response,elapsed(start));return 1;}catch(RuntimeException failure){try{execution.unknown(active,"PROVIDER_OUTCOME_UNKNOWN");}catch(RuntimeException persist){org.slf4j.LoggerFactory.getLogger(getClass()).warn("STAFF_REPLY_LEASE_RECOVERY_REQUIRED");}return 1;}}
 private static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");}private static int elapsed(long start){return(int)Math.min(Integer.MAX_VALUE,Math.max(0,(System.nanoTime()-start)/1_000_000));}
}
