package com.nigson.acceptance.configuration;

import com.nigson.commerce.platform.*;
import com.nigson.commerce.catalogue.CatalogueMetadataPolicy;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.problem.*;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class ConfigurationPersistenceTest {
    JdbcTemplate jdbc;OwnerConfiguration service;UUID admin;String key;Clock clock=Clock.systemUTC();
    String issuer="https://identity.synthetic.invalid/realms/test";
    @BeforeEach void setup(){
        String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"configuration-check supplies isolated PostgreSQL");
        if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");
        var ds=new DriverManagerDataSource(url+"?currentSchema=commerce","nigson_commerce_app","mock-commerce-runtime");jdbc=new JdbcTemplate(ds);
        var manager=new DataSourceTransactionManager(ds);var metrics=new SimpleMeterRegistry();admin=UUID.randomUUID();key="synthetic."+UUID.randomUUID();
        jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic configuration actor')",admin,issuer,admin.toString(),admin+"@example.invalid");
        jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,'ADMIN',?)",UUID.randomUUID(),admin,admin);
        ConfigurationPolicy policy=new ConfigurationPolicy(){
            public String key(){return key;}
            public void validate(JsonNode value){if(!value.isObject()||value.size()!=1||!value.has("synthetic")||!value.get("synthetic").isBoolean())throw ApiValues.invalid();}
            public void activate(JdbcTemplate db,JsonNode value){if(!value.get("synthetic").asBoolean())throw new Problem.Failure(ProblemCode.PLATFORM_RULE_VIOLATION);}
        };
        service=new OwnerConfiguration(new JdbcConfigurationRepository(jdbc,clock),new PlatformStore(jdbc,manager,clock,metrics),new AuditWriter(jdbc,clock,metrics),clock,List.of(policy));
    }
    @AfterEach void clean(){if(jdbc!=null){jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id=?",admin);jdbc.update("DELETE FROM commerce.party WHERE id=?",admin);}}
    ActorAssertion.Actor actor(Instant mfa){return new ActorAssertion.Actor(admin,issuer,admin.toString(),null,Set.of("ADMIN"),Set.of(),mfa);}
    JsonNode body(boolean enabled,boolean valid){return EventCodec.JSON.valueToTree(Map.of("value",Map.of("synthetic",valid),"active",enabled,"reason","Synthetic operator approval"));}
    OwnerConfiguration.View put(long version,JsonNode body,String request){return service.put(actor(clock.instant()),key,version,body,request,UUID.randomUUID());}
    @Test void historyReplayVersionAndAuditAreAtomic(){
        String request=UUID.randomUUID().toString();var first=put(0,body(false,true),request);
        assertThat(first.version()).isEqualTo(1);assertThat(first.revision()).isEqualTo(1);
        assertThat(put(0,body(false,true),request)).isEqualTo(first);
        assertThatThrownBy(()->put(0,body(true,true),request)).isInstanceOf(PlatformStore.Conflict.class);
        assertThatThrownBy(()->put(0,body(false,true),UUID.randomUUID().toString())).hasMessage("VERSION_MISMATCH");
        assertThat(put(1,body(true,true),UUID.randomUUID().toString()).revision()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.feature_configuration WHERE key=?",Integer.class,key)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE created_by=?",Integer.class,admin.toString())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT string_agg(coalesce(new_value::text,''),'') FROM commerce.audit_record WHERE created_by=?",String.class,admin.toString())).doesNotContain("synthetic","operator","approval");
        assertThat(jdbc.queryForObject("SELECT approved_by FROM commerce.feature_configuration WHERE key=? AND revision=2",String.class,key)).isEqualTo(admin.toString());
    }
    @Test void staleMfaRevokedRoleForeignOrganizationAndInvalidActivationDenyBeforeWrites(){
        assertThatThrownBy(()->service.put(actor(clock.instant().minusSeconds(901)),key,0,body(true,true),"stale",UUID.randomUUID())).hasMessage("ACTOR_TRUST_REJECTED");
        assertThatThrownBy(()->put(0,body(true,false),"invalid-activation")).hasMessage("PLATFORM_RULE_VIOLATION");
        var foreign=new ActorAssertion.Actor(admin,issuer,admin.toString(),UUID.randomUUID(),Set.of("ADMIN"),Set.of(),clock.instant());
        assertThatThrownBy(()->service.put(foreign,key,0,body(false,true),"foreign",UUID.randomUUID())).hasMessage("ACCESS_DENIED");
        jdbc.update("UPDATE commerce.staff_role_assignment SET revoked_at=now() WHERE party_id=?",admin);
        assertThatThrownBy(()->put(0,body(false,true),"revoked")).hasMessage("ACCESS_DENIED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.feature_configuration WHERE key=?",Integer.class,key)).isZero();
    }
    @Test void competingFirstWritesHaveExactlyOneRevision()throws Exception{
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var futures=new ArrayList<Future<Boolean>>();for(int i=0;i<12;i++)futures.add(pool.submit(()->{
                try{put(0,body(false,true),UUID.randomUUID().toString());return true;}
                catch(Problem.Failure rejected){assertThat(rejected.code).isIn(ProblemCode.VERSION_MISMATCH,ProblemCode.CONFLICT);return false;}
            }));int accepted=0;for(var f:futures)if(f.get(10,TimeUnit.SECONDS))accepted++;assertThat(accepted).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.feature_configuration WHERE key=?",Integer.class,key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE created_by=?",Integer.class,admin.toString())).isEqualTo(1);
    }
    @Test void deniedAuditInsertRollsBackConfigurationAndAllowsSameKeyRetry(){
        var bootstrap=new JdbcTemplate(new DriverManagerDataSource(System.getenv("NIGSON_TEST_JDBC_URL"),"nigson_bootstrap","mock-bootstrap"));
        bootstrap.execute("REVOKE INSERT ON commerce.audit_record FROM nigson_commerce_app");
        try{assertThatThrownBy(()->put(0,body(false,true),"audit-denial")).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.feature_configuration WHERE key=?",Integer.class,key)).isZero();
        }finally{bootstrap.execute("GRANT INSERT ON commerce.audit_record TO nigson_commerce_app");}
        assertThat(put(0,body(false,true),"audit-denial").version()).isEqualTo(1);
    }
    @Test void typedMetadataRejectsUnknownSecretsDuplicatesAndWrongTypes(){
        var policy=new CatalogueMetadataPolicy();
        String valid="{\"schemaVersion\":1,\"units\":[{\"code\":\"SYNTHETIC\",\"scales\":[0,2]}],\"divisions\":[{\"code\":\"synthetic\",\"attributes\":[{\"key\":\"synthetic\",\"type\":\"DECIMAL\",\"unit\":\"SYNTHETIC\",\"maximumLength\":20}]}]}";
        policy.validate(EventCodec.JSON.readTree(valid));
        for(String bad:List.of(valid.replace("[0,2]","[0,0]"),valid.replace("DECIMAL","TEXT"),valid.replace("\"schemaVersion\":1","\"schemaVersion\":\"1\""),valid.replace("\"schemaVersion\":1","\"secret\":\"synthetic\",\"schemaVersion\":1")))assertThatThrownBy(()->policy.validate(EventCodec.JSON.readTree(bad))).isInstanceOf(Problem.Failure.class);
        assertThatThrownBy(()->service.put(actor(clock.instant()),"unregistered",0,body(false,true),"unknown-key",UUID.randomUUID())).hasMessage("PLATFORM_RULE_VIOLATION");
    }
    @Test void metadataActivationPreservesExistingSkuAndSpecificationMeaning(){
        UUID product=UUID.randomUUID(),sku=UUID.randomUUID();
        jdbc.update("INSERT INTO commerce.product(id,created_by,updated_by,name,slug,description,division_code,specifications) VALUES (?,'test','test','Synthetic metadata product',?,'Synthetic','synthetic','{\"synthetic\":\"2.00\"}')",product,"synthetic-"+product);
        jdbc.update("INSERT INTO commerce.sku(id,created_by,updated_by,product_id,code,variant_name,unit,quantity_scale) VALUES (?,'test','test',?,?,'Synthetic','SYNTHETIC',2)",sku,product,"SYNTHETIC-"+sku);
        try{
            var metrics=new SimpleMeterRegistry();var manager=new DataSourceTransactionManager(jdbc.getDataSource());
            var metadataService=new OwnerConfiguration(new JdbcConfigurationRepository(jdbc,clock),new PlatformStore(jdbc,manager,clock,metrics),new AuditWriter(jdbc,clock,metrics),clock,List.of(new CatalogueMetadataPolicy()));
            var value=EventCodec.JSON.readTree("{\"schemaVersion\":1,\"units\":[{\"code\":\"SYNTHETIC\",\"scales\":[0,2]}],\"divisions\":[{\"code\":\"synthetic\",\"attributes\":[{\"key\":\"synthetic\",\"type\":\"DECIMAL\",\"unit\":\"SYNTHETIC\",\"maximumLength\":20}]}]}");
            var input=EventCodec.JSON.valueToTree(Map.of("value",value,"active",true,"reason","Synthetic metadata qualification"));
            assertThat(metadataService.put(actor(clock.instant()),"catalogue.metadata",0,input,"metadata-create",UUID.randomUUID()).active()).isTrue();
            var incompatible=EventCodec.JSON.readTree(EventCodec.JSON.writeValueAsString(input).replace("[0,2]","[0]"));
            assertThatThrownBy(()->metadataService.put(actor(clock.instant()),"catalogue.metadata",1,incompatible,"metadata-incompatible",UUID.randomUUID())).isInstanceOf(Problem.Failure.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.feature_configuration WHERE key='catalogue.metadata' AND created_by=?",Integer.class,admin.toString())).isEqualTo(1);
            var disabled=EventCodec.JSON.valueToTree(Map.of("value",value,"active",false,"reason","Synthetic metadata deactivation"));
            assertThat(metadataService.put(actor(null),"catalogue.metadata",1,disabled,"metadata-disable",UUID.randomUUID()).active()).isFalse();
            assertThat(new JdbcConfigurationRepository(jdbc,clock).latest("catalogue.metadata").active()).isFalse();
        }finally{
            jdbc.update("DELETE FROM commerce.sku WHERE id=?",sku);jdbc.update("DELETE FROM commerce.product WHERE id=?",product);
            jdbc.update("DELETE FROM commerce.feature_configuration WHERE key='catalogue.metadata' AND created_by=?",admin.toString());
        }
    }
}
