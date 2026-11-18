package com.nigson.integration.carrier;

import com.nigson.integration.providers.*;
import com.nigson.integration.platform.*;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;
import com.nigson.shared.events.EventCodec;
import java.util.*;import java.time.*;import java.math.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PandagoAutomaticAddressTest {
 private final PandagoAdapter provider=mock(PandagoAdapter.class);
 private final GeocodingProvider geocoder=mock(GeocodingProvider.class);
 private final PlatformStore store=mock(PlatformStore.class);
 private final Clock clock=Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"),ZoneOffset.UTC);
 private final UUID party=UUID.randomUUID();
 private PrivateWorkload.Proof proof(){var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-182")).findFirst().orElseThrow();return new PrivateWorkload.Proof(route.client(),"test-service",route,new ActorAssertion.Actor(party,"http://localhost/realms/test","test-user",null,Set.of("CUSTOMER"),Set.of(route.scope()),null));}
 private CarrierValues.QuoteInput input(BigDecimal lat,BigDecimal lng){return new CarrierValues.QuoteInput(UUID.randomUUID(),UUID.randomUUID(),"NG","NGN",null,new CarrierValues.Address("Test Customer","+2348000000001","12 Test street",null,"Lagos","Lagos",null,"NG",lat,lng),List.of(new CarrierValues.Parcel(UUID.randomUUID(),"1","1","10","10","10")),null,"25.00");}
 private CarrierValues.QuoteInput scheduledInput(Instant scheduled){var base=input(new BigDecimal("6.5"),new BigDecimal("3.4"));return new CarrierValues.QuoteInput(base.commandId(),base.cartId(),base.market(),base.currency(),base.shipper(),base.destination(),base.packages(),base.serviceCode(),base.orderAmount(),"SCHEDULED",scheduled);}
 private PandagoQuoteService service(){return new PandagoQuoteService(provider,store,mock(AuditWriter.class),clock,"test-approval",clock.instant().plusSeconds(600),geocoder);}
 private void providerReady(){
  when(provider.mode()).thenReturn(ProviderHttp.Mode.TEST);when(provider.accountFingerprint()).thenReturn("1".repeat(64));
  when(provider.estimate(any(),any(),any(),any())).thenReturn(new PandagoAdapter.Estimate(new BigDecimal("26.00"),"NGN",clock.instant().plusSeconds(60),clock.instant().plusSeconds(600),"PT9M","PT8M","PT10M"));
  when(provider.quote(any(),any())).thenReturn(new PandagoAdapter.Quote("test-quote",new BigDecimal("26.00"),"NGN",clock.instant().plusSeconds(300),"1".repeat(64)));
  var quote=new CarrierValues.Quote("PANDAGO","PANDAGO","NGN","26.00",clock.instant().plusSeconds(300),"test-quote","26.00",clock.instant().plusSeconds(60),clock.instant().plusSeconds(600),"PT9M","PT8M","PT10M");
  when(store.executeLocal(any(),any(),any(),any(),any())).thenReturn(new PlatformStore.Response(200,EventCodec.JSON.valueToTree(quote),UUID.randomUUID()));
 }
 @Test void quotesPostalAddressUsingCoordinatesFromActualGeocoder(){
  providerReady();when(geocoder.resolve(any())).thenReturn(new GeocodingProvider.Point(new BigDecimal("6.5"),new BigDecimal("3.4")));
  assertThat(service().quote(proof(),input(null,null),UUID.randomUUID()).amount()).isEqualTo("26.00");
  verify(geocoder).resolve(new GeocodingProvider.Address("12 Test street",null,"Lagos","Lagos",null,"NG"));
  verify(provider).quote(argThat(location->location.latitude().compareTo(new BigDecimal("6.5"))==0&&location.longitude().compareTo(new BigDecimal("3.4"))==0),isNull());
 }
 @Test void retainsExistingSavedCoordinatesWithoutAnotherLookup(){providerReady();service().quote(proof(),input(new BigDecimal("6.5"),new BigDecimal("3.4")),UUID.randomUUID());verifyNoInteractions(geocoder);}
 @Test void scheduledDeliveryPassesThePreorderedTimeToTheProvider(){
  providerReady();Instant scheduled=clock.instant().plusSeconds(3600);
  service().quote(proof(),scheduledInput(scheduled),UUID.randomUUID());
  verify(provider).quote(any(),eq(scheduled));
 }
 @Test void scheduledDeliveryOutsideTheProviderWindowFailsClosed(){
  providerReady();Instant tooSoon=clock.instant().plusSeconds(44*60);
  assertThatThrownBy(()->service().quote(proof(),scheduledInput(tooSoon),UUID.randomUUID())).isInstanceOfSatisfying(Problem.Failure.class,e->assertThat(e.code).isEqualTo(ProblemCode.PROVIDER_RULE_VIOLATION));
  verify(provider,never()).quote(any(),any());verifyNoInteractions(store);
 }
 @Test void failedAddressLookupNeverCallsCarrierOrPersistsAQuote(){when(geocoder.resolve(any())).thenThrow(new Problem.Failure(ProblemCode.ADDRESS_NOT_LOCATED));assertThatThrownBy(()->service().quote(proof(),input(null,null),UUID.randomUUID())).isInstanceOfSatisfying(Problem.Failure.class,e->assertThat(e.code).isEqualTo(ProblemCode.ADDRESS_NOT_LOCATED));verifyNoInteractions(provider,store);}
 @Test void providerFeeRejectionIsARuleOutcomeAndIsNotPersisted(){
  when(provider.estimate(any(),any(),any(),any())).thenThrow(new ProviderHttp.Failure("PROVIDER_HTTP_422",false,422));
  assertThatThrownBy(()->service().quote(proof(),input(new BigDecimal("6.5"),new BigDecimal("3.4")),UUID.randomUUID())).isInstanceOfSatisfying(Problem.Failure.class,e->assertThat(e.code).isEqualTo(ProblemCode.PROVIDER_RULE_VIOLATION));
  verifyNoInteractions(store);
 }
 @Test void providerQuoteRejectionIsARuleOutcomeAndIsNotPersisted(){
  when(provider.estimate(any(),any(),any(),any())).thenReturn(new PandagoAdapter.Estimate(new BigDecimal("26.00"),"NGN",clock.instant().plusSeconds(60),clock.instant().plusSeconds(600),"PT9M","PT8M","PT10M"));
  when(provider.quote(any(),any())).thenThrow(new ProviderHttp.Failure("PROVIDER_HTTP_422",false,422));
  assertThatThrownBy(()->service().quote(proof(),input(new BigDecimal("6.5"),new BigDecimal("3.4")),UUID.randomUUID())).isInstanceOfSatisfying(Problem.Failure.class,e->assertThat(e.code).isEqualTo(ProblemCode.PROVIDER_RULE_VIOLATION));
  verifyNoInteractions(store);
 }
 @Test void providerOutageIsNotReclassifiedAsARuleOutcome(){
  when(provider.estimate(any(),any(),any(),any())).thenThrow(new ProviderHttp.Failure("PROVIDER_HTTP_503",true,503));
  assertThatThrownBy(()->service().quote(proof(),input(new BigDecimal("6.5"),new BigDecimal("3.4")),UUID.randomUUID())).isInstanceOf(ProviderHttp.Failure.class);
  verifyNoInteractions(store);
 }
}
