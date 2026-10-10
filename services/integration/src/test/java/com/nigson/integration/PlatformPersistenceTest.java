package com.nigson.integration;

import com.nigson.integration.platform.*;
import com.nigson.shared.events.EventCodec;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class PlatformPersistenceTest {
 private static final String integration="integration";
 private JdbcTemplate jdbc;
 private PlatformStore store;
 private TransactionTemplate tx;
 private MutableClock clock;
 static class MutableClock extends Clock {
  Instant value=Instant.parse("2026-10-06T12:00:00Z");
  public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId z){return this;} public Instant instant(){return value;}
 }
 @BeforeEach void setup() {
  String url=System.getenv("NIGSON_TEST_JDBC_URL");
  Assumptions.assumeTrue(url!=null,"Real PostgreSQL qualification runs through make platform-check");
  if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson")) throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");
  var ds=new DriverManagerDataSource(url+"?currentSchema="+integration,"nigson_"+integration+"_app","mock-"+integration+"-runtime");
  jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);clock=new MutableClock();store=new PlatformStore(jdbc,manager,clock,new SimpleMeterRegistry());
 }
 @Test void hashReplayConcurrentCommandAndRollback() throws Exception {
  String key=UUID.randomUUID().toString();String effect="effect-"+key;UUID id=EventCodec.uuid7(clock.instant());var request=EventCodec.JSON.readTree("{\"b\":2,\"a\":1}");var calls=new AtomicInteger();
  java.util.function.Function<JdbcTemplate,PlatformStore.Response> command=db -> {
   calls.incrementAndGet();db.update("INSERT INTO integration.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (?,'test','test',?,1,'{}')",id,effect);
   return new PlatformStore.Response(201,EventCodec.JSON.readTree("{\"resourceId\":\""+id+"\"}"),id);
  };
  try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
   var futures=new ArrayList<Future<PlatformStore.Response>>();
   for(int n=0;n<20;n++) futures.add(pool.submit(() -> {
    for(int attempt=0;attempt<100;attempt++) { try { return store.executeLocal("subject","test-operation",key,request,command); } catch(PlatformStore.Conflict active) {Thread.sleep(10);} }
    throw new AssertionError("Concurrent replay failed to recover");
   }));
   for(var future:futures) assertThat(future.get(15,TimeUnit.SECONDS).resource()).isEqualTo(id);
  }
  assertThat(calls.get()).isEqualTo(1);
  assertThat(store.executeLocal("subject","test-operation",key,EventCodec.JSON.readTree("{\"a\":1,\"b\":2}"),command).status()).isEqualTo(201);
  assertThatThrownBy(() -> store.executeLocal("subject","test-operation",key,EventCodec.JSON.readTree("{\"a\":9}"),command)).isInstanceOf(PlatformStore.Conflict.class).hasMessage("IDEMPOTENCY_HASH_MISMATCH");
  String failed=UUID.randomUUID().toString();String rejected="rollback-"+failed;
  assertThatThrownBy(() -> store.executeLocal("subject","test-operation",failed,request,db -> {db.update("INSERT INTO integration.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (?,'test','test',?,1,'{}')",EventCodec.uuid7(clock.instant()),rejected);throw new IllegalStateException("injected failure");})).isInstanceOf(IllegalStateException.class);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.feature_configuration WHERE key=?",Integer.class,rejected)).isZero();
  assertThat(store.executeLocal("subject","test-operation",failed,request,db -> new PlatformStore.Response(200,request,null)).status()).isEqualTo(200);
  String stale=UUID.randomUUID().toString();jdbc.update("INSERT INTO integration.idempotency_record(id,created_by,updated_by,principal_key,operation,request_key,request_hash,state,lease_until,expires_at) VALUES (?,'test','test','subject','test-operation',?,?,'RUNNING',?,?)",EventCodec.uuid7(clock.instant()),stale,EventCodec.hash(request),java.sql.Timestamp.from(clock.instant().minusSeconds(86400)),java.sql.Timestamp.from(clock.instant().plusSeconds(86400)));
  assertThat(store.executeLocal("subject","test-operation",stale,request,db -> new PlatformStore.Response(200,request,null)).status()).isEqualTo(200);
 }
 @Test void lockedLedgerAndUncommittedUniqueKeyReturnBoundedConflictWithoutCommand() throws Exception {
  var request=EventCodec.JSON.readTree("{\"synthetic\":true}");var calls=new AtomicInteger();
  java.util.function.Function<JdbcTemplate,PlatformStore.Response> command=db->{calls.incrementAndGet();return new PlatformStore.Response(200,request,null);};
  String key=UUID.randomUUID().toString();store.executeLocal("bounded-subject","bounded-operation",key,request,command);
  try(var connection=jdbc.getDataSource().getConnection()) {
   connection.setAutoCommit(false);var held=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
   held.queryForObject("SELECT id FROM integration.idempotency_record WHERE principal_key='bounded-subject' AND operation='bounded-operation' AND request_key=? FOR UPDATE",UUID.class,key);
   long start=System.nanoTime();assertThatThrownBy(()->store.executeLocal("bounded-subject","bounded-operation",key,request,command)).isInstanceOf(PlatformStore.Conflict.class).hasMessage("IDEMPOTENCY_IN_PROGRESS");
   assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(1));assertThat(calls.get()).isEqualTo(1);connection.rollback();
  }
  assertThat(store.executeLocal("bounded-subject","bounded-operation",key,request,command).body()).isEqualTo(request);assertThat(calls.get()).isEqualTo(1);
  String uncommitted=UUID.randomUUID().toString();
  try(var connection=jdbc.getDataSource().getConnection()) {
   connection.setAutoCommit(false);var held=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
   held.update("INSERT INTO integration.idempotency_record(id,created_by,updated_by,principal_key,operation,request_key,request_hash,state,response_status,response_body,expires_at) VALUES (?,'test','test','bounded-subject','bounded-operation',?,?,'SUCCEEDED',200,?::jsonb,?)",EventCodec.uuid7(clock.instant()),uncommitted,EventCodec.hash(request),EventCodec.JSON.writeValueAsString(request),java.sql.Timestamp.from(clock.instant().plusSeconds(86400)));
   long start=System.nanoTime();assertThatThrownBy(()->store.executeLocal("bounded-subject","bounded-operation",uncommitted,request,command)).isInstanceOf(PlatformStore.Conflict.class).hasMessage("IDEMPOTENCY_IN_PROGRESS");
   assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(1));assertThat(calls.get()).isEqualTo(1);connection.commit();
  }
  assertThat(store.executeLocal("bounded-subject","bounded-operation",uncommitted,request,command).body()).isEqualTo(request);assertThat(calls.get()).isEqualTo(1);
 }
 @Test void duplicateEventHasOneEffectAndFailedTransactionCanReplay() throws Exception {
  String type=integration.equals("experience")?"distributor.entitlement.changed.v1":integration.equals("commerce")?"order.paid.v1":"order.pending.v1";
  String body=java.nio.file.Files.readString(java.nio.file.Path.of(System.getenv("NIGSON_EVENT_FIXTURES"),type+".json"));
  var event=EventCodec.decode(body);var eventId=EventCodec.uuid7(clock.instant());var updated=((tools.jackson.databind.node.ObjectNode)event).put("event_id",eventId.toString());body=EventCodec.JSON.writeValueAsString(updated);final String wire=body;
  String business="event-effect-"+eventId;AtomicInteger calls=new AtomicInteger();
  try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
   var futures=new ArrayList<Future<Boolean>>();
   for(int n=0;n<20;n++) futures.add(pool.submit(() -> store.consume("test-consumer",wire,5,(db,envelope) -> {calls.incrementAndGet();db.update("INSERT INTO integration.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (?,'test','test',?,1,'{}') ON CONFLICT(key,revision) DO NOTHING",EventCodec.uuid7(clock.instant()),business);return "APPLIED";})));
   int applied=0;for(var f:futures) if(f.get(15,TimeUnit.SECONDS)) applied++;assertThat(applied).isEqualTo(1);
  }
  assertThat(calls.get()).isEqualTo(1);
  var rollback=((tools.jackson.databind.node.ObjectNode)event).put("event_id",EventCodec.uuid7(clock.instant()).toString());String retry=EventCodec.JSON.writeValueAsString(rollback);
  assertThatThrownBy(() -> store.consume("failed-consumer",retry,5,(db,e) -> {throw new IllegalStateException("crash");})).isInstanceOf(IllegalStateException.class);
  assertThat(store.consume("failed-consumer",retry,5,(db,e) -> "APPLIED")).isTrue();
  assertThat(store.consume("failed-consumer",retry,5,(db,e) -> {throw new AssertionError("Duplicate handler ran");})).isFalse();
 }
 @Test void providerCallsAreRejectedInsideActualOwnedTransactions() {
  assertThatThrownBy(() -> tx.execute(status -> {new com.nigson.integration.providers.TwilioAdapter("test",com.nigson.shared.security.ProviderHttp.Mode.MOCK,"http://127.0.0.1:1","AC"+"0".repeat(32),"mock-twilio-token","MG"+"0".repeat(32)).sms("+2348000000001","Synthetic","http://callback-receiver:8080/twilio");return null;})).isInstanceOf(IllegalStateException.class).hasMessage("PROVIDER_CALL_INSIDE_TRANSACTION");
 }
 @Test void leaseReclaimFencesOldPublisher() throws Exception {
  Assumptions.assumeFalse(integration.equals("integration"),"No integration-produced event exists in specification v1");
  String type=integration.equals("commerce")?"order.pending.v1":"lead.created.v1";
  var event=(tools.jackson.databind.node.ObjectNode)EventCodec.JSON.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(System.getenv("NIGSON_EVENT_FIXTURES"),type+".json")));
  event.put("event_id",EventCodec.uuid7(clock.instant()).toString());event.put("occurred_at",clock.instant().toString());
  String body=EventCodec.JSON.writeValueAsString(event);
  assertThatThrownBy(() -> store.append(body,"actor")).isInstanceOf(IllegalStateException.class);
  tx.execute(s -> {store.append(body,"actor");return null;});
  var first=store.claim(1,Duration.ofSeconds(30)).getFirst();
  assertThat(store.claim(1,Duration.ofSeconds(30))).isEmpty();clock.value=clock.value.plusSeconds(31);
  var second=store.claim(1,Duration.ofSeconds(30)).getFirst();assertThat(second.generation()).isGreaterThan(first.generation());
  assertThat(store.published(first)).isFalse();assertThat(store.published(second)).isTrue();assertThat(store.published(second)).isFalse();
 }
}
