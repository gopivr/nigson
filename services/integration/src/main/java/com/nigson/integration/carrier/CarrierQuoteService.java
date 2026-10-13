package com.nigson.integration.carrier;

import java.time.*;
import java.util.*;
import com.nigson.integration.providers.CarrierProvider;
import com.nigson.integration.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;

/** Rates are read remotely before the short local acknowledgement/audit transaction. */
public final class CarrierQuoteService {
 private final CarrierProvider provider;private final PlatformStore store;private final AuditWriter audit;private final Clock clock;
 private final Set<String> services;private final String defaultService,pickup,approval,mode;private final Instant approvedUntil;
 public CarrierQuoteService(CarrierProvider provider,PlatformStore store,AuditWriter audit,Clock clock,Set<String> services,String mode,String defaultService,String pickup,String approval,Instant approvedUntil){
  if(mode==null||provider==null||provider.mode()==null||!mode.equals(provider.mode()==ProviderHttp.Mode.TEST?"SANDBOX":provider.mode().name())||services==null||services.isEmpty()||services.size()>100||defaultService==null||!services.contains(defaultService)||approval==null||!approval.matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,119}")||approvedUntil==null||!Set.of("DROPOFF_AT_FEDEX_LOCATION","USE_SCHEDULED_PICKUP","CONTACT_FEDEX_TO_SCHEDULE").contains(pickup))throw new IllegalArgumentException("CARRIER_APPROVAL_REQUIRED");for(String service:services)CarrierValues.service(service);
  this.provider=Objects.requireNonNull(provider);this.store=Objects.requireNonNull(store);this.audit=Objects.requireNonNull(audit);this.clock=Objects.requireNonNull(clock);this.services=Set.copyOf(services);this.defaultService=defaultService;this.pickup=pickup;this.approval=approval;this.approvedUntil=approvedUntil;this.mode=mode;
 }
 public String mode(){return mode;}
 private void proof(PrivateWorkload.Proof proof){var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-182")).findFirst().orElseThrow(ActorAssertion::denied);if(proof==null||!route.equals(proof.route())||!route.client().equals(proof.client())||proof.subject()==null||!proof.subject().matches("[\\x21-\\x7e]{1,100}")||proof.actor()==null||!proof.actor().scopes().contains(route.scope()))throw ActorAssertion.denied();}
 public CarrierValues.Quote quote(PrivateWorkload.Proof proof,CarrierValues.QuoteInput input,UUID correlation){
  proof(proof);Objects.requireNonNull(input);if(correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();if(!approvedUntil.isAfter(clock.instant()))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);
  String service=input.serviceCode()==null?defaultService:input.serviceCode();if(!services.contains(service))throw CarrierValues.rule();Instant expires=clock.instant().plusSeconds(300);
  CarrierValues.Quote quote;try{quote=CarrierValues.rate(provider.rates(CarrierValues.rateRequest(input,service,pickup)),service,expires);}catch(ProviderHttp.Failure failure){throw new Problem.Failure(failure.status==400||failure.status==404?ProblemCode.PROVIDER_RULE_VIOLATION:ProblemCode.PROVIDER_UNAVAILABLE);}
  if(!expires.isAfter(clock.instant()))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);
  String principal="nigson-commerce:carrier:"+proof.actor().party();
  var response=store.executeLocal(principal,"API-182",input.commandId().toString(),EventCodec.JSON.valueToTree(Map.of("request",input,"mode",mode,"approval",approval)),db->{
   proof(proof);if(!approvedUntil.isAfter(clock.instant())||!quote.expiresAt().isAfter(clock.instant()))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);
   audit.append(proof.actor().party().toString(),AuditValues.ActorKind.USER,AuditValues.Action.CHECKOUT_QUOTE,AuditValues.Resource.CART,input.cartId(),correlation,AuditValues.Reason.VERIFIED_PROVIDER,null,Map.of("amount",quote.amount(),"currency","NGN","approval",approval),null);
   return new PlatformStore.Response(200,EventCodec.JSON.valueToTree(quote),input.cartId());
  });return EventCodec.JSON.treeToValue(response.body(),CarrierValues.Quote.class);
 }
}
