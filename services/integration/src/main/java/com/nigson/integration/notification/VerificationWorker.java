package com.nigson.integration.notification;
import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.integration.providers.*;
import com.nigson.shared.problem.*;
/** Bounded dispatch: source read, durable fence, unlocked single HTTP write, durable outcome. */
public final class VerificationWorker {
 private final VerificationExecution execution;private final VerificationContextClient source;private final VerificationMessage messages;private final SmsProvider sms;private final WhatsAppProvider whatsapp;private final Clock clock;private final Instant approvedUntil;
 public VerificationWorker(VerificationExecution execution,VerificationContextClient source,VerificationMessage messages,SmsProvider sms,WhatsAppProvider whatsapp,Clock clock,Instant approvedUntil){this.execution=Objects.requireNonNull(execution);this.source=Objects.requireNonNull(source);this.messages=Objects.requireNonNull(messages);this.sms=Objects.requireNonNull(sms);this.whatsapp=Objects.requireNonNull(whatsapp);this.clock=Objects.requireNonNull(clock);this.approvedUntil=Objects.requireNonNull(approvedUntil);if(!execution.matches(sms.mode(),sms.accountFingerprint())||sms.mode()!=whatsapp.mode()||!sms.accountFingerprint().equals(whatsapp.accountFingerprint()))throw new IllegalArgumentException("MESSAGE_CHANNEL_IDENTITY_MISMATCH");}
 @Scheduled(fixedDelayString="${NIGSON_MESSAGING_WORKER_DELAY_MS:30000}") public void scheduled(){try{poll();}catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger(getClass()).warn("MESSAGE_DURABLE_RECOVERY_REQUIRED");}}
 public int poll(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");var claim=execution.claim();return claim.isEmpty()?0:process(claim.get());}
 public int process(VerificationExecution.Claim claim){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");var active=claim;
  try{if(!claim.compatible()){execution.unknown(claim,"PROVIDER_MODE_MISMATCH");return 1;}if(!claim.maySend()){if(claim.sid()==null){execution.unknown(claim,"PROVIDER_OUTCOME_UNKNOWN");return 1;}long start=System.nanoTime();var result=claim.channel().equals("SMS")?sms.status(claim.sid()):whatsapp.status(claim.sid());execution.outcome(claim,result,elapsed(start));return 1;}
   VerificationContextClient.Context context;try{context=source.read(claim.verification(),claim.id(),claim.channel(),claim.correlation());}catch(Problem.Failure denied){execution.beforeSendFailed(claim,denied.code!=ProblemCode.PROVIDER_UNAVAILABLE);return 1;}
   var request=messages.build(claim.id(),sms.accountFingerprint(),claim.template(),context);var prepared=execution.prepare(claim,request,approvedUntil);if(prepared.isEmpty())return 1;active=prepared.get();if(!request.expiresAt().isAfter(clock.instant().plusSeconds(9))||!approvedUntil.isAfter(clock.instant().plusSeconds(9))||!active.lease().isAfter(clock.instant().plusSeconds(9)))throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);long start=System.nanoTime();var result=request.channel().equals("SMS")?sms.sms(request.phone(),request.body(),request.callback()):whatsapp.whatsapp(request.phone(),request.contentSid(),request.variables(),request.callback());execution.outcome(active,result,elapsed(start));return 1;
  }catch(RuntimeException failure){try{execution.unknown(active,"PROVIDER_OUTCOME_UNKNOWN");}catch(RuntimeException persist){org.slf4j.LoggerFactory.getLogger(getClass()).warn("MESSAGE_DURABLE_LEASE_RECOVERY_REQUIRED");}return 1;}
 }
 private static int elapsed(long start){return (int)Math.min(Integer.MAX_VALUE,Math.max(0,(System.nanoTime()-start)/1_000_000));}
}
