package com.nigson.acceptance.carrier;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.node.ObjectNode;
import com.nigson.integration.carrier.*;
import com.nigson.integration.provider.*;
import com.nigson.integration.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ProviderHttp;
import static org.assertj.core.api.Assertions.*;

/** Native integration runtime role, real transactional inbox and actual SNS/SQS; no carrier HTTP. */
class CarrierBookingIntakePersistenceTest {
 private JdbcTemplate jdbc,admin;private PlatformStore store;private TransactionTemplate tx;private JobRepository jobs;private AuditWriter audit;private Clock clock;
 private final UUID generation=UUID.randomUUID();private CarrierBookingRequestedHandler handler;private final String consumer="carrier-intake-"+UUID.randomUUID();private final List<UUID> shipments=new ArrayList<>(),references=new ArrayList<>();
 @BeforeEach void setup(){
  String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"native isolated owned PostgreSQL required");if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_INTEGRATION_DB_REQUIRED");
  var ds=new DriverManagerDataSource(url+"?currentSchema=integration","nigson_integration_app","mock-integration-runtime");jdbc=new JdbcTemplate(ds);admin=new JdbcTemplate(new DriverManagerDataSource(url,"nigson_bootstrap","mock-bootstrap"));var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);clock=Clock.systemUTC();var metrics=new SimpleMeterRegistry();audit=new AuditWriter(jdbc,clock,metrics);store=new PlatformStore(jdbc,manager,clock,metrics);jobs=new JdbcJobRepository(jdbc,clock);handler=new CarrierBookingRequestedHandler(jobs,audit,clock,ProviderHttp.Mode.MOCK,generation,"a".repeat(64));
 }
 @AfterEach void cleanup(){if(jdbc!=null){for(UUID id:shipments)jdbc.update("DELETE FROM integration.carrier_booking WHERE shipment_id=?",id);for(UUID id:references)jdbc.update("DELETE FROM integration.integration_job WHERE business_key=?",key(id));}}
 private static String key(UUID reference){return "carrier-booking:"+reference;}
 private ObjectNode event()throws Exception{
  UUID shipment=UUID.randomUUID(),reference=UUID.randomUUID();shipments.add(shipment);references.add(reference);
  var event=(ObjectNode)EventCodec.JSON.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(System.getenv("NIGSON_EVENT_FIXTURES"),"shipment.booking.requested.v1.json")));event.put("event_id",UUID.randomUUID().toString()).put("aggregate_id",shipment.toString());var payload=(ObjectNode)event.get("payload");payload.put("shipment_id",shipment.toString()).put("booking_request_id",reference.toString());return event;
 }
 private UUID shipment(ObjectNode event){return UUID.fromString(event.path("payload").path("shipment_id").asString());}
 private UUID reference(ObjectNode event){return UUID.fromString(event.path("payload").path("booking_request_id").asString());}
 private boolean consume(ObjectNode event){return store.consume(consumer,EventCodec.JSON.writeValueAsString(event),1,handler::handle);}
 private int count(String table,UUID shipment){return jdbc.queryForObject("SELECT count(*) FROM integration."+table+" WHERE shipment_id=?",Integer.class,shipment);}
 private UUID job(ObjectNode event){return jdbc.queryForObject("SELECT id FROM integration.integration_job WHERE business_key=?",UUID.class,key(reference(event)));}
 @Test void concurrentSemanticDuplicatesPersistOneIntentJobAndAudit()throws Exception{
  var event=event();var events=new ArrayList<ObjectNode>();for(int i=0;i<12;i++)events.add(event.deepCopy().put("event_id",UUID.randomUUID().toString()).put("aggregate_version",i));
  try(var pool=Executors.newFixedThreadPool(12)){var start=new CountDownLatch(1);var tasks=new ArrayList<Future<Boolean>>();for(var value:events)tasks.add(pool.submit(()->{start.await();return consume(value);}));start.countDown();for(var task:tasks)assertThat(task.get(15,TimeUnit.SECONDS)).isTrue();}
  assertThat(count("carrier_booking",shipment(event))).isEqualTo(1);var saved=jobs.find(job(event),false);assertThat(saved.kind()).isEqualTo(JobValues.Kind.LOGISTICS_CREATE);assertThat(saved.resource()).isEqualTo(shipment(event));assertThat(saved.owner()).isEqualTo("commerce");assertThat(saved.attempts()).isZero();assertThat(saved.state()).isEqualTo(JobValues.State.PENDING);assertThat(saved.businessKey()).isEqualTo(key(reference(event)));
  assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.audit_record WHERE resource_id=?",Integer.class,saved.id())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.processed_event WHERE consumer=?",Integer.class,consumer)).isEqualTo(12);assertThat(consume(events.getFirst())).isFalse();assertThat(jdbc.queryForObject("SELECT request_hash FROM integration.carrier_booking WHERE shipment_id=?",String.class,shipment(event))).isEqualTo(EventCodec.hash(event.path("payload")));
 }
 @Test void conflictingShipmentOrReferenceRollsBackItsReceiptAndJob()throws Exception{
  var first=event();assertThat(consume(first)).isTrue();var second=event();((ObjectNode)second.get("payload")).put("shipment_id",shipment(first).toString());second.put("aggregate_id",shipment(first).toString());assertThatThrownBy(()->consume(second)).isInstanceOfSatisfying(Problem.Failure.class,f->assertThat(f.code).isEqualTo(ProblemCode.IDEMPOTENCY_HASH_MISMATCH));
  var third=event();((ObjectNode)third.get("payload")).put("booking_request_id",reference(first).toString());assertThatThrownBy(()->consume(third)).isInstanceOfSatisfying(Problem.Failure.class,f->assertThat(f.code).isEqualTo(ProblemCode.IDEMPOTENCY_HASH_MISMATCH));assertThat(count("carrier_booking",shipment(third))).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.integration_job WHERE business_key=?",Integer.class,key(reference(second)))).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.processed_event WHERE consumer=?",Integer.class,consumer)).isEqualTo(1);
 }
 @Test void failedAuditRollsBackIntentJobInboxAndSameDeliveryRecovers()throws Exception{
  var event=event();admin.execute("REVOKE INSERT ON integration.audit_record FROM nigson_integration_app");try{assertThatThrownBy(()->consume(event)).isInstanceOf(org.springframework.dao.DataAccessException.class);}finally{admin.execute("GRANT INSERT ON integration.audit_record TO nigson_integration_app");}
  assertThat(count("carrier_booking",shipment(event))).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.integration_job WHERE business_key=?",Integer.class,key(reference(event)))).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.processed_event WHERE consumer=?",Integer.class,consumer)).isZero();assertThat(consume(event)).isTrue();assertThat(count("carrier_booking",shipment(event))).isEqualTo(1);
 }
 @Test void replayPreservesModeUnknownEvidenceLeaseAndTerminalJob()throws Exception{
  var event=event();consume(event);UUID job=job(event);Instant lease=Instant.parse("2026-10-14T12:00:00Z");String boundHash="a".repeat(64);jdbc.update("UPDATE integration.carrier_booking SET state='UNKNOWN',version=7,request_hash=?,lease_until=?,tracking_number='123456789012',provider_shipment_id='synthetic-job',error_code='PROVIDER_OUTCOME_UNKNOWN' WHERE shipment_id=?",boundHash,java.sql.Timestamp.from(lease),shipment(event));jdbc.update("UPDATE integration.integration_job SET state='SUCCEEDED',version=4,attempt_count=2 WHERE id=?",job);event.put("event_id",UUID.randomUUID().toString());var changedMode=new CarrierBookingRequestedHandler(jobs,audit,clock,ProviderHttp.Mode.LIVE,generation,"a".repeat(64));assertThat(store.consume(consumer,EventCodec.JSON.writeValueAsString(event),1,changedMode::handle)).isTrue();
  var row=jdbc.queryForMap("SELECT * FROM integration.carrier_booking WHERE shipment_id=?",shipment(event));assertThat(row.get("mode")).isEqualTo("MOCK");assertThat(row.get("state")).isEqualTo("UNKNOWN");assertThat(row.get("version")).isEqualTo(7L);assertThat(row.get("request_hash")).isEqualTo(boundHash);assertThat(((java.sql.Timestamp)row.get("lease_until")).toInstant()).isEqualTo(lease);assertThat(row.get("tracking_number")).isEqualTo("123456789012");assertThat(row.get("provider_shipment_id")).isEqualTo("synthetic-job");assertThat(jobs.find(job,false).state()).isEqualTo(JobValues.State.SUCCEEDED);assertThat(jobs.find(job,false).version()).isEqualTo(4);assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.audit_record WHERE resource_id=?",Integer.class,job)).isEqualTo(1);
 }
 @Test void missingExecutionEvidenceCannotManufactureFreshCreationJob()throws Exception{
  var event=event();consume(event);jdbc.update("DELETE FROM integration.integration_job WHERE id=?",job(event));event.put("event_id",UUID.randomUUID().toString());assertThatThrownBy(()->consume(event)).isInstanceOfSatisfying(Problem.Failure.class,f->assertThat(f.code).isEqualTo(ProblemCode.PROVIDER_OUTCOME_UNKNOWN));assertThat(count("carrier_booking",shipment(event))).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.integration_job WHERE business_key=?",Integer.class,key(reference(event)))).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.processed_event WHERE consumer=?",Integer.class,consumer)).isEqualTo(1);
 }
 @Test void conflictingExistingJobCannotCommitAnOrphanIntent()throws Exception{
  var event=event();tx.execute(s->jobs.enqueue(JobValues.Kind.REPORT_EXPORT,key(reference(event)),"commerce",shipment(event)));assertThatThrownBy(()->consume(event)).isInstanceOfSatisfying(Problem.Failure.class,f->assertThat(f.code).isEqualTo(ProblemCode.IDEMPOTENCY_HASH_MISMATCH));assertThat(count("carrier_booking",shipment(event))).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.processed_event WHERE consumer=?",Integer.class,consumer)).isZero();
 }
 @Test void survivingJobCannotRecreateLostBookingEvidence()throws Exception{
  var event=event();var saved=tx.execute(s->jobs.enqueue(JobValues.Kind.LOGISTICS_CREATE,key(reference(event)),"commerce",shipment(event)));jdbc.update("UPDATE integration.integration_job SET state='UNKNOWN',attempt_count=1,error_code='PROVIDER_OUTCOME_UNKNOWN' WHERE id=?",saved.id());assertThatThrownBy(()->consume(event)).isInstanceOfSatisfying(Problem.Failure.class,f->assertThat(f.code).isEqualTo(ProblemCode.PROVIDER_OUTCOME_UNKNOWN));assertThat(count("carrier_booking",shipment(event))).isZero();assertThat(jobs.find(saved.id(),false).state()).isEqualTo(JobValues.State.UNKNOWN);assertThat(jobs.find(saved.id(),false).attempts()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.processed_event WHERE consumer=?",Integer.class,consumer)).isZero();
 }
 @Test void malformedOrMisroutedEventCannotPersistIntentAndHandlerRequiresTransaction()throws Exception{
  var event=event();assertThatThrownBy(()->handler.handle(jdbc,event)).hasMessage("LOCAL_TRANSACTION_REQUIRED");event.put("aggregate_id",UUID.randomUUID().toString());assertThatThrownBy(()->consume(event)).isInstanceOf(Problem.Failure.class);event.put("aggregate_id",shipment(event).toString());((ObjectNode)event.get("payload")).put("accountNumber","mock-forbidden");assertThatThrownBy(()->consume(event)).isInstanceOf(IllegalArgumentException.class);assertThat(count("carrier_booking",shipment(event))).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.processed_event WHERE consumer=?",Integer.class,consumer)).isZero();
 }
 @Test void sandboxIntentRecordsSandboxWithoutCallingProvider()throws Exception{
  var event=event();var sandbox=new CarrierBookingRequestedHandler(jobs,audit,clock,ProviderHttp.Mode.TEST,generation,"a".repeat(64));assertThat(store.consume(consumer,EventCodec.JSON.writeValueAsString(event),1,sandbox::handle)).isTrue();assertThat(jdbc.queryForObject("SELECT mode FROM integration.carrier_booking WHERE shipment_id=?",String.class,shipment(event))).isEqualTo("SANDBOX");
 }
 @Test void uppercaseUuidEncodingKeepsCanonicalIntentIdentity()throws Exception{var event=event();UUID shipment=shipment(event),reference=reference(event);event.put("aggregate_id",shipment.toString().toUpperCase(Locale.ROOT));((ObjectNode)event.get("payload")).put("shipment_id",shipment.toString().toUpperCase(Locale.ROOT)).put("booking_request_id",reference.toString().toUpperCase(Locale.ROOT));assertThat(consume(event)).isTrue();assertThat(jdbc.queryForObject("SELECT request_hash FROM integration.carrier_booking WHERE shipment_id=?",String.class,shipment)).isEqualTo(EventCodec.hash(EventCodec.JSON.valueToTree(Map.of("shipment_id",shipment,"booking_request_id",reference,"provider","FEDEX"))));}
 @Test void actualSnsSqsDeletesOnlyAfterAtomicIntentAndAuditCommit()throws Exception{
  String endpoint=System.getenv("NIGSON_TEST_EVENT_ENDPOINT");Assumptions.assumeTrue(endpoint!=null,"isolated LocalStack required");if(!endpoint.matches("http://127.0.0.1:[0-9]+"))throw new IllegalArgumentException("LOCAL_EVENT_ENDPOINT_REQUIRED");var credentials=software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("mock-localstack","mock-localstack"));
  try(var sns=software.amazon.awssdk.services.sns.SnsClient.builder().endpointOverride(URI.create(endpoint)).credentialsProvider(credentials).region(software.amazon.awssdk.regions.Region.US_EAST_1).build();var sqs=software.amazon.awssdk.services.sqs.SqsClient.builder().endpointOverride(URI.create(endpoint)).credentialsProvider(credentials).region(software.amazon.awssdk.regions.Region.US_EAST_1).build()){
   String suffix=UUID.randomUUID().toString(),topic=sns.createTopic(r->r.name("carrier-intake-"+suffix)).topicArn(),queue=sqs.createQueue(r->r.queueName("carrier-intake-"+suffix)).queueUrl(),dead=sqs.createQueue(r->r.queueName("carrier-intake-dead-"+suffix)).queueUrl();String arn=sqs.getQueueAttributes(r->r.queueUrl(queue).attributeNamesWithStrings("QueueArn")).attributesAsStrings().get("QueueArn");sns.subscribe(r->r.topicArn(topic).protocol("sqs").endpoint(arn).attributes(Map.of("RawMessageDelivery","true","FilterPolicyScope","MessageBody","FilterPolicy","{\"producer\":[\"commerce\"],\"event_type\":[\"shipment.booking.requested.v1\"]}")));
   var event=event();String body=EventCodec.JSON.writeValueAsString(event);var handlers=Map.<String,java.util.function.BiFunction<JdbcTemplate,tools.jackson.databind.JsonNode,String>>of(handler.eventType(),handler::handle);
   try(var pump=new EventPump(store,sns,sqs,topic,queue,dead,1,handlers)){try{
    sns.publish(r->r.topicArn(topic).message(body));List<software.amazon.awssdk.services.sqs.model.Message> received=List.of();for(int i=0;i<5&&received.isEmpty();i++)received=sqs.receiveMessage(r->r.queueUrl(queue).waitTimeSeconds(1).maxNumberOfMessages(1)).messages();assertThat(received).hasSize(1);var message=received.getFirst();admin.execute("REVOKE INSERT ON integration.audit_record FROM nigson_integration_app");try{assertThat(pump.process(message)).isFalse();assertThat(count("carrier_booking",shipment(event))).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.processed_event WHERE event_id=?",Integer.class,UUID.fromString(event.path("event_id").asString()))).isZero();}finally{admin.execute("GRANT INSERT ON integration.audit_record TO nigson_integration_app");}
    sqs.changeMessageVisibility(r->r.queueUrl(queue).receiptHandle(message.receiptHandle()).visibilityTimeout(0));var retry=sqs.receiveMessage(r->r.queueUrl(queue).waitTimeSeconds(1).maxNumberOfMessages(1)).messages();assertThat(retry).hasSize(1);assertThat(pump.process(retry.getFirst())).isTrue();assertThat(count("carrier_booking",shipment(event))).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.processed_event WHERE event_id=?",Integer.class,UUID.fromString(event.path("event_id").asString()))).isEqualTo(1);assertThat(sqs.receiveMessage(r->r.queueUrl(queue).waitTimeSeconds(1)).messages()).isEmpty();
    event.put("event_id",UUID.randomUUID().toString());String duplicate=EventCodec.JSON.writeValueAsString(event);sqs.sendMessage(r->r.queueUrl(queue).messageBody(duplicate));var replay=sqs.receiveMessage(r->r.queueUrl(queue).waitTimeSeconds(1)).messages();assertThat(replay).hasSize(1);assertThat(pump.process(replay.getFirst())).isTrue();assertThat(count("carrier_booking",shipment(event))).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.audit_record WHERE resource_id=?",Integer.class,job(event))).isEqualTo(1);
   }finally{sqs.deleteQueue(r->r.queueUrl(queue));sqs.deleteQueue(r->r.queueUrl(dead));sns.deleteTopic(r->r.topicArn(topic));}}
  }
 }
}
