package com.nigson.integration.carrier;

import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.integration.providers.CarrierProvider;

/** Bounded read-only provider polling with durable original source-command replay. */
public final class CarrierTrackingWorker {
 private final CarrierTrackingExecution execution;private final CarrierProvider provider;private final CarrierContextClient source;private final Clock clock;
 public CarrierTrackingWorker(CarrierTrackingExecution execution,CarrierProvider provider,CarrierContextClient source,Clock clock){this.execution=Objects.requireNonNull(execution);this.provider=Objects.requireNonNull(provider);this.source=Objects.requireNonNull(source);this.clock=Objects.requireNonNull(clock);}
 @Scheduled(fixedDelayString="${NIGSON_CARRIER_WORKER_DELAY_MS:30000}") public void scheduled(){try{poll();}catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger(getClass()).warn("CARRIER_TRACKING_RECOVERY_REQUIRED");}}
 public int poll(){var claim=execution.claim();return claim.isEmpty()?0:process(claim.get());}
 public int process(CarrierTrackingExecution.Claim initial){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");var active=initial;try{if(!active.compatible()||!active.checkpoint().path("accountHash").asString().equals(provider.accountFingerprint())||!active.mode().equals(provider.mode()==com.nigson.shared.security.ProviderHttp.Mode.TEST?"SANDBOX":provider.mode().name())){execution.failed(active);return 1;}if(!active.checkpoint().path("tracking").has("pending")){var numbers=new ArrayList<String>();for(var id:active.checkpoint().path("pieceTrackingNumbers"))numbers.add(id.asString());var scans=new ArrayList<CarrierTrackingValues.Scan>();for(int i=0;i<numbers.size();i+=30){var batch=List.copyOf(numbers.subList(i,Math.min(i+30,numbers.size())));scans.addAll(CarrierTrackingValues.parse(provider.track(batch),batch,clock.instant()));}var prepared=execution.prepare(active,CarrierTrackingValues.aggregate(scans,numbers.size()));if(prepared.isEmpty())return 1;active=prepared.get();}var body=active.checkpoint().path("tracking").path("pending");var response=source.acknowledge(active.shipmentId(),body,active.correlation());execution.acknowledged(active,response);return 1;}catch(RuntimeException unavailable){try{execution.failed(active);}catch(RuntimeException durable){org.slf4j.LoggerFactory.getLogger(getClass()).warn("CARRIER_TRACKING_LEASE_RECOVERY_REQUIRED");}return 1;}}
}
