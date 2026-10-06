package com.nigson.commerce.platform;

import com.nigson.shared.events.EventCodec;
import tools.jackson.databind.JsonNode;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.BiFunction;
import org.springframework.jdbc.core.JdbcTemplate;

/** Network operations happen after owned transaction methods return. */
public final class EventPump implements AutoCloseable {
    private final PlatformStore store;
    private final SnsClient sns;
    private final SqsClient sqs;
    private final String topic, queue, dead;
    private final int release;
    private final Map<String,BiFunction<JdbcTemplate,JsonNode,String>> handlers;
    private final ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    public EventPump(PlatformStore store,SnsClient sns,SqsClient sqs,String topic,String queue,String dead,int release,Map<String,BiFunction<JdbcTemplate,JsonNode,String>> handlers) {
        this.store=store;this.sns=sns;this.sqs=sqs;this.topic=Objects.requireNonNull(topic);this.queue=Objects.requireNonNull(queue);this.dead=Objects.requireNonNull(dead);this.release=release;this.handlers=Map.copyOf(handlers);
        if(release<1||release>5) throw new IllegalArgumentException("RELEASE_INVALID");
    }
    public int relay() {
        int published=0;
        for(var claim:store.claim(10,Duration.ofSeconds(120))) {
            try {
                var event=EventCodec.decode(claim.body());
                sns.publish(r -> r.topicArn(topic).message(claim.body()).messageAttributes(Map.of(
                    "event_type",MessageAttributeValue.builder().dataType("String").stringValue(event.path("event_type").asString()).build(),
                    "producer",MessageAttributeValue.builder().dataType("String").stringValue(event.path("producer").asString()).build())));
                if(store.published(claim)) published++;
            } catch(RuntimeException failure) { store.failed(claim,5); }
        }
        return published;
    }
    public int poll() {
        // An absent domain handler must never acknowledge or fabricate its effect.
        if(handlers.isEmpty()) return 0;
        var messages=sqs.receiveMessage(r -> r.queueUrl(queue).waitTimeSeconds(20).maxNumberOfMessages(10).visibilityTimeout(120).messageSystemAttributeNamesWithStrings("ApproximateReceiveCount")).messages();
        var tasks=new ArrayList<Future<Boolean>>();
        for(Message message:messages) tasks.add(workers.submit(() -> process(message)));
        int count=0;
        for(var task:tasks) { try { if(task.get(95,TimeUnit.SECONDS)) count++; } catch(Exception failure) { task.cancel(true); } }
        return count;
    }
    public boolean process(Message message) {
        long deadline=System.nanoTime()+Duration.ofSeconds(90).toNanos();
        var lease=heartbeat.scheduleAtFixedRate(() -> {
            if(System.nanoTime()<deadline) sqs.changeMessageVisibility(r -> r.queueUrl(queue).receiptHandle(message.receiptHandle()).visibilityTimeout(120));
        },30,30,TimeUnit.SECONDS);
        try {
            var event=EventCodec.decode(message.body());
            var handler=handlers.get(event.path("event_type").asString());
            if(handler==null) return false;
            store.consume("commerce-domain-v1",message.body(),release,handler);
            sqs.deleteMessage(r -> r.queueUrl(queue).receiptHandle(message.receiptHandle()));
            return true;
        } catch(IllegalArgumentException permanent) {
            // Invalid bodies may contain hostile PII. Review receives a digest/code, not raw input.
            var notice=EventCodec.JSON.writeValueAsString(Map.of("source_message_id",message.messageId(),"classification","INVALID_EVENT","body_sha256",EventCodec.digest(message.body())));
            sqs.sendMessage(r -> r.queueUrl(dead).messageBody(notice));
            sqs.deleteMessage(r -> r.queueUrl(queue).receiptHandle(message.receiptHandle()));return false;
        } catch(RuntimeException transientFailure) {
            int delivery=Integer.parseInt(message.attributesAsStrings().getOrDefault("ApproximateReceiveCount","1"));
            int[] backoff={5,20,60,120,300};
            sqs.changeMessageVisibility(r -> r.queueUrl(queue).receiptHandle(message.receiptHandle()).visibilityTimeout(backoff[Math.min(Math.max(delivery-1,0),4)]));return false;
        } finally { lease.cancel(false); }
    }
    @Override public void close() { heartbeat.shutdownNow();workers.shutdownNow();sns.close();sqs.close(); }
}
