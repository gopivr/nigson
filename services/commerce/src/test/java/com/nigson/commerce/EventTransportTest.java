package com.nigson.commerce;

import com.nigson.commerce.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.observability.WireContext;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.api.trace.SpanKind;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class EventTransportTest {
 @Test void committedCommandSurvivesLostPublicationAcknowledgementAndRepeatedDelivery() throws Exception {
  String endpoint=System.getenv("NIGSON_TEST_EVENT_ENDPOINT"),url=System.getenv("NIGSON_TEST_JDBC_URL");
  Assumptions.assumeTrue(endpoint!=null&&url!=null,"Isolated LocalStack/PostgreSQL qualification required");
  if(!endpoint.matches("http://127.0.0.1:[0-9]+") || !url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson")) throw new IllegalArgumentException("LOCAL_TEST_REQUIRED");
  var credentials=StaticCredentialsProvider.create(AwsBasicCredentials.create("test","test"));
  try(var sns=SnsClient.builder().endpointOverride(URI.create(endpoint)).credentialsProvider(credentials).region(Region.US_EAST_1).build();var sqs=SqsClient.builder().endpointOverride(URI.create(endpoint)).credentialsProvider(credentials).region(Region.US_EAST_1).build()) {
   String topic=sns.createTopic(r->r.name("platform-test")).topicArn();
   String dead=sqs.createQueue(r->r.queueName("platform-test-dlq")).queueUrl();
   String queue=sqs.createQueue(r->r.queueName("platform-test-commerce").attributesWithStrings(Map.of("VisibilityTimeout","120","ReceiveMessageWaitTimeSeconds","20","MessageRetentionPeriod","345600","SqsManagedSseEnabled","true"))).queueUrl();
   String arn=sqs.getQueueAttributes(r->r.queueUrl(queue).attributeNamesWithStrings("QueueArn")).attributesAsStrings().get("QueueArn");
   sns.subscribe(r->r.topicArn(topic).protocol("sqs").endpoint(arn).attributes(Map.of("RawMessageDelivery","true","FilterPolicyScope","MessageBody","FilterPolicy","{\"producer\":[\"commerce\"],\"event_type\":[\"order.paid.v1\"]}")));
   var ds=new DriverManagerDataSource(url+"?currentSchema=commerce","nigson_commerce_app","mock-commerce-runtime");var jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);var tx=new TransactionTemplate(manager);var clock=new PlatformPersistenceTest.MutableClock();var store=new PlatformStore(jdbc,manager,clock,new SimpleMeterRegistry());
   var event=(ObjectNode)EventCodec.JSON.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(System.getenv("NIGSON_EVENT_FIXTURES"),"order.paid.v1.json")));
   String key=UUID.randomUUID().toString();event.put("event_id",EventCodec.uuid7(clock.instant()).toString());event.put("aggregate_id",EventCodec.uuid7(clock.instant()).toString());event.put("occurred_at",clock.instant().toString());String body=EventCodec.JSON.writeValueAsString(event);
   String commandKey="command-"+key,consumerKey="consumer-"+key;var input=EventCodec.JSON.readTree("{\"quantity\":1}");
   var exporter=InMemorySpanExporter.create();try(var telemetry=new Telemetry(new SimpleMeterRegistry(),"test",exporter)){
   UUID correlation=UUID.randomUUID();String[] upstream={null};
   PlatformStore.Response original;try(var observed=telemetry.http(correlation,null,"/api/v1/checkout/attempts","POST")){upstream[0]=WireContext.traceparent();
   original=store.executeLocal("subject","transport-test",key,input,db->{
    db.update("INSERT INTO commerce.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (?,'test','test',?,1,'{}')",EventCodec.uuid7(clock.instant()),commandKey);
    store.append(body,"test");return new PlatformStore.Response(201,EventCodec.JSON.readTree("{\"accepted\":true}"),null);
   });
   }
   var claim=store.claim(1,Duration.ofSeconds(120)).getFirst();
   var stored=EventCodec.decode(claim.body());assertThat(stored.path("correlation_id").asString()).isEqualTo(correlation.toString());assertThat(stored.path("traceparent").asString()).isEqualTo(upstream[0]);
   sns.publish(r->r.topicArn(topic).message(claim.body())); // accepted, then relay dies before DB acknowledgement
   assertThat(jdbc.queryForObject("SELECT state FROM commerce.outbox_event WHERE id=?",String.class,claim.id())).isEqualTo("CLAIMED");
   clock.value=clock.value.plusSeconds(121);
   var handlers=Map.<String,java.util.function.BiFunction<JdbcTemplate,tools.jackson.databind.JsonNode,String>>of("order.paid.v1",(db,e)->{
    assertThat(WireContext.current().correlationId).isEqualTo(UUID.fromString(e.path("correlation_id").asString()));
    // Business uniqueness also fences different event IDs describing the same effect.
    db.update("INSERT INTO commerce.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (?,'test','test',?,1,'{}') ON CONFLICT(key,revision) DO NOTHING",EventCodec.uuid7(clock.instant()),consumerKey);return "APPLIED";
   });
   try(var pump=new EventPump(store,sns,sqs,topic,queue,dead,5,handlers,telemetry)) {
    assertThat(pump.relay()).isEqualTo(1);assertThat(store.published(claim)).isFalse();
    for(int n=0;n<10&&jdbc.queryForObject("SELECT count(*) FROM commerce.processed_event WHERE event_id=?",Integer.class,UUID.fromString(event.path("event_id").asString()))==0;n++) pump.poll();
    sqs.sendMessage(r->r.queueUrl(queue).messageBody(body));pump.poll();
    event.put("event_id",EventCodec.uuid7(clock.instant()).toString());String sameBusiness=EventCodec.JSON.writeValueAsString(event);sqs.sendMessage(r->r.queueUrl(queue).messageBody(sameBusiness));pump.poll();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.feature_configuration WHERE key IN (?,?)",Integer.class,commandKey,consumerKey)).isEqualTo(2);
    assertThat(store.executeLocal("subject","transport-test",key,input,db->{throw new AssertionError("Replayed command executed");})).isEqualTo(original);
    sqs.sendMessage(r->r.queueUrl(queue).messageBody("{\"email\":\"private@example.invalid\"}"));pump.poll();
    var quarantine=sqs.receiveMessage(r->r.queueUrl(dead).waitTimeSeconds(5)).messages();assertThat(quarantine).hasSize(1);assertThat(quarantine.getFirst().body()).contains("INVALID_EVENT").doesNotContain("private@example.invalid");
    // Wrong producer fails the subscription before reaching a consumer.
    event.put("producer","experience");sns.publish(r->r.topicArn(topic).message(EventCodec.JSON.writeValueAsString(event)));
    assertThat(sqs.receiveMessage(r->r.queueUrl(queue).waitTimeSeconds(2)).messages()).isEmpty();
   }
   telemetry.flush();var spans=exporter.getFinishedSpanItems();
   assertThat(spans).anySatisfy(span->{assertThat(span.getKind()).isEqualTo(SpanKind.PRODUCER);assertThat(span.getParentSpanId()).isEqualTo("0000000000000000");assertThat(span.getLinks()).anySatisfy(link->assertThat(link.getSpanContext().getTraceId()).isEqualTo(upstream[0].substring(3,35)));});
   assertThat(spans).anySatisfy(span->{assertThat(span.getKind()).isEqualTo(SpanKind.CONSUMER);assertThat(span.getParentSpanId()).isEqualTo("0000000000000000");assertThat(span.getLinks()).anySatisfy(link->assertThat(link.getSpanContext().getTraceId()).isEqualTo(upstream[0].substring(3,35)));});
   assertThat(WireContext.current()).isNull();
   }
  }
 }
}
