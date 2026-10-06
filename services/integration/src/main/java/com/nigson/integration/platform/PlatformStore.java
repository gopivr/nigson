package com.nigson.integration.platform;

import com.nigson.shared.events.EventCodec;
import tools.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.BiFunction;

/** Owned local-transaction primitives. Callbacks perform local DB work only. */
public final class PlatformStore {
    private static final String integration = "integration";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final MeterRegistry metrics;
    public PlatformStore(JdbcTemplate jdbc, PlatformTransactionManager manager, Clock clock, MeterRegistry metrics) {
        this.jdbc=jdbc; this.tx=new TransactionTemplate(manager); this.tx.setTimeout(90); this.clock=clock; this.metrics=metrics;
    }
    public record Response(int status, JsonNode body, UUID resource) {
        public Response { if(status<100||status>599||body==null||EventCodec.JSON.writeValueAsBytes(body).length>65536) throw new IllegalArgumentException("RESPONSE_INVALID"); }
    }
    public record Claim(UUID id, long generation, String body, int attempt) {}
    public static final class Conflict extends RuntimeException { public final int status=409; public Conflict(String code) { super(code); } }
    private static String bounded(String input,int max) {
        if(input==null || input.isBlank() || input.length()>max || !input.matches("[\\x20-\\x7e]+")) throw new IllegalArgumentException("KEY_INVALID"); return input;
    }
    private Instant now() { return clock.instant(); }
    private static java.sql.Timestamp stamp(Instant value) { return java.sql.Timestamp.from(value); }
    private void transactional() { if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED"); }

    /** Store the full wire envelope in payload, retaining correlation/trace across crashes. */
    public void append(String body, String actor) {
        transactional(); JsonNode event=EventCodec.decode(body);
        if(!event.path("producer").asString().equals(integration)) throw new IllegalArgumentException("EVENT_PRODUCER_DENIED");
        bounded(actor,200); Instant time=now();
        jdbc.update("INSERT INTO integration.outbox_event(id,created_by,updated_by,event_id,event_type,schema_version,aggregate_id,aggregate_version,payload,occurred_at,next_attempt_at) VALUES (?,?,?,?,?,1,?,?,?::jsonb,?,?)",
            EventCodec.uuid7(time),actor,actor,UUID.fromString(event.path("event_id").asString()),event.path("event_type").asString(),UUID.fromString(event.path("aggregate_id").asString()),event.path("aggregate_version").asLong(),body,stamp(Instant.parse(event.path("occurred_at").asString())),stamp(time));
    }
    public List<Claim> claim(int limit, Duration lease) {
        if(limit<1||limit>100||lease.toSeconds()<1||lease.toSeconds()>120) throw new IllegalArgumentException("LEASE_INVALID");
        return tx.execute(status -> jdbc.query("WITH due AS (SELECT id FROM integration.outbox_event WHERE (state='PENDING' AND next_attempt_at<=?) OR (state='CLAIMED' AND lease_until<=?) ORDER BY next_attempt_at,id FOR UPDATE SKIP LOCKED LIMIT ?) UPDATE integration.outbox_event e SET state='CLAIMED',lease_until=?,attempt_count=attempt_count+1,version=version+1,updated_at=?,updated_by='outbox-relay' FROM due WHERE e.id=due.id RETURNING e.id,e.version,e.payload::text,e.attempt_count",
            (row,index) -> new Claim(row.getObject(1,UUID.class),row.getLong(2),row.getString(3),row.getInt(4)),stamp(now()),stamp(now()),limit,stamp(now().plus(lease)),stamp(now())));
    }
    public boolean published(Claim claim) {
        int changed=jdbc.update("UPDATE integration.outbox_event SET state='PUBLISHED',lease_until=NULL,version=version+1,updated_at=?,updated_by='outbox-relay' WHERE id=? AND version=? AND state='CLAIMED'",stamp(now()),claim.id(),claim.generation());
        if(changed==1) metrics.counter("nigson.outbox.published","owner",integration).increment(); return changed==1;
    }
    public void failed(Claim claim, int maxAttempts) {
        if(maxAttempts<1||maxAttempts>20) throw new IllegalArgumentException("ATTEMPT_LIMIT_INVALID");
        int[] seconds={5,20,60,120,300};
        jdbc.update("UPDATE integration.outbox_event SET state=?,lease_until=NULL,next_attempt_at=?,version=version+1,updated_at=?,updated_by='outbox-relay' WHERE id=? AND version=? AND state='CLAIMED'",
            claim.attempt()>=maxAttempts?"FAILED":"PENDING",stamp(now().plusSeconds(seconds[Math.min(claim.attempt()-1,4)])),stamp(now()),claim.id(),claim.generation());
        metrics.counter("nigson.outbox.failure","owner",integration).increment();
    }
    /** Handler inserts a durable owned effect/job and returns APPLIED or refreshed-authority IGNORED. */
    public boolean consume(String consumer, String body, int release, BiFunction<JdbcTemplate,JsonNode,String> handler) {
        bounded(consumer,80); JsonNode event=EventCodec.decode(body);
        if(!EventCodec.subscribed(integration,event,release)) throw new IllegalArgumentException("EVENT_SUBSCRIPTION_DENIED");
        return Boolean.TRUE.equals(tx.execute(status -> {
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,consumer+":"+UUID.fromString(event.path("event_id").asString()).toString());
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM integration.processed_event WHERE consumer=? AND event_id=?)",Boolean.class,consumer,UUID.fromString(event.path("event_id").asString())))) {
                metrics.counter("nigson.event.duplicate","owner","integration").increment(); return false;
            }
            String outcome=handler.apply(jdbc,event);
            if(!Set.of("APPLIED","IGNORED").contains(outcome)) throw new IllegalArgumentException("EVENT_OUTCOME_INVALID");
            jdbc.update("INSERT INTO integration.processed_event(id,created_by,consumer,event_id,event_type,outcome,processed_at) VALUES (?,?,?,?,?,?,?)",
                EventCodec.uuid7(now()),consumer,consumer,UUID.fromString(event.path("event_id").asString()),event.path("event_type").asString(),outcome,stamp(now()));
            return true;
        }));
    }
    public boolean ignoreConfirmed(String consumer,String body,int release,Runnable authoritativeRefresh) {
        bounded(consumer,80); JsonNode event=EventCodec.decode(body);
        if(!EventCodec.subscribed(integration,event,release)) throw new IllegalArgumentException("EVENT_SUBSCRIPTION_DENIED");
        // Refresh outside DB locks; owner port must assert current authoritative revision.
        authoritativeRefresh.run();
        return Boolean.TRUE.equals(tx.execute(status -> jdbc.update("INSERT INTO integration.processed_event(id,created_by,consumer,event_id,event_type,outcome,processed_at) VALUES (?,?,?,?,?,'IGNORED',?) ON CONFLICT(consumer,event_id) DO NOTHING",EventCodec.uuid7(now()),consumer,consumer,UUID.fromString(event.path("event_id").asString()),event.path("event_type").asString(),stamp(now()))==1));
    }
    public Response executeLocal(String principal,String operation,String key,JsonNode request,Function<JdbcTemplate,Response> command) {
        bounded(principal,200); bounded(operation,120); bounded(key,100); String hash=EventCodec.hash(request);
        UUID token=EventCodec.uuid7(now());
        Map<String,Object> saved=tx.execute(status -> {
            jdbc.update("INSERT INTO integration.idempotency_record(id,created_by,updated_by,principal_key,operation,request_key,request_hash,state,lease_until,expires_at) VALUES (?,?,?,?,?,?,?,'RUNNING',?,?) ON CONFLICT(principal_key,operation,request_key) DO NOTHING",token,principal,principal,principal,operation,key,hash,stamp(now().plusSeconds(30)),stamp(now().plus(Duration.ofDays(1))));
            var row=jdbc.queryForMap("SELECT * FROM integration.idempotency_record WHERE principal_key=? AND operation=? AND request_key=? FOR UPDATE",principal,operation,key);
            if(!row.get("request_hash").equals(hash)) throw new Conflict("IDEMPOTENCY_HASH_MISMATCH");
            if(row.get("state").equals("SUCCEEDED")) return row;
            if(!row.get("id").equals(token)) {
                var deadline=(java.sql.Timestamp)row.get("lease_until");
                if(row.get("state").equals("RUNNING") && deadline!=null && deadline.toInstant().isAfter(now())) throw new Conflict("IDEMPOTENCY_IN_PROGRESS");
                jdbc.update("UPDATE integration.idempotency_record SET state='RUNNING',lease_until=?,version=version+1,updated_at=?,updated_by=? WHERE id=?",stamp(now().plusSeconds(30)),stamp(now()),principal,row.get("id"));
                row.put("version",((Number)row.get("version")).longValue()+1);
            }
            return row;
        });
        if(saved.get("state").equals("SUCCEEDED")) return replay(saved);
        UUID id=(UUID)saved.get("id"); long generation=((Number)saved.get("version")).longValue();
        try {
            return tx.execute(status -> {
                var row=jdbc.queryForMap("SELECT * FROM integration.idempotency_record WHERE id=? FOR UPDATE",id);
                if(((Number)row.get("version")).longValue()!=generation || !row.get("state").equals("RUNNING")) throw new Conflict("IDEMPOTENCY_LEASE_LOST");
                Response response=Objects.requireNonNull(command.apply(jdbc));
                jdbc.update("UPDATE integration.idempotency_record SET state='SUCCEEDED',response_status=?,response_body=?::jsonb,resource_id=?,lease_until=NULL,version=version+1,updated_at=?,updated_by=? WHERE id=? AND version=?",response.status(),EventCodec.JSON.writeValueAsString(response.body()),response.resource(),stamp(now()),principal,id,generation);
                return response;
            });
        } catch(RuntimeException failure) {
            jdbc.update("UPDATE integration.idempotency_record SET state='FAILED_RETRYABLE',lease_until=NULL,version=version+1,updated_at=?,updated_by=? WHERE id=? AND version=? AND state='RUNNING'",stamp(now()),principal,id,generation); throw failure;
        }
    }
    private Response replay(Map<String,Object> row) {
        metrics.counter("nigson.idempotency.replay","owner",integration).increment();
        return new Response(((Number)row.get("response_status")).intValue(),EventCodec.JSON.readTree(row.get("response_body").toString()),(UUID)row.get("resource_id"));
    }
}
