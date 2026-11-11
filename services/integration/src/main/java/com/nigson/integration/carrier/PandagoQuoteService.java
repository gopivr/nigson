package com.nigson.integration.carrier;

import java.time.*;
import java.util.*;
import com.nigson.integration.platform.*;
import com.nigson.integration.providers.PandagoAdapter;
import com.nigson.integration.providers.GeocodingProvider;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;
import tools.jackson.databind.JsonNode;

/** Side-effect-free PandaGo rate read. Order confirmation is a separate durable workflow. */
public final class PandagoQuoteService {
 private final GeocodingProvider geocoder;
 private final PandagoAdapter provider;private final PlatformStore store;private final AuditWriter audit;private final Clock clock;private final String approval;private final Instant approvedUntil;
 public PandagoQuoteService(PandagoAdapter provider,PlatformStore store,AuditWriter audit,Clock clock,String approval,Instant approvedUntil){this(provider,store,audit,clock,approval,approvedUntil,address->{throw new Problem.Failure(ProblemCode.GEOCODING_NOT_CONFIGURED);});
 }
 public PandagoQuoteService(PandagoAdapter provider,PlatformStore store,AuditWriter audit,Clock clock,String approval,Instant approvedUntil,GeocodingProvider geocoder){
  this.geocoder=Objects.requireNonNull(geocoder);
  if(provider==null||store==null||audit==null||clock==null||approval==null||!approval.matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,119}")||approvedUntil==null)throw new IllegalArgumentException("PANDAGO_APPROVAL_REQUIRED");
  this.provider=provider;this.store=store;this.audit=audit;this.clock=clock;this.approval=approval;this.approvedUntil=approvedUntil;
 }
 public String mode(){return provider.mode()==ProviderHttp.Mode.TEST?"SANDBOX":provider.mode().name();}
 public String accountHash(){return provider.accountFingerprint();}
 private void proof(PrivateWorkload.Proof proof){
  var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-182")).findFirst().orElseThrow(ActorAssertion::denied);
  if(proof==null||!route.equals(proof.route())||!route.client().equals(proof.client())||proof.subject()==null||!proof.subject().matches("[\\x21-\\x7e]{1,100}")||proof.actor()==null||!proof.actor().scopes().contains(route.scope()))throw ActorAssertion.denied();
 }
 private PandagoAdapter.Location location(CarrierValues.Address address){
  var point=address.latitude()!=null&&address.longitude()!=null
   ? new GeocodingProvider.Point(address.latitude(),address.longitude())
   : geocoder.resolve(new GeocodingProvider.Address(address.line1(),address.line2(),address.city(),address.region(),address.postalCode(),address.countryCode()));
  var lines=new ArrayList<String>();lines.add(address.line1());if(address.line2()!=null)lines.add(address.line2());lines.add(address.city());if(address.region()!=null)lines.add(address.region());if(address.postalCode()!=null)lines.add(address.postalCode());
  return new PandagoAdapter.Location(String.join(", ",lines),point.latitude(),point.longitude());
 }
 public CarrierValues.Quote quote(PrivateWorkload.Proof proof,CarrierValues.QuoteInput input,UUID correlation){
  proof(proof);if(input==null||input.shipper()!=null||input.serviceCode()!=null||correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();
  if(!approvedUntil.isAfter(clock.instant()))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);
  var destination=location(input.destination());var estimate=provider.estimate(input.commandId(),new PandagoAdapter.Recipient(input.destination().recipient(),input.destination().phone(),destination),new java.math.BigDecimal(input.orderAmount()),"Nigson checkout order "+input.cartId());
  var quote=provider.quote(destination);Instant latest=clock.instant().plusSeconds(300);Instant expires=quote.expiresAt().isBefore(approvedUntil)?quote.expiresAt():approvedUntil;if(latest.isBefore(expires))expires=latest;
  if(!expires.isAfter(clock.instant()))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);
  var result=new CarrierValues.Quote("PANDAGO","PANDAGO","NGN",quote.fee().setScale(2).toPlainString(),expires,quote.id(),
   estimate.fee().setScale(2).toPlainString(),estimate.estimatedPickupTime(),estimate.estimatedDeliveryTime(),estimate.deliveryDuration(),estimate.lowerBound(),estimate.upperBound());
  String principal="nigson-commerce:carrier:"+proof.actor().party();
  var response=store.executeLocal(principal,"API-182",input.commandId().toString(),EventCodec.JSON.valueToTree(Map.of("request",input,"mode",mode(),"approval",approval,"accountHash",accountHash())),db->{
   proof(proof);if(!approvedUntil.isAfter(clock.instant())||!result.expiresAt().isAfter(clock.instant()))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);
   audit.append(proof.actor().party().toString(),AuditValues.ActorKind.USER,AuditValues.Action.CHECKOUT_QUOTE,AuditValues.Resource.CART,input.cartId(),correlation,AuditValues.Reason.VERIFIED_PROVIDER,null,Map.of("amount",result.amount(),"currency","NGN","approval",approval),null);
   return new PlatformStore.Response(200,EventCodec.JSON.valueToTree(result),input.cartId());
  });
  return EventCodec.JSON.treeToValue(response.body(),CarrierValues.Quote.class);
 }
}
