package com.nigson.experience.reporting;

import com.nigson.experience.platform.AuditWriter;
import com.nigson.experience.platform.JdbcTrace;
import com.nigson.shared.security.ActorAssertion;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import static org.assertj.core.api.Assertions.*;

class ReportPersistenceTest {
    static final java.util.concurrent.atomic.AtomicInteger WINDOW=new java.util.concurrent.atomic.AtomicInteger();
    JdbcTemplate jdbc; ReportService service; SimpleMeterRegistry metrics; ActorAssertion.Actor analyst; Instant from,to; UUID campaign; long auditBefore;
    @BeforeEach void setup(){
        String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"API-110 acceptance requires isolated Experience PostgreSQL");if(!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_EXPERIENCE_DB_REQUIRED");
        metrics=new SimpleMeterRegistry();metrics.config().meterFilter(new com.nigson.experience.platform.MetricPolicy());var ds=new JdbcTrace(new DriverManagerDataSource(url+"?currentSchema=experience","nigson_experience_app","mock-experience-runtime"),()->metrics);jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);var audit=new AuditWriter(jdbc,Clock.systemUTC(),metrics);service=new ReportService(jdbc,new JdbcReportRepository(jdbc),audit,metrics,manager);
        from=Instant.parse("2035-01-01T05:00:00Z").plus(Duration.ofDays(WINDOW.getAndIncrement()*2L));to=from.plus(Duration.ofDays(1));analyst=new ActorAssertion.Actor(UUID.randomUUID(),"https://identity.synthetic.invalid","synthetic-analyst",null,Set.of("ANALYST"),Set.of("report.read_WITH_SIGNED_ANALYST"),null);auditBefore=jdbc.queryForObject("SELECT count(*) FROM experience.audit_record WHERE action='REPORT_READ'",Long.class);
        seed(from.minusMillis(1),"OUTSIDE_BEFORE");seed(from,"INSIDE_START");seed(to.minusMillis(1),"INSIDE_END");seed(to,"OUTSIDE_END");
    }
    private void seed(Instant created,String suffix){
        UUID lead=UUID.randomUUID();jdbc.update("INSERT INTO experience.lead(id,created_at,created_by,updated_by,type,name,email,subject,message,status,source) VALUES (?,?,'synthetic','synthetic','CONTACT','Private Name','private@example.invalid','Private subject','Private message','NEW','SYNTHETIC')",lead,Timestamp.from(created));
        UUID opportunity=UUID.randomUUID();jdbc.update("INSERT INTO experience.career_opportunity(id,created_at,created_by,updated_by,title,slug,kind,description,fields_schema,state) VALUES (?,?,'synthetic','synthetic','Synthetic role',?,'JOB','private description','{\"schemaVersion\":1,\"fields\":[]}'::jsonb,'PUBLISHED')",opportunity,Timestamp.from(created),"report-"+UUID.randomUUID());
        jdbc.update("INSERT INTO experience.career_application(id,created_at,created_by,updated_by,opportunity_id,name,email_ciphertext,email_hash,answers,form_revision,state,retention_until) VALUES (?,?,'synthetic','synthetic',?,'Private Applicant',?::bytea,?,'{}'::jsonb,1,'SUBMITTED',?)",UUID.randomUUID(),Timestamp.from(created),opportunity,new byte[]{4,5,6},UUID.randomUUID().toString().replace("-","0").substring(0,32)+"a".repeat(32),Timestamp.from(Instant.parse("2040-01-01T00:00:00Z")));
        jdbc.update("INSERT INTO experience.review(id,created_at,created_by,updated_by,party_id,kind,body,state) VALUES (?,?,'synthetic','synthetic',?,'TESTIMONIAL','Private review body','PENDING')",UUID.randomUUID(),Timestamp.from(created),UUID.randomUUID());
        jdbc.update("INSERT INTO experience.newsletter_subscription(id,created_at,created_by,updated_by,email_ciphertext,email_hash,state,unsubscribe_token_hash) VALUES (?,?,'synthetic','synthetic',?::bytea,?,'ACTIVE',?)",UUID.randomUUID(),Timestamp.from(created),new byte[]{1,2,3},UUID.randomUUID().toString().replace("-","0").substring(0,32)+"b".repeat(32),UUID.randomUUID().toString().replace("-","0").substring(0,32)+"c".repeat(32));
        if(suffix.equals("INSIDE_START")){campaign=UUID.randomUUID();jdbc.update("INSERT INTO experience.campaign(id,created_by,updated_by,code,title,market_id,display_config,starts_at,ends_at,published) VALUES (?,'synthetic','synthetic',?,'Synthetic campaign',?,'{}'::jsonb,?,?,true)",campaign,"report-"+UUID.randomUUID().toString().replace("-",""),UUID.randomUUID(),Timestamp.from(created),Timestamp.from(created.plusSeconds(3600)));}
    }
    @Test void aggregatesOnlyUtcHalfOpenWindowAndAuditsWithoutDisclosingPrivateData(){
        var result=service.engagement(analyst,new ReportValues.Query(from,to));
        assertThat(result.leadsByState()).containsEntry("NEW",2L);assertThat(result.applicationsByState()).containsEntry("SUBMITTED",2L);assertThat(result.reviewsByState()).containsEntry("PENDING",2L);assertThat(result.subscriptions()).isEqualTo(2);assertThat(result.campaigns()).containsExactly(new ReportValues.Campaign(campaign,0));
        assertThat(result.toString()).doesNotContain("Private Applicant","private@example.invalid","Private Name","Private review body","Private subject","Private message");
        assertThatThrownBy(()->jdbc.queryForObject("SELECT count(*) FROM commerce.sales_order",Long.class)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.audit_record WHERE action='REPORT_READ'",Long.class)).isEqualTo(auditBefore+1);assertThat(metrics.get("nigson.reporting.queries").tag("owner","experience").tag("report","engagement").counter().count()).isEqualTo(1);
    }
    @Test void requiresExactAnalystAndBoundedDateRange(){
        var sales=new ActorAssertion.Actor(analyst.party(),analyst.identityIssuer(),analyst.identitySubject(),null,Set.of("SALES"),analyst.scopes(),null);assertThatThrownBy(()->service.engagement(sales,new ReportValues.Query(from,to))).hasMessage("ACCESS_DENIED");
        var org=new ActorAssertion.Actor(analyst.party(),analyst.identityIssuer(),analyst.identitySubject(),UUID.randomUUID(),analyst.roles(),analyst.scopes(),null);assertThatThrownBy(()->service.engagement(org,new ReportValues.Query(from,to))).hasMessage("ACCESS_DENIED");
        assertThatThrownBy(()->new ReportValues.Query(to,from)).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->new ReportValues.Query(from,from.plus(Duration.ofDays(367)))).hasMessage("VALIDATION_ERROR");
    }
}
