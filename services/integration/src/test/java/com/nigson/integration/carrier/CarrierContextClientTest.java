package com.nigson.integration.carrier;

import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.node.ObjectNode;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ProviderHttp;
import static org.assertj.core.api.Assertions.*;

class CarrierContextClientTest {
 private HttpServer server;private ExecutorService pool;private CarrierContextClient client;private volatile String body,type="application/json";private volatile int status=200;private volatile boolean stall;
 private final AtomicInteger requests=new AtomicInteger(),credentials=new AtomicInteger();private final List<String> wire=new CopyOnWriteArrayList<>();private ObjectNode context;
 static ObjectNode context(){
  var address=new CarrierValues.Address("Synthetic Recipient","+2348000000001","Synthetic street",null,"Lagos","LA",null,"NG");var destination=(ObjectNode)EventCodec.JSON.valueToTree(address);destination.put("label","Synthetic saved address").put("isDefault",false);
  return (ObjectNode)EventCodec.JSON.valueToTree(Map.of("shipmentId",UUID.randomUUID(),"orderId",UUID.randomUUID(),"address",destination,"shipper",address,"serviceCode","SYNTHETIC_SERVICE","packages",List.of(new CarrierValues.Parcel(UUID.randomUUID(),"3","1.201","12.01","13","14")),"bookingReference",UUID.randomUUID(),"mode","MOCK","accountHash","1".repeat(64)));
 }
 @BeforeEach void setup()throws Exception{
  context=context();body=EventCodec.JSON.writeValueAsString(context);server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);pool=Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(pool);
  server.createContext("/",e->{requests.incrementAndGet();wire.add(e.getRequestMethod()+" "+e.getRequestURI()+" "+e.getRequestBody().readAllBytes().length+" "+e.getRequestHeaders().getFirst("X-Correlation-ID"));if(!"Bearer synthetic-workload".equals(e.getRequestHeaders().getFirst("Authorization")))wire.add("INVALID_AUTH");if(e.getRequestHeaders().getFirst("Cookie")!=null||e.getRequestHeaders().getFirst("X-Nigson-Actor")!=null)wire.add("UNEXPECTED_BROWSER_AUTH");e.getResponseHeaders().set("Content-Type",type);if(status==302)e.getResponseHeaders().set("Location","http://127.0.0.1:"+server.getAddress().getPort()+"/redirect");
   try{if(stall){e.sendResponseHeaders(200,0);try(var out=e.getResponseBody()){out.write('{');out.flush();try{Thread.sleep(6500);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}}else{byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);e.sendResponseHeaders(status,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}}}catch(java.io.IOException cancelled){e.close();}
  });server.start();client=new CarrierContextClient("test","http://127.0.0.1:"+server.getAddress().getPort(),r->{credentials.incrementAndGet();assertThat(r.api()).isEqualTo("API-119");assertThat(r.scope()).isEqualTo("logistics.read");assertThat(r.client()).isEqualTo("nigson-integration");return "synthetic-workload";});
 }
 @AfterEach void close(){if(server!=null)server.stop(0);if(pool!=null)pool.close();}
 private UUID shipment(){return UUID.fromString(context.path("shipmentId").asString());}private UUID reference(){return UUID.fromString(context.path("bookingReference").asString());}
 private CarrierCreationContext read(){return client.read(shipment(),reference(),ProviderHttp.Mode.MOCK,UUID.randomUUID());}
 @Test void exactPrivateRouteReturnsFrozenMinimalValuesAndDoesNotUseBrowserAuth(){UUID correlation=UUID.randomUUID();var value=client.read(shipment(),reference(),ProviderHttp.Mode.MOCK,correlation);assertThat(value.shipmentId()).isEqualTo(shipment());assertThat(value.bookingReference()).isEqualTo(reference());assertThat(value.destination().line1()).isEqualTo("Synthetic street");assertThat(value.packages()).hasSize(1);assertThat(value.toString()).doesNotContain("street","Recipient","+234");assertThat(wire).containsExactly("GET /internal/v1/shipments/"+shipment()+"/provider-context 0 "+correlation);assertThat(requests.get()).isEqualTo(1);assertThat(credentials.get()).isEqualTo(1);}
 @ParameterizedTest @ValueSource(strings={"extra","missing","foreign-shipment","foreign-reference","bad-package","numeric-weight","too-many","null-shipper","address-extra","bad-default","duplicate-json","trailing-json"})
 void invalidOrForeignResponseFailsClosed(String scenario){
  switch(scenario){case "extra"->context.put("accountNumber","mock-forbidden");case "missing"->context.remove("shipper");case "foreign-shipment"->context.put("shipmentId",UUID.randomUUID().toString());case "foreign-reference"->context.put("bookingReference",UUID.randomUUID().toString());case "bad-package"->((ObjectNode)context.path("packages").path(0)).put("weightKg","0");case "numeric-weight"->((ObjectNode)context.path("packages").path(0)).put("weightKg",1);case "too-many"->{var array=(tools.jackson.databind.node.ArrayNode)context.path("packages");for(int i=0;i<100;i++)array.add(array.path(0).deepCopy());}case "null-shipper"->context.putNull("shipper");case "address-extra"->((ObjectNode)context.get("address")).put("id",UUID.randomUUID().toString());case "bad-default"->((ObjectNode)context.get("address")).put("isDefault","false");default->{} }
  body=EventCodec.JSON.writeValueAsString(context);if(scenario.equals("duplicate-json"))body=body.substring(0,body.length()-1)+",\"mode\":\"MOCK\"}";if(scenario.equals("trailing-json"))body+=" {}";
  UUID requested=scenario.equals("foreign-shipment")?UUID.randomUUID():shipment(),booking=scenario.equals("foreign-reference")?UUID.randomUUID():reference();assertThatThrownBy(()->client.read(requested,booking,ProviderHttp.Mode.MOCK,UUID.randomUUID())).isInstanceOf(Problem.Failure.class);assertThat(requests.get()).isEqualTo(1);
 }
 @Test void modeMustMatchActualAdapterAndSandboxMappingIsExplicit(){context.put("mode","SANDBOX");body=EventCodec.JSON.writeValueAsString(context);assertThatThrownBy(this::read).isInstanceOfSatisfying(Problem.Failure.class,f->assertThat(f.code).isEqualTo(ProblemCode.PROVIDER_MODE_MISMATCH));assertThat(client.read(shipment(),reference(),ProviderHttp.Mode.TEST,UUID.randomUUID()).mode()).isEqualTo("SANDBOX");context.put("mode","LIVE");body=EventCodec.JSON.writeValueAsString(context);assertThatThrownBy(this::read).isInstanceOf(Problem.Failure.class);}
 @ParameterizedTest @ValueSource(ints={401,403,404,409,422,302,500})
 void sourceFailuresAndRedirectAreNeverRetriedOrForwarded(int code){status=code;body="{\"unsafeDiagnostic\":\"synthetic-private-address\"}";assertThatThrownBy(this::read).isInstanceOf(Problem.Failure.class).hasMessageNotContaining("private-address");assertThat(requests.get()).isEqualTo(1);}
 @Test void wrongContentTypeOrOversizedBodyCannotBecomeCreationInput(){type="text/plain";assertThatThrownBy(this::read).isInstanceOf(Problem.Failure.class);type="application/json";body=" ".repeat(65537);assertThatThrownBy(this::read).isInstanceOf(Problem.Failure.class);assertThat(requests.get()).isEqualTo(2);}
 @Test void wholeResponseDeadlineCancelsStalledRead(){stall=true;long start=System.nanoTime();assertThatThrownBy(this::read).isInstanceOf(Problem.Failure.class);assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(8));assertThat(requests.get()).isEqualTo(1);}
 @Test void invalidIdsAndActiveTransactionCannotAcquireCredentialsOrSendHttp(){assertThatThrownBy(()->client.read(new UUID(0,0),reference(),ProviderHttp.Mode.MOCK,UUID.randomUUID())).isInstanceOf(Problem.Failure.class);TransactionSynchronizationManager.setActualTransactionActive(true);try{assertThatThrownBy(this::read).hasMessage("PROVIDER_CALL_INSIDE_TRANSACTION");}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}assertThat(credentials.get()).isZero();assertThat(requests.get()).isZero();}
 @Test void configuredTargetsAndInvalidCredentialsFailClosed(){for(String endpoint:List.of("http://127.0.0.1:9","https://external.example.invalid","https://commerce.internal:443","https://commerce.internal/path","https://user@commerce.internal","https://commerce.internal?x=1"))assertThatThrownBy(()->new CarrierContextClient("prod",endpoint,r->"synthetic-workload")).isInstanceOf(IllegalArgumentException.class);var invalid=new CarrierContextClient("test","http://127.0.0.1:"+server.getAddress().getPort(),r->"synthetic\r\nprivate");assertThatThrownBy(()->invalid.read(shipment(),reference(),ProviderHttp.Mode.MOCK,UUID.randomUUID())).isInstanceOf(Problem.Failure.class);assertThat(requests.get()).isZero();}
}
