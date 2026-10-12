package com.nigson.acceptance.provider;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.nigson.integration.provider.*;
import com.nigson.integration.platform.*;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;
import static org.assertj.core.api.Assertions.*;

class IntegrationJobPersistenceTest {
 private JdbcTemplate jdbc,admin;private TransactionTemplate tx;private JobRepository jobs;private JobService service;private Clock clock;private final List<UUID> ids=new ArrayList<>();
 @BeforeEach void setup(){
  String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"native isolated owned PostgreSQL required");if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_INTEGRATION_DB_REQUIRED");
  var ds=new DriverManagerDataSource(url+"?currentSchema=integration","nigson_integration_app","mock-integration-runtime");jdbc=new JdbcTemplate(ds);admin=new JdbcTemplate(new DriverManagerDataSource(url,"nigson_bootstrap","mock-bootstrap"));var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);clock=Clock.systemUTC();var metrics=new SimpleMeterRegistry();jobs=new JdbcJobRepository(jdbc,clock);service=new JobService(jobs,new PlatformStore(jdbc,manager,clock,metrics),new AuditWriter(jdbc,clock,metrics),manager,clock);
 }
 @AfterEach void clean(){if(jdbc!=null)for(UUID id:ids)jdbc.update("DELETE FROM integration.integration_job WHERE id=?",id);ids.clear();}
 private PrivateWorkload.Route route(String api){return PrivateWorkload.ROUTES.stream().filter(r->r.api().equals(api)).findFirst().orElseThrow();}
 private PrivateWorkload.Proof read(){return new PrivateWorkload.Proof("nigson-commerce","synthetic-workload",route("API-140"),null);}
 private PrivateWorkload.Proof retry(Instant mfa,Set<String> roles){var actor=new ActorAssertion.Actor(UUID.randomUUID(),"https://identity.example.invalid/realms/synthetic","synthetic-operator",null,roles,Set.of(route("API-141").scope()),mfa);return new PrivateWorkload.Proof("nigson-commerce","synthetic-workload",route("API-141"),actor);}
 private JobRepository.Record enqueue(){var saved=tx.execute(s->jobs.enqueue(JobValues.Kind.PAYMENT_RECONCILE,"payment:"+UUID.randomUUID(),"commerce",UUID.randomUUID()));ids.add(saved.id());return saved;}
 private JobRepository.Record failed(){var saved=enqueue();jdbc.update("UPDATE integration.integration_job SET state='FAILED',attempt_count=3,error_code='PROVIDER_UNAVAILABLE' WHERE id=?",saved.id());return jobs.find(saved.id(),false);}
 private void code(Runnable action,ProblemCode expected){assertThatThrownBy(action::run).isInstanceOfSatisfying(Problem.Failure.class,e->assertThat(e.code).isEqualTo(expected));}
 @Test void localRetryAndAuditRollbackTogetherThenSameCommandRecovers(){
  var job=failed();var proof=retry(clock.instant(),Set.of("OPERATIONS"));String key=UUID.randomUUID().toString();var body=new JobValues.Retry("Preserve durable evidence when audit fails");admin.execute("REVOKE INSERT ON integration.audit_record FROM nigson_integration_app");try{assertThatThrownBy(()->service.retry(proof,job.id(),0,body,key,UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);}finally{admin.execute("GRANT INSERT ON integration.audit_record TO nigson_integration_app");}
  assertThat(jobs.find(job.id(),false).state()).isEqualTo(JobValues.State.FAILED);assertThat(jobs.find(job.id(),false).version()).isZero();assertThat(service.retry(proof,job.id(),0,body,key,UUID.randomUUID()).version()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.audit_record WHERE resource_id=?",Integer.class,job.id())).isEqualTo(1);
 }
 @Test void sheetJobRetryRetainsOriginalBusinessIdentityAndRefusesUnknownWrite(){
  UUID resource=UUID.randomUUID();String business="sheet-sync:"+resource;var saved=tx.execute(s->jobs.enqueue(JobValues.Kind.SHEETS_SYNC,business,"experience",resource));ids.add(saved.id());jdbc.update("UPDATE integration.integration_job SET state='FAILED',attempt_count=2,error_code='PROVIDER_UNAVAILABLE' WHERE id=?",saved.id());var proof=retry(clock.instant(),Set.of("OPERATIONS"));String key=UUID.randomUUID().toString();var body=new JobValues.Retry("Recover original Sheet job without another business key");var ack=service.retry(proof,saved.id(),0,body,key,UUID.randomUUID());assertThat(service.retry(proof,saved.id(),0,body,key,UUID.randomUUID())).isEqualTo(ack);assertThat(jobs.find(saved.id(),false).businessKey()).isEqualTo(business);assertThat(jobs.find(saved.id(),false).attempts()).isEqualTo(2);assertThat(tx.execute(s->jobs.enqueue(JobValues.Kind.SHEETS_SYNC,business,"experience",resource)).id()).isEqualTo(saved.id());jdbc.update("UPDATE integration.integration_job SET state='UNKNOWN',version=2 WHERE id=?",saved.id());code(()->service.retry(proof,saved.id(),2,body,UUID.randomUUID().toString(),UUID.randomUUID()),ProblemCode.PROVIDER_OUTCOME_UNKNOWN);assertThat(jobs.find(saved.id(),false).state()).isEqualTo(JobValues.State.UNKNOWN);
 }
 @Test void stableEnqueueDeduplicatesWithoutChangingOriginalAssociation(){
  UUID resource=UUID.randomUUID();String key="payment:"+UUID.randomUUID();var first=tx.execute(s->jobs.enqueue(JobValues.Kind.PAYMENT_RECONCILE,key,"commerce",resource));ids.add(first.id());var second=tx.execute(s->jobs.enqueue(JobValues.Kind.PAYMENT_RECONCILE,key,"commerce",resource));assertThat(second.id()).isEqualTo(first.id());assertThat(second.version()).isZero();
  code(()->tx.execute(s->jobs.enqueue(JobValues.Kind.SHEETS_SYNC,key,"experience",resource)),ProblemCode.IDEMPOTENCY_HASH_MISMATCH);assertThat(jobs.find(first.id(),false).kind()).isEqualTo(JobValues.Kind.PAYMENT_RECONCILE);
 }
 @Test void signedRetryAndReplayPreserveBusinessKeyAttemptsAndOneAtomicAudit(){
  var failed=failed();var proof=retry(clock.instant(),Set.of("OPERATIONS"));String key=UUID.randomUUID().toString();var body=new JobValues.Retry("Inspect the original durable operation");var accepted=service.retry(proof,failed.id(),0,body,key,UUID.randomUUID());assertThat(accepted.job().state()).isEqualTo("PENDING");assertThat(accepted.version()).isEqualTo(1);assertThat(accepted.job().attemptCount()).isEqualTo(3);assertThat(jobs.find(failed.id(),false).businessKey()).isEqualTo(failed.businessKey());
  assertThat(service.retry(proof,failed.id(),0,body,key,UUID.randomUUID())).isEqualTo(accepted);assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.audit_record WHERE resource_id=? AND action='QUEUE_REPLAY'",Integer.class,failed.id())).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT response_body::text FROM integration.idempotency_record WHERE request_key=?",String.class,key)).doesNotContain(body.reason()).doesNotContain(failed.businessKey());
  assertThatThrownBy(()->service.retry(proof,failed.id(),0,new JobValues.Retry("Changed operator intention"),key,UUID.randomUUID())).isInstanceOf(PlatformStore.Conflict.class).hasMessage("IDEMPOTENCY_HASH_MISMATCH");
 }
 @Test void unknownAndClaimedOutcomesNeverBecomeAnotherAttempt(){
  var saved=failed();jdbc.update("UPDATE integration.integration_job SET state='UNKNOWN' WHERE id=?",saved.id());var proof=retry(clock.instant(),Set.of("OPERATIONS"));code(()->service.retry(proof,saved.id(),0,new JobValues.Retry("Reconcile before another side effect"),UUID.randomUUID().toString(),UUID.randomUUID()),ProblemCode.PROVIDER_OUTCOME_UNKNOWN);
  assertThat(jobs.find(saved.id(),false).state()).isEqualTo(JobValues.State.UNKNOWN);assertThat(jobs.find(saved.id(),false).attempts()).isEqualTo(3);jdbc.update("UPDATE integration.integration_job SET state='CLAIMED',lease_until=now()+interval '1 minute' WHERE id=?",saved.id());code(()->service.retry(proof,saved.id(),0,new JobValues.Retry("Cannot bypass a current execution claim"),UUID.randomUUID().toString(),UUID.randomUUID()),ProblemCode.INVALID_TRANSITION);
 }
 @Test void wrongProofRolesAndStaleMfaAreDeniedBeforeCachedAcknowledgement(){
  var saved=failed();var proof=retry(clock.instant(),Set.of("OPERATIONS"));String key=UUID.randomUUID().toString();var body=new JobValues.Retry("Original staff command");service.retry(proof,saved.id(),0,body,key,UUID.randomUUID());var stale=new PrivateWorkload.Proof(proof.client(),proof.subject(),proof.route(),new ActorAssertion.Actor(proof.actor().party(),proof.actor().identityIssuer(),proof.actor().identitySubject(),null,Set.of("OPERATIONS"),proof.actor().scopes(),clock.instant().minusSeconds(901)));
  assertThatThrownBy(()->service.retry(stale,saved.id(),0,body,key,UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  assertThatThrownBy(()->service.retry(retry(clock.instant(),Set.of("ADMIN")),saved.id(),0,body,key,UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  assertThatThrownBy(()->service.list(new PrivateWorkload.Proof("nigson-experience","other",route("API-140"),null),new JobValues.Query(null,null,0,20,"id,asc"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
 }
 @Test void competingVersionsQueueExactlyOnce()throws Exception{
  var saved=failed();var proof=retry(clock.instant(),Set.of("OPERATIONS"));try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var tasks=new ArrayList<Future<String>>();for(int i=0;i<2;i++)tasks.add(pool.submit(()->{start.await();try{service.retry(proof,saved.id(),0,new JobValues.Retry("Competing staff request"),UUID.randomUUID().toString(),UUID.randomUUID());return "accepted";}catch(Problem.Failure failure){return failure.code.name();}}));start.countDown();var results=new ArrayList<String>();for(var result:tasks)results.add(result.get(10,TimeUnit.SECONDS));assertThat(results).containsExactlyInAnyOrder("accepted","VERSION_MISMATCH");}
  assertThat(jobs.find(saved.id(),false).version()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.audit_record WHERE resource_id=?",Integer.class,saved.id())).isEqualTo(1);
 }
 @Test void boundedFilteredQueriesReturnOnlySafeViews(){
  var first=enqueue();var second=failed();var page=service.list(read(),new JobValues.Query(JobValues.Kind.PAYMENT_RECONCILE,JobValues.State.FAILED,0,1,"nextAttemptAt,asc"));assertThat(page.items()).hasSize(1);assertThat(page.items().getFirst().id()).isEqualTo(second.id());assertThat(page.totalElements()).isEqualTo(1);assertThat(page.totalPages()).isEqualTo(1);assertThat(com.nigson.shared.events.EventCodec.JSON.writeValueAsString(page)).doesNotContain(first.businessKey()).doesNotContain("resource_id");
  code(()->service.retry(retry(clock.instant(),Set.of("OPERATIONS")),UUID.randomUUID(),0,new JobValues.Retry("Missing job denial"),UUID.randomUUID().toString(),UUID.randomUUID()),ProblemCode.NOT_FOUND);
 }
}
