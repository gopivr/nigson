package com.nigson.experience;

import com.nigson.experience.platform.*;
import com.nigson.shared.events.EventCodec;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class ConfigurationHttpTest {
    private static final String SCOPE="configuration.write_WITH_SIGNED_ADMIN";
    private static final String PATH="/api/v1/experience/staff/configuration/experience.worker.controls";
    @Test void actualSignedHttpEnforcesAdminOrgVersionReplayAndTypedWorkerConfiguration() throws Exception {
        String url=System.getenv("NIGSON_TEST_JDBC_URL"); Assumptions.assumeTrue(url!=null,"API-139 HTTP acceptance uses isolated native PostgreSQL");
        try(var fixture=new ExperienceHttpFixtures(url)) {
            var http=java.net.http.HttpClient.newHttpClient(); var jdbc=fixture.app().getBean(JdbcTemplate.class); UUID party=UUID.randomUUID();
            String input="{\"value\":{\"relayEnabled\":true,\"consumerEnabled\":true},\"active\":true,\"reason\":\"Synthetic worker configuration\"}";
            String admin=fixture.actor(party,"ADMIN",SCOPE,"PUT",PATH,input,null),sales=fixture.actor(party,"SALES",SCOPE,"PUT",PATH,input,null);
            String workload=fixture.workload(SCOPE,"nigson-commerce");
            assertThat(put(http,fixture,PATH,null,null,"\"0\"","unauth",input).statusCode()).isEqualTo(401);
            assertThat(put(http,fixture,PATH,fixture.workload(SCOPE,"wrong-client"),admin,"\"0\"","client",input).statusCode()).isEqualTo(401);
            assertThat(put(http,fixture,PATH,fixture.workload("audit.read_WITH_SIGNED_ADMIN","nigson-commerce"),admin,"\"0\"","scope",input).statusCode()).isEqualTo(401);
            assertThat(put(http,fixture,PATH,workload,sales,"\"0\"","role",input).statusCode()).isIn(401,403);
            String orgActor=fixture.actor(party,"ADMIN",SCOPE,"PUT",PATH,input,UUID.randomUUID());
            var organizationDenied=put(http,fixture,PATH,workload,orgActor,"\"0\"","org",input);
            assertThat(organizationDenied.statusCode()).withFailMessage(organizationDenied.body()).isIn(401,403);
            var signedOrg=fixture.verifyActor(orgActor,SCOPE,"PUT",PATH,input);
            var route=com.nigson.shared.security.PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-139")).findFirst().orElseThrow();
            var orgProof=new com.nigson.shared.security.PrivateWorkload.Proof("nigson-commerce","synthetic-workload",route,signedOrg);
            assertThatThrownBy(()->fixture.app().getBean(OwnerConfiguration.class).put(orgProof,"experience.worker.controls",0,EventCodec.JSON.readTree(input),UUID.randomUUID().toString(),UUID.fromString("10000000-0000-4000-8000-000000000139"))).hasMessage("ACCESS_DENIED");
            String secretInput=input.replace("true,\"consumerEnabled\":true","true,\"consumerEnabled\":true,\"secret\":\"must-not-persist\"");
            String secretActor=fixture.actor(party,"ADMIN",SCOPE,"PUT",PATH,secretInput,null);
            var secretRejected=put(http,fixture,PATH,workload,secretActor,"\"0\"","bad",secretInput);
            assertThat(secretRejected.statusCode()).isEqualTo(400); assertThat(secretRejected.body()).doesNotContain("must-not-persist");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.feature_configuration WHERE key='experience.worker.controls'",Integer.class)).isZero();
            assertThat(put(http,fixture,PATH+"?unexpected=1",workload,admin,"\"0\"","query",input).statusCode()).isEqualTo(400);
            String unknownPath="/api/v1/experience/staff/configuration/unregistered.key";
            String unknown=fixture.actor(party,"ADMIN",SCOPE,"PUT",unknownPath,input,null);
            assertThat(put(http,fixture,unknownPath,workload,unknown,"\"0\"","unknown",input).statusCode()).isEqualTo(422);

            String firstKey=UUID.randomUUID().toString(); var first=put(http,fixture,PATH,workload,admin,"\"0\"",firstKey,input);
            assertThat(first.statusCode()).withFailMessage(first.body()).isEqualTo(200); assertThat(first.headers().firstValue("ETag")).hasValue("\"1\""); assertThat(first.headers().firstValue("Cache-Control")).hasValue("no-store");
            assertThat(EventCodec.JSON.readTree(first.body()).propertyNames()).containsExactlyInAnyOrder("key","revision","active","version");
            String replayAdmin=fixture.actor(party,"ADMIN",SCOPE,"PUT",PATH,input,null);
            assertThat(put(http,fixture,PATH,workload,replayAdmin,"\"0\"",firstKey,input).body()).isEqualTo(first.body());
            assertThat(put(http,fixture,PATH,workload,replayAdmin,"\"0\"",UUID.randomUUID().toString(),input).statusCode()).isEqualTo(412);
            String changed=input.replace("Synthetic worker configuration","Changed worker configuration"); String changedAdmin=fixture.actor(party,"ADMIN",SCOPE,"PUT",PATH,changed,null);
            assertThat(put(http,fixture,PATH,workload,changedAdmin,"\"0\"",firstKey,changed).statusCode()).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.feature_configuration WHERE key='experience.worker.controls'",Integer.class)).isEqualTo(1);
            assertThat(fixture.app().getBean(WorkerControls.class).enabled("relayEnabled")).isTrue();

            String raceKey=UUID.randomUUID().toString();
            try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
                var futures=new ArrayList<Future<java.net.http.HttpResponse<String>>>();
                for(int n=0;n<12;n++) futures.add(workers.submit(()->{
                    for(int attempt=0;attempt<100;attempt++) {
                        try {
                            String signed=fixture.actor(party,"ADMIN",SCOPE,"PUT",PATH,input,null);
                            var response=put(http,fixture,PATH,workload,signed,"\"1\"",raceKey,input);
                            if(response.statusCode()==200)return response;
                            if(response.statusCode()!=409)throw new AssertionError("Unexpected racing response "+response.statusCode()+": "+response.body());
                            Thread.sleep(10);
                        } catch(Exception failure) { if(failure instanceof RuntimeException runtime)throw runtime; throw new RuntimeException(failure); }
                    }
                    throw new AssertionError("Idempotent API-139 race did not settle");
                }));
                String expected=null;
                for(var future:futures){var response=future.get(15,TimeUnit.SECONDS);if(expected==null)expected=response.body();else assertThat(response.body()).isEqualTo(expected);}
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.feature_configuration WHERE key='experience.worker.controls'",Integer.class)).isEqualTo(2);

            String paused="{\"value\":{\"relayEnabled\":false,\"consumerEnabled\":false},\"active\":true,\"reason\":\"Synthetic worker pause\"}";
            String pausedActor=fixture.actor(party,"ADMIN",SCOPE,"PUT",PATH,paused,null);
            assertThat(put(http,fixture,PATH,workload,pausedActor,"\"2\"",UUID.randomUUID().toString(),paused).statusCode()).isEqualTo(200);
            assertThat(fixture.app().getBean(WorkerControls.class).enabled("relayEnabled")).isFalse();
            assertThat(fixture.app().getBean(WorkerControls.class).enabled("consumerEnabled")).isFalse();

            UUID eventId=failedEvent(fixture, jdbc); UUID outboxId=jdbc.queryForObject("SELECT id FROM experience.outbox_event WHERE event_id=?",UUID.class,eventId);
            String replayPath="/api/v1/experience/staff/configuration/experience.queue.replay";
            String replayBody="{\"value\":{\"eventId\":\""+eventId+"\"},\"active\":true,\"reason\":\"Synthetic terminal queue replay\"}";
            String replayActor=fixture.actor(party,"ADMIN",SCOPE,"PUT",replayPath,replayBody,null); String replayKey=UUID.randomUUID().toString();
            var queued=put(http,fixture,replayPath,workload,replayActor,"\"0\"",replayKey,replayBody);
            assertThat(queued.statusCode()).withFailMessage(queued.body()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT state FROM experience.outbox_event WHERE id=?",String.class,outboxId)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT attempt_count FROM experience.outbox_event WHERE id=?",Integer.class,outboxId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.audit_record WHERE action='QUEUE_REPLAY' AND resource_id=?",Integer.class,outboxId)).isEqualTo(1);
            String staleReplayActor=fixture.actor(party,"ADMIN",SCOPE,"PUT",replayPath,replayBody,null);
            assertThat(put(http,fixture,replayPath,workload,staleReplayActor,"\"0\"",UUID.randomUUID().toString(),replayBody).statusCode()).isEqualTo(412);
            String queueReplayActor=fixture.actor(party,"ADMIN",SCOPE,"PUT",replayPath,replayBody,null);
            assertThat(put(http,fixture,replayPath,workload,queueReplayActor,"\"0\"",replayKey,replayBody).body()).isEqualTo(queued.body());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.audit_record WHERE action='QUEUE_REPLAY' AND resource_id=?",Integer.class,outboxId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.audit_record WHERE new_value::text LIKE '%Synthetic terminal queue replay%'",Integer.class)).isZero();
        }
    }
    private static UUID failedEvent(ExperienceHttpFixtures fixture, JdbcTemplate jdbc)throws Exception {
        var store=fixture.app().getBean(PlatformStore.class); var tx=new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        var envelope=(tools.jackson.databind.node.ObjectNode)EventCodec.JSON.readTree(Files.readString(Path.of(System.getenv("NIGSON_EVENT_FIXTURES"),"lead.created.v1.json")));
        UUID event=UUID.randomUUID(); envelope.put("event_id",event.toString()); envelope.put("occurred_at",java.time.Instant.now().toString()); String body=EventCodec.JSON.writeValueAsString(envelope);
        tx.execute(s->{store.append(body,"synthetic-platform-test");return null;}); UUID row=jdbc.queryForObject("SELECT id FROM experience.outbox_event WHERE event_id=?",UUID.class,event);
        var claim=store.claim(100,Duration.ofSeconds(30)).stream().filter(c->c.id().equals(row)).findFirst().orElseThrow();store.failed(claim,1);
        assertThat(jdbc.queryForObject("SELECT state FROM experience.outbox_event WHERE id=?",String.class,row)).isEqualTo("FAILED");return event;
    }
    private static java.net.http.HttpResponse<String> put(java.net.http.HttpClient http,ExperienceHttpFixtures f,String path,String workload,String actor,String match,String key,String body)throws Exception {
        var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(f.base()+path)).timeout(Duration.ofSeconds(10)).header("X-Correlation-ID","10000000-0000-4000-8000-000000000139").header("Content-Type","application/json").header("If-Match",match).header("Idempotency-Key",key);
        if(workload!=null)request.header("Authorization","Bearer "+workload); if(actor!=null)request.header("X-Nigson-Actor",actor);
        return http.send(request.PUT(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
    }
}
