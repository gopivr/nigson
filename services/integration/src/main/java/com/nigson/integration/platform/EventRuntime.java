package com.nigson.integration.platform;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_EVENTS_ENABLED",havingValue="true")
public class EventRuntime implements AutoCloseable {
 private final ScheduledExecutorService executor=Executors.newScheduledThreadPool(2);
 private final EventPump pump;
 public EventRuntime(PlatformStore store,@Value("${APP_ENV}") String environment,
   @Value("${NIGSON_EVENT_ENDPOINT:}") String endpoint,@Value("${AWS_REGION:us-east-1}") String region,
   @Value("${NIGSON_EVENT_TOPIC}") String topic,@Value("${NIGSON_EVENT_QUEUE}") String queue,
   @Value("${NIGSON_EVENT_DLQ}") String dead,@Value("${SCHEMA_RELEASE:BP1}") String release,org.springframework.beans.factory.ObjectProvider<EventHandler> handlers) {
   if(!release.matches("BP[1-5]")) throw new IllegalArgumentException("RELEASE_INVALID");
   var sns=SnsClient.builder().region(Region.of(region));var sqs=SqsClient.builder().region(Region.of(region));
   var bounds=ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(10)).apiCallAttemptTimeout(Duration.ofSeconds(8)).build();sns.overrideConfiguration(bounds);sqs.overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(35)).apiCallAttemptTimeout(Duration.ofSeconds(30)).build());
   if(Set.of("local","test").contains(environment)) {
    if(!endpoint.equals("http://localstack:4566")) throw new IllegalArgumentException("LOCAL_AWS_TARGET_INVALID");
    var credentials=StaticCredentialsProvider.create(AwsBasicCredentials.create("test","test"));sns.credentialsProvider(credentials).endpointOverride(URI.create(endpoint));sqs.credentialsProvider(credentials).endpointOverride(URI.create(endpoint));
   } else {
    if(!endpoint.isEmpty() || !environment.equals("prod") || topic.contains(":000000000000:")) throw new IllegalArgumentException("PRODUCTION_AWS_CONFIG_INVALID");
    sns.credentialsProvider(DefaultCredentialsProvider.builder().build());sqs.credentialsProvider(DefaultCredentialsProvider.builder().build());
   }
   // Domain tasks register concrete handlers; no placeholder acknowledgement is installed.
   var registered=new HashMap<String,java.util.function.BiFunction<org.springframework.jdbc.core.JdbcTemplate,tools.jackson.databind.JsonNode,String>>();
   handlers.forEach(h -> {if(registered.putIfAbsent(h.eventType(),h::handle)!=null) throw new IllegalArgumentException("DUPLICATE_EVENT_HANDLER");});
   pump=new EventPump(store,sns.build(),sqs.build(),topic,queue,dead,Integer.parseInt(release.substring(2)),registered);
 }
 @EventListener(ApplicationReadyEvent.class) public void start() {
  executor.scheduleWithFixedDelay(() -> { try {pump.relay();} catch(RuntimeException failure) {System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"OUTBOX_RELAY_UNAVAILABLE");} },0,1,TimeUnit.SECONDS);
  executor.scheduleWithFixedDelay(() -> {try {pump.poll();} catch(RuntimeException failure) {System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"EVENT_POLL_UNAVAILABLE");}},0,1,TimeUnit.SECONDS);
 }
 @Override public void close() {executor.shutdownNow();pump.close();}
}
