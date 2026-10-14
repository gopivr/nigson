package com.nigson.integration.carrier;

import java.math.BigDecimal;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ProviderHttp;
import com.nigson.integration.providers.FedExAdapter;
import static org.assertj.core.api.Assertions.*;

class CarrierShipmentRequestTest {
 private final Clock clock=Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"),ZoneOffset.UTC);private final LocalDate date=LocalDate.of(2026,10,14);
 private CarrierShipmentRequest builder(String stock,String billing,Instant until){return new CarrierShipmentRequest(Set.of("SYNTHETIC_SERVICE"),"DROPOFF_AT_FEDEX_LOCATION",stock,billing,"synthetic-offline-approval",until,clock);}
 private CarrierShipmentRequest builder(){return builder("PAPER_4X6","SENDER",clock.instant().plusSeconds(600));}
 private CarrierCreationContext context(int count){var wire=CarrierContextClientTest.context();var value=CarrierCreationContext.parse(wire,UUID.fromString(wire.path("shipmentId").asString()),UUID.fromString(wire.path("bookingReference").asString()),ProviderHttp.Mode.MOCK);return new CarrierCreationContext(value.shipmentId(),value.orderId(),value.destination(),value.shipper(),value.serviceCode(),Collections.nCopies(count,value.packages().getFirst()),value.bookingReference(),"MOCK");}
 @ParameterizedTest @ValueSource(ints={1,40,41,100})
 void realPackageCountChoosesAsyncAndReferenceBindsEveryCarton(int count){var context=context(count);var request=builder().build(context,date);assertThat(request.has("accountNumber")).isFalse();assertThat(request.path("labelResponseOptions").asString()).isEqualTo("LABEL");assertThat(request.path("processingOptionType").asString()).isEqualTo(count>40?"ALLOW_ASYNCHRONOUS":"SYNCHRONOUS_ONLY");var shipment=request.path("requestedShipment");assertThat(shipment.path("totalPackageCount").asInt()).isEqualTo(count);assertThat(shipment.path("requestedPackageLineItems")).hasSize(count);assertThat(shipment.path("totalWeight").decimalValue()).isEqualByComparingTo(new BigDecimal("1.201").multiply(BigDecimal.valueOf(count)).setScale(1,java.math.RoundingMode.CEILING));assertThat(shipment.path("recipients").path(0).path("contact").path("phoneNumber").asString()).isEqualTo("2348000000001");assertThat(shipment.path("shipDatestamp").asString()).isEqualTo("2026-10-14");assertThat(shipment.path("labelSpecification").path("imageType").asString()).isEqualTo("PDF");
  for(int i=0;i<count;i++){var box=shipment.path("requestedPackageLineItems").path(i);assertThat(box.path("sequenceNumber").asInt()).isEqualTo(i+1);assertThat(box.path("groupPackageCount").asInt()).isEqualTo(1);assertThat(box.path("weight").path("value").decimalValue()).isEqualByComparingTo("1.201");assertThat(box.path("dimensions").path("length").asInt()).isEqualTo(13);assertThat(box.path("customerReferences").path(0).path("value").asString()).isEqualTo(context.bookingReference().toString());assertThat(box.has("quantity")).isFalse();}
  assertThat(EventCodec.hash(builder().build(context,date))).isEqualTo(EventCodec.hash(request));
 }
 @Test void missingOrUnreviewedConfigurationAndExpiredQualificationFailClosed(){for(String stock:Arrays.asList(null,"invented","STOCK_4X6"))assertThatThrownBy(()->builder(stock,"SENDER",clock.instant().plusSeconds(600))).isInstanceOf(IllegalArgumentException.class);for(String billing:Arrays.asList(null,"RECIPIENT","THIRD_PARTY"))assertThatThrownBy(()->builder("PAPER_4X6",billing,clock.instant().plusSeconds(600))).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->builder("PAPER_4X6","SENDER",clock.instant()).build(context(1),date)).isInstanceOf(Problem.Failure.class);assertThatThrownBy(()->builder().build(context(1),null)).isInstanceOf(Problem.Failure.class);}
 @Test void shipmentAndRateMappingsRejectUnprintableOrOversizedAddressWithoutTruncation(){
  for(var address:List.of(new CarrierValues.Address("Synthetic Récipient","+2348000000001","Synthetic street",null,"Lagos","LA",null,"NG"),new CarrierValues.Address("a".repeat(71),"+2348000000001","Synthetic street",null,"Lagos","LA",null,"NG"),new CarrierValues.Address("Synthetic Recipient","+2348000000001","a".repeat(36),null,"Lagos","LA",null,"NG"),new CarrierValues.Address("Synthetic Recipient","+2348000000001","Synthetic street",null,"a".repeat(36),"LA",null,"NG"),new CarrierValues.Address("Synthetic Recipient","+2348000000001","Synthetic street",null,"Lagos","LA","1".repeat(11),"NG"))){var original=context(1);var bound=new CarrierCreationContext(original.shipmentId(),original.orderId(),address,original.shipper(),original.serviceCode(),original.packages(),original.bookingReference(),"MOCK");assertThatThrownBy(()->builder().build(bound,date)).isInstanceOf(Problem.Failure.class);var quote=new CarrierValues.QuoteInput(UUID.randomUUID(),UUID.randomUUID(),"NG","NGN",original.shipper(),address,original.packages(),null);assertThatThrownBy(()->CarrierValues.rateRequest(quote,"SYNTHETIC_SERVICE","DROPOFF_AT_FEDEX_LOCATION")).isInstanceOf(Problem.Failure.class);}
 }
 @Test void actualFedExOauthAndShipmentHttpUseOnlyConfiguredAccount()throws Exception{
  var requests=new ArrayList<tools.jackson.databind.JsonNode>();var writes=new AtomicInteger();var grants=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{boolean token=e.getRequestURI().getPath().equals("/oauth/token");byte[] input=e.getRequestBody().readAllBytes();Object result;if(token){grants.incrementAndGet();result=Map.of("access_token","mock-access","token_type","bearer","expires_in",3600);}else{writes.incrementAndGet();requests.add(EventCodec.JSON.readTree(input));result=Map.of("output",Map.of("jobId","synthetic-persisted-job"));}byte[] bytes=EventCodec.JSON.writeValueAsBytes(result);e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}});server.start();
  try{var adapter=new FedExAdapter("test",ProviderHttp.Mode.MOCK,"http://127.0.0.1:"+server.getAddress().getPort(),"mock-client","mock-secret","mock-configured-account");var request=builder().build(context(41),date);assertThat(adapter.book(request).path("jobId").asString()).isEqualTo("synthetic-persisted-job");assertThat(writes.get()).isEqualTo(1);assertThat(grants.get()).isEqualTo(1);assertThat(requests.getFirst().path("accountNumber").path("value").asString()).isEqualTo("mock-configured-account");assertThat(EventCodec.hash(requests.getFirst().path("requestedShipment"))).isEqualTo(EventCodec.hash(request.path("requestedShipment")));assertThat(request.has("accountNumber")).isFalse();}finally{server.stop(0);}
 }
}
