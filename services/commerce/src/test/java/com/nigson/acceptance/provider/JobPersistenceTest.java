package com.nigson.acceptance.provider;
import com.nigson.commerce.provider.*;
import com.nigson.commerce.platform.AuditWriter;
import com.nigson.shared.security.ActorAssertion;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
class JobPersistenceTest {
    JdbcTemplate jdbc;JobCommands commands;UUID operator,job;ActorAssertion.Actor actor;AtomicLong seconds=new AtomicLong();
    @BeforeEach void setup(){String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"provider-check supplies isolated owner PostgreSQL");if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");var ds=new DriverManagerDataSource(url+"?currentSchema=commerce","nigson_commerce_app","mock-commerce-runtime");jdbc=new JdbcTemplate(ds);Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return Instant.now().plusSeconds(seconds.get());}};commands=new JobCommands(jdbc,new DataSourceTransactionManager(ds),clock,new AuditWriter(jdbc,clock,new SimpleMeterRegistry()));operator=UUID.randomUUID();job=UUID.randomUUID();String issuer="https://identity.synthetic.invalid/realms/test";
        jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic operations')",operator,issuer,operator.toString(),operator+"@example.invalid");jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,'OPERATIONS',?)",UUID.randomUUID(),operator,operator);actor=new ActorAssertion.Actor(operator,issuer,operator.toString(),null,Set.of("OPERATIONS"),Set.of(),clock.instant());
    }
    @AfterEach void cleanup(){if(jdbc!=null){jdbc.update("DELETE FROM commerce.idempotency_record WHERE principal_key=?",operator.toString());jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id=?",operator);jdbc.update("DELETE FROM commerce.party WHERE id=?",operator);}}
    JobCommands.Intent claim(String key){return commands.claim(actor,job,0,new JobValues.Retry("Synthetic retry"),key,UUID.randomUUID());}
    IntegrationJobs.Accepted accepted(){return new IntegrationJobs.Accepted(new JobValues.Job(job,"PENDING",1,Instant.now(),null),1);}
    @Test void unknownAndExpiredLeasesPreserveExactCommandIdentityThenReplayAcknowledgement(){
        var first=claim("key");assertThatThrownBy(()->claim("key")).hasMessage("IDEMPOTENCY_IN_PROGRESS");commands.unknown(first,actor,job,UUID.randomUUID());assertThat(jdbc.queryForObject("SELECT response_body->>'outcome' FROM commerce.idempotency_record WHERE id=?",String.class,first.command())).isEqualTo("UNKNOWN");var next=claim("key");assertThat(next.command()).isEqualTo(first.command());seconds.set(31);var expired=claim("key");assertThat(expired.command()).isEqualTo(first.command());var response=accepted();commands.finish(expired,actor,job,UUID.randomUUID(),response,null);assertThat(claim("key").completed()).isEqualTo(response);
        assertThatThrownBy(()->commands.claim(actor,job,1,new JobValues.Retry("Synthetic retry"),"key",UUID.randomUUID())).hasMessage("IDEMPOTENCY_HASH_MISMATCH");
    }
    @Test void currentRoleIdentityOrganizationAndMfaAreCheckedBeforeCachedResponse(){var first=claim("role");commands.finish(first,actor,job,UUID.randomUUID(),accepted(),null);jdbc.update("UPDATE commerce.staff_role_assignment SET revoked_at=now() WHERE party_id=?",operator);assertThatThrownBy(()->claim("role")).hasMessage("ACTOR_TRUST_REJECTED");assertThatThrownBy(()->commands.authority(new ActorAssertion.Actor(operator,actor.identityIssuer(),actor.identitySubject(),null,Set.of("OPERATIONS"),Set.of(),Instant.now().minusSeconds(901)),true)).hasMessage("ACTOR_TRUST_REJECTED");assertThatThrownBy(()->commands.authority(new ActorAssertion.Actor(operator,actor.identityIssuer(),actor.identitySubject(),UUID.randomUUID(),Set.of("OPERATIONS"),Set.of(),Instant.now()),false)).hasMessage("ACTOR_TRUST_REJECTED");}
    @Test void twelveConcurrentClaimsAuthorizeOnlyOneCallerAndOneIntent()throws Exception{var start=new CyclicBarrier(12);try(var pool=Executors.newVirtualThreadPerTaskExecutor()){var futures=new ArrayList<Future<Boolean>>();for(int i=0;i<12;i++)futures.add(pool.submit(()->{start.await();try{claim("race");return true;}catch(com.nigson.shared.problem.Problem.Failure busy){assertThat(busy.code.name()).isEqualTo("IDEMPOTENCY_IN_PROGRESS");return false;}}));int claims=0;for(var f:futures)if(f.get(10,TimeUnit.SECONDS))claims++;assertThat(claims).isEqualTo(1);}assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.idempotency_record WHERE principal_key=?",Integer.class,operator.toString())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE created_by=?",Integer.class,operator.toString())).isEqualTo(1);}
    @Test void auditFailureRollsBackIntentBeforeAnyOutboundCommand(){var admin=new JdbcTemplate(new DriverManagerDataSource(System.getenv("NIGSON_TEST_JDBC_URL"),"nigson_bootstrap","mock-bootstrap"));admin.execute("REVOKE INSERT ON commerce.audit_record FROM nigson_commerce_app");try{assertThatThrownBy(()->claim("audit")).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.idempotency_record WHERE principal_key=?",Integer.class,operator.toString())).isZero();}finally{admin.execute("GRANT INSERT ON commerce.audit_record TO nigson_commerce_app");}assertThat(claim("audit").command()).isNotNull();}
    @Test void acceptedCommandSurvivesFinalAuditFailureAndReconcilesWithSameIdentity(){
        var intent=claim("final-audit");var response=accepted();var admin=new JdbcTemplate(new DriverManagerDataSource(System.getenv("NIGSON_TEST_JDBC_URL"),"nigson_bootstrap","mock-bootstrap"));admin.execute("REVOKE INSERT ON commerce.audit_record FROM nigson_commerce_app");
        try{assertThatThrownBy(()->commands.finish(intent,actor,job,UUID.randomUUID(),response,null)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(jdbc.queryForObject("SELECT state FROM commerce.idempotency_record WHERE id=?",String.class,intent.command())).isEqualTo("RUNNING");assertThat(jdbc.queryForObject("SELECT response_body->>'outcome' FROM commerce.idempotency_record WHERE id=?",String.class,intent.command())).isEqualTo("CALLING");}
        finally{admin.execute("GRANT INSERT ON commerce.audit_record TO nigson_commerce_app");}
        seconds.set(31);var reconciliation=claim("final-audit");assertThat(reconciliation.command()).isEqualTo(intent.command());commands.finish(reconciliation,actor,job,UUID.randomUUID(),response,null);assertThat(claim("final-audit").completed()).isEqualTo(response);
    }
    @Test void disabledIntegrationCreatesNoRetryIntent(){
        var inactive=new JobConfiguration().integrationJobs(new org.springframework.mock.env.MockEnvironment(),null);var service=new JobService(commands,inactive,null,new SimpleMeterRegistry());
        assertThatThrownBy(()->service.retry(actor,"synthetic-session",job,0,new JobValues.Retry("Synthetic retry"),"disabled",UUID.randomUUID())).hasMessage("FEATURE_DISABLED");assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.idempotency_record WHERE principal_key=?",Integer.class,operator.toString())).isZero();
    }
}
