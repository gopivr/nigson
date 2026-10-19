package com.nigson.experience.content;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.nigson.experience.platform.AuditWriter;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.security.PrivateWorkload;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.assertj.core.api.Assertions.*;

class ContentKnowledgePersistenceTest {
    private JdbcTemplate jdbc;
    private ContentKnowledgeService service;
    private final UUID item=UUID.randomUUID();
    private final UUID party=UUID.randomUUID();

    @BeforeEach void setup() {
        String url=System.getenv("NIGSON_TEST_JDBC_URL");
        Assumptions.assumeTrue(url!=null,"API-125/126 acceptance requires isolated PostgreSQL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/nigson")) throw new IllegalArgumentException("LOCAL_EXPERIENCE_DB_REQUIRED");
        var ds=new DriverManagerDataSource(url+"?currentSchema=experience","nigson_experience_app","mock-experience-runtime");
        jdbc=new JdbcTemplate(ds); var manager=new DataSourceTransactionManager(ds); var metrics=new SimpleMeterRegistry();
        metrics.config().meterFilter(new com.nigson.experience.platform.MetricPolicy());
        service=new ContentKnowledgeService(jdbc,new ContentKnowledgeRepository(jdbc),new AuditWriter(jdbc,java.time.Clock.systemUTC(),metrics),manager,metrics);
    }

    @Test void exactWorkloadRouteValidatesCurrentApprovedRevisionAndFailsClosedAfterWithdrawal() {
        seedItem(item,"article", "ACTIVE"); seedRevision(item,1,"PUBLISHED",UUID.randomUUID(),Instant.now().minusSeconds(90),null); seedRevision(item,2,"PUBLISHED",UUID.randomUUID(),Instant.now().minusSeconds(30),null); point(item,2);
        UUID draft=UUID.randomUUID();seedItem(draft,"draft","ACTIVE");seedRevision(draft,1,"DRAFT",null,null,null);
        UUID archived=UUID.randomUUID();seedItem(archived,"archived","ARCHIVED");seedRevision(archived,1,"PUBLISHED",UUID.randomUUID(),Instant.now().minusSeconds(20),null);point(archived,1);
        UUID future=UUID.randomUUID();seedItem(future,"future","ACTIVE");seedRevision(future,1,"PUBLISHED",UUID.randomUUID(),Instant.now().plusSeconds(600),Instant.now().plusSeconds(600));point(future,1);
        var valid=proof("API-125","knowledge.validate");var read=proof("API-126","knowledge.read");var denied=proof("API-126","knowledge.validate");
        var statuses=service.validate(valid,new ContentKnowledgeValues.ValidationInput(java.util.List.of(
                new ContentKnowledgeValues.RequestedRevision(item,1),new ContentKnowledgeValues.RequestedRevision(item,2),
                new ContentKnowledgeValues.RequestedRevision(draft,1),new ContentKnowledgeValues.RequestedRevision(archived,1),
                new ContentKnowledgeValues.RequestedRevision(future,1),new ContentKnowledgeValues.RequestedRevision(UUID.randomUUID(),1))),UUID.randomUUID()).items();
        assertThat(statuses).extracting(ContentKnowledgeValues.ValidatedRevision::approved).containsExactly(true,true,false,false,false,false);
        assertThat(statuses).extracting(ContentKnowledgeValues.ValidatedRevision::current).containsExactly(false,true,false,false,false,false);
        var content=service.read(read,item,UUID.randomUUID());assertThat(content.revision()).isEqualTo(2);assertThat(content.visibility()).isEqualTo("PUBLIC");assertThat(content.approved()).isTrue();assertThat(content.body().toString()).contains("Published");
        assertThatThrownBy(()->service.read(denied,item,UUID.randomUUID())).isInstanceOf(Problem.Failure.class).extracting(e->((Problem.Failure)e).code).isEqualTo(ProblemCode.ACCESS_DENIED);
        jdbc.update("UPDATE experience.content_item SET current_published_revision_id=NULL,version=version+1 WHERE id=?",item);
        jdbc.update("UPDATE experience.content_revision SET state='WITHDRAWN',version=version+1 WHERE content_item_id=? AND revision=2",item);
        assertThat(service.validate(valid,new ContentKnowledgeValues.ValidationInput(java.util.List.of(new ContentKnowledgeValues.RequestedRevision(item,2))),UUID.randomUUID()).items().getFirst().current()).isFalse();
        assertThatThrownBy(()->service.read(read,item,UUID.randomUUID())).isInstanceOf(Problem.Failure.class).extracting(e->((Problem.Failure)e).code).isEqualTo(ProblemCode.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.outbox_event WHERE aggregate_id=? AND event_type IN ('content.published.v1','content.withdrawn.v1')",Long.class,item)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.audit_record WHERE resource_type='CONTENT' AND resource_id=? AND action IN ('CONTENT_REVISION_VALIDATE','CONTENT_KNOWLEDGE_READ')",Long.class,item)).isGreaterThanOrEqualTo(4);
    }

    private PrivateWorkload.Proof proof(String api,String scope) {
        var route=PrivateWorkload.ROUTES.stream().filter(value->value.api().equals(api)).findFirst().orElseThrow();
        return new PrivateWorkload.Proof("nigson-ai","synthetic-ai-workload",route,null);
    }
    private void seedItem(UUID id,String slug,String state) {
        jdbc.update("INSERT INTO experience.content_item(id,created_by,updated_by,content_type,slug,locale,status) VALUES (?,'content-test','content-test','BLOG',?,'en-NG',?)",id,slug+"-"+id.toString().substring(0,8),state);
    }
    private void seedRevision(UUID id,int revision,String state,UUID approved,Instant published,Instant scheduled) {
        String checksum="a".repeat(64);
        jdbc.update("INSERT INTO experience.content_revision(id,created_by,updated_by,content_item_id,revision,title,body,metadata,state,scheduled_at,published_at,approved_by,checksum_sha256) VALUES (?,'content-test','content-test',?,?,?,?::jsonb,?::jsonb,?,?,?,?,?)",
                UUID.randomUUID(),id,revision,"Title "+revision,"{\"schemaVersion\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"Published text\"}]}","{\"title\":\"Public title\",\"description\":\"Public description\",\"canonicalUrl\":null,\"robots\":\"INDEX\",\"alternates\":[],\"structuredData\":{}}",state,scheduled==null?null:java.sql.Timestamp.from(scheduled),published==null?null:java.sql.Timestamp.from(published),approved,checksum);
    }
    private void point(UUID id,int revision) { jdbc.update("UPDATE experience.content_item SET current_published_revision_id=(SELECT id FROM experience.content_revision WHERE content_item_id=? AND revision=?) WHERE id=?",id,revision,id); }
}
