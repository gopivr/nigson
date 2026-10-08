package com.nigson.experience;
import com.nigson.experience.platform.*;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.observability.WireContext;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class AuditPersistenceTest {
 private JdbcTemplate jdbc;private AuditWriter audit;private TransactionTemplate tx;private SimpleMeterRegistry metrics;
 @BeforeEach void setup(){String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"make observability-check supplies isolated real PostgreSQL");if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");metrics=new SimpleMeterRegistry();metrics.config().meterFilter(new MetricPolicy());var source=new JdbcTrace(new DriverManagerDataSource(url+"?currentSchema=experience","nigson_experience_app","mock-experience-runtime"),()->metrics);jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));audit=new AuditWriter(jdbc,Clock.systemUTC(),metrics);}
 private UUID write(UUID resource,UUID correlation){return audit.append("opaque-test-subject",AuditValues.ActorKind.USER,AuditValues.Action.FEATURE_ACTIVATION,AuditValues.Resource.FEATURE,resource,correlation,AuditValues.Reason.CONFIGURATION_CHANGE,Map.of("enabled",false,"email","private@example.invalid"),Map.of("enabled",true,"token","private-token-fixture"),AuditValues.ipFingerprint(new byte[32],"127.0.0.1"));}
 @Test void auditAndEffectCommitTogetherRedactAndRemainAppendOnly(){
  UUID resource=EventCodec.uuid7(Instant.now());UUID correlation=UUID.randomUUID();assertThatThrownBy(()->write(resource,correlation)).isInstanceOf(IllegalStateException.class);
  UUID id=tx.execute(s->{jdbc.update("INSERT INTO experience.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (?,'test','test',?,1,'{}')",resource,"audit-"+resource);return write(resource,correlation);});
  var row=jdbc.queryForMap("SELECT * FROM experience.audit_record WHERE id=?",id);assertThat(row.get("correlation_id")).isEqualTo(correlation);assertThat(row.get("actor_subject")).isEqualTo("opaque-test-subject");assertThat(row.get("old_value").toString()+row.get("new_value").toString()).doesNotContain("private","email","token");assertThat(row.get("ip_hash").toString()).hasSize(64);
  assertThatThrownBy(()->jdbc.update("UPDATE experience.audit_record SET reason='tampered' WHERE id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThatThrownBy(()->jdbc.update("DELETE FROM experience.audit_record WHERE id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(metrics.get("nigson.audit.persisted").counter().count()).isEqualTo(1);
 }
 @Test void commandAndAuditRollbackAndJdbcTracesExcludeSqlValues(){
  UUID resource=EventCodec.uuid7(Instant.now());UUID correlation=UUID.randomUUID();var exporter=InMemorySpanExporter.create();
  try(var telemetry=new Telemetry(metrics,"test",exporter);var observed=telemetry.http(correlation,null,"/api/v1/checkout/attempts","POST")){
   assertThatThrownBy(()->tx.execute(s->{jdbc.update("INSERT INTO experience.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (?,'test','test',?,1,'{}')",resource,"rollback-"+resource);write(resource,correlation);jdbc.update("INSERT INTO experience.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (?,'test','test',?,-1,'{}')",EventCodec.uuid7(Instant.now()),"private-token-fixture");return null;})).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
   assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.feature_configuration WHERE id=?",Long.class,resource)).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.audit_record WHERE resource_id=?",Long.class,resource)).isZero();assertThat(metrics.find("nigson.audit.persisted").counter()).isNull();observed.failed("CONFLICT");
   telemetry.flush();assertThat(exporter.getFinishedSpanItems()).anySatisfy(span->{assertThat(span.getName()).isEqualTo("db.execute");assertThat(span.getTraceId()).isEqualTo(observed.span.getSpanContext().getTraceId());assertThat(span.getAttributes().toString()).doesNotContain("private","SELECT","WHERE");});assertThat(metrics.get("nigson.db.query").timers()).isNotEmpty();assertThat(WireContext.current().correlationId).isEqualTo(correlation);
  }
 }
}
