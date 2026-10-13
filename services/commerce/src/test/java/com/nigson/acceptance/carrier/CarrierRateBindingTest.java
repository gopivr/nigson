package com.nigson.acceptance.carrier;

import java.time.*;
import java.net.*;
import java.util.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.acceptance.pricing.PricingFixture;
import com.nigson.commerce.fulfillment.*;
import com.nigson.commerce.pricing.*;
import com.nigson.commerce.market.*;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.security.ActorDelegation;
import com.nigson.shared.events.EventCodec;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real owned market/cart/price fixture and private rate HTTP; signer is a test-only source port. */
class CarrierRateBindingTest {
 private PricingFixture f;private DeliveryQuoteService service;private HttpServer server;private final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();private Runnable duringRate=()->{};
 @BeforeEach void setup()throws Exception{
  String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"native isolated owned PostgreSQL required");if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_COMMERCE_DB_REQUIRED");var ds=new DriverManagerDataSource(url+"?currentSchema=commerce","nigson_commerce_app","mock-commerce-runtime");f=new PricingFixture(new JdbcTemplate(ds),new DataSourceTransactionManager(ds));f.retail();f.jdbc.update("DELETE FROM commerce.carrier_quote WHERE cart_id=?",f.cart);
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/internal/v1/carrier/quotes",x->{x.getRequestBody().readAllBytes();calls.incrementAndGet();duringRate.run();byte[] b=EventCodec.JSON.writeValueAsBytes(new CarrierRates.Rate("FEDEX","SYNTHETIC_SERVICE","NGN","1234.56",Instant.now().plusSeconds(250),null));x.getResponseHeaders().add("Content-Type","application/json");x.getResponseHeaders().add("X-Nigson-Carrier-Mode","MOCK");x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});server.start();
  var clock=Clock.systemUTC();var actual=new HttpCarrierRates("test","http://127.0.0.1:"+server.getAddress().getPort(),r->"mock-source-workload",clock,"MOCK");CarrierRates rates=(request,actor,correlation)->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return actual.quote(request,actor,correlation);};var signer=mock(ActorDelegation.class);when(signer.issue(anyString(),any(),nullable(UUID.class))).thenAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return "mock-source-assertion";});var authority=new PricingAuthority(f.jdbc,clock,3,"SYNTHETIC_DISTRIBUTOR_BUY");var providers=new PricingProviders(true,"test","MOCK","SYNTHETIC_PAYMENT_NOT_QUALIFIED","SYNTHETIC_CARRIER_NOT_QUALIFIED",Instant.now().plusSeconds(600),clock);var policy=new FulfillmentPolicy(true,"test","MOCK","synthetic-offline-approval",Instant.now().plusSeconds(600),null,Set.of("www.fedex.com"),clock);var origin=new CarrierPacking.Address("Synthetic Origin","+2348000000001","Synthetic Origin Street",null,"Lagos","LA",null,"NG");service=new DeliveryQuoteService(f.jdbc,f.manager,authority,providers,new JdbcMarketEligibility(f.jdbc,clock,"test"),policy,rates,signer,new PlatformStore(f.jdbc,f.manager,clock,f.metrics),new AuditWriter(f.jdbc,clock,f.metrics),clock,origin,"MOCK");
 }
 @AfterEach void cleanup(){if(server!=null)server.stop(0);if(f!=null)f.close();}
 private DeliveryQuoteValues.Quote quote(String key){return service.create(f.customer,"mock-source-session",f.cart,0,new DeliveryQuoteValues.Input(f.address,null),key,UUID.randomUUID());}
 @Test void bindsExactFeeAndFrozenShipperWithoutReservingStockAndReplaysWithoutHttp(){var q=quote("original");assertThat(q.deliveryAmount()).isEqualTo("1234.56");assertThat(q.cartVersion()).isZero();assertThat(q.mode()).isEqualTo("MOCK");assertThat(quote("original")).isEqualTo(q);assertThat(calls.get()).isEqualTo(1);var row=f.jdbc.queryForMap("SELECT * FROM commerce.carrier_quote WHERE id=?",q.id());assertThat(row.get("status")).isEqualTo("QUOTED");assertThat(row.get("provider_snapshot").toString()).contains("Synthetic Origin");assertThat(f.jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE resource_id=? AND action='CHECKOUT_QUOTE'",Integer.class,q.id())).isEqualTo(1);var checkout=f.quotes.create(f.customer,new PricingValues.QuoteInput(f.cart,f.address,q.id(),null),"checkout-with-real-rate",UUID.randomUUID());assertThat(checkout.totals().grandTotal()).isEqualTo("1259.24");assertThat(f.jdbc.queryForObject("SELECT count(*) FROM commerce.inventory_reservation r JOIN commerce.inventory_balance b ON b.id=r.balance_id WHERE b.sku_id=?",Integer.class,f.sku)).isZero();}
 @Test void changedCartVersionDuringHttpCannotBindAQuote(){duringRate=()->f.jdbc.update("UPDATE commerce.cart SET version=1 WHERE id=?",f.cart);assertThatThrownBy(()->quote("changed-cart")).hasMessage("VERSION_MISMATCH");assertThat(f.jdbc.queryForObject("SELECT count(*) FROM commerce.carrier_quote WHERE cart_id=?",Integer.class,f.cart)).isZero();}
 @Test void changedAddressOrPackagesDuringHttpCannotBindAQuote(){duringRate=()->f.jdbc.update("UPDATE commerce.address SET line1='Changed synthetic address' WHERE id=?",f.address);assertThatThrownBy(()->quote("changed-address")).hasMessage("CHECKOUT_REPRICE_REQUIRED");assertThat(f.jdbc.queryForObject("SELECT count(*) FROM commerce.carrier_quote WHERE cart_id=?",Integer.class,f.cart)).isZero();}
 @Test void changedPackingDuringHttpCannotBindAQuote(){duringRate=()->f.jdbc.update("UPDATE commerce.sku SET carrier_packaging=jsonb_set(carrier_packaging,'{unitWeightKg}','\"2\"'::jsonb) WHERE id=?",f.sku);assertThatThrownBy(()->quote("changed-packages")).hasMessage("CHECKOUT_REPRICE_REQUIRED");assertThat(f.jdbc.queryForObject("SELECT count(*) FROM commerce.carrier_quote WHERE cart_id=?",Integer.class,f.cart)).isZero();}
 @Test void auditFailureRollsBackFeeAndSameCommandRecovers(){f.admin.execute("REVOKE INSERT ON commerce.audit_record FROM nigson_commerce_app");try{assertThatThrownBy(()->quote("audit-recovery")).isInstanceOf(org.springframework.dao.DataAccessException.class);}finally{f.admin.execute("GRANT INSERT ON commerce.audit_record TO nigson_commerce_app");}assertThat(f.jdbc.queryForObject("SELECT count(*) FROM commerce.carrier_quote WHERE cart_id=?",Integer.class,f.cart)).isZero();assertThat(quote("audit-recovery").deliveryAmount()).isEqualTo("1234.56");}
 @Test void currentCustomerAuthorityIsRequiredBeforeReplay(){quote("authority");f.jdbc.update("UPDATE commerce.party SET status='SUSPENDED' WHERE id=?",f.customer.party());assertThatThrownBy(()->quote("authority")).hasMessage("ACCESS_DENIED");assertThat(calls.get()).isEqualTo(1);}
}
