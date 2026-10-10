package com.nigson.acceptance.catalogue;

import com.nigson.commerce.catalogue.*;
import com.nigson.commerce.platform.*;
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

class TaxonomyPersistenceTest {
    JdbcTemplate jdbc;TaxonomyService service;UUID editor;Clock clock=Clock.systemUTC();
    String issuer="https://identity.synthetic.invalid/realms/test";
    @BeforeEach void setup(){
        String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"catalogue-check supplies isolated PostgreSQL");
        if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");
        var ds=new DriverManagerDataSource(url+"?currentSchema=commerce","nigson_commerce_app","mock-commerce-runtime");jdbc=new JdbcTemplate(ds);
        var manager=new DataSourceTransactionManager(ds);var metrics=new SimpleMeterRegistry();editor=UUID.randomUUID();
        jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic editor')",editor,issuer,editor.toString(),editor+"@example.invalid");
        jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,'CONTENT_EDITOR',?)",UUID.randomUUID(),editor,editor);
        service=new TaxonomyService(new JdbcTaxonomyRepository(jdbc,clock),new CatalogueAuthority(jdbc),new PlatformStore(jdbc,manager,clock,metrics),new AuditWriter(jdbc,clock,metrics),clock);
    }
    @AfterEach void clean(){if(jdbc!=null){jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id=?",editor);jdbc.update("DELETE FROM commerce.party WHERE id=?",editor);}}
    ActorAssertion.Actor actor(){return new ActorAssertion.Actor(editor,issuer,editor.toString(),null,Set.of("CONTENT_EDITOR"),Set.of(),null);}
    CatalogueValues.Taxonomy create(TaxonomyRepository.Kind kind,UUID parent){var body=new HashMap<String,Object>();body.put("name","Synthetic taxonomy");body.put("slug","synthetic-"+UUID.randomUUID());if(parent!=null)body.put("parentId",parent);return write(kind,null,0,EventCodec.JSON.valueToTree(body),UUID.randomUUID().toString());}
    CatalogueValues.Taxonomy write(TaxonomyRepository.Kind kind,UUID id,long version,JsonNode input,String request){return service.write(actor(),kind,id,version,input,request,UUID.randomUUID());}
    @Test void replayVersionCollisionAndDeactivationPreserveIdentity(){
        var kind=TaxonomyRepository.Kind.BRAND;var input=EventCodec.JSON.valueToTree(Map.of("name","Synthetic brand","slug","synthetic-"+UUID.randomUUID()));String key=UUID.randomUUID().toString();
        var first=write(kind,null,0,input,key);assertThat(write(kind,null,0,input,key)).isEqualTo(first);
        assertThatThrownBy(()->write(kind,null,0,EventCodec.JSON.valueToTree(Map.of("name","Changed","slug",first.slug())),key)).isInstanceOf(PlatformStore.Conflict.class);
        assertThatThrownBy(()->write(kind,null,0,input,UUID.randomUUID().toString())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        UUID product=UUID.randomUUID();jdbc.update("INSERT INTO commerce.product(id,created_by,updated_by,brand_id,name,slug,description) VALUES (?,'test','test',?,'Synthetic',?,'Synthetic')",product,first.id(),"synthetic-"+product);
        var changed=write(kind,first.id(),0,EventCodec.JSON.readTree("{\"active\":false}"),"deactivate");assertThat(changed.active()).isFalse();assertThat(changed.version()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT brand_id FROM commerce.product WHERE id=?",UUID.class,product)).isEqualTo(first.id());
        assertThatThrownBy(()->write(kind,first.id(),0,EventCodec.JSON.readTree("{\"name\":\"Changed\"}"),"stale")).hasMessage("VERSION_MISMATCH");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE created_by=?",Integer.class,editor.toString())).isEqualTo(2);
    }
    @Test void depthAndMovedSubtreeAreBounded(){
        var kind=TaxonomyRepository.Kind.CATEGORY;var root=create(kind,null);var level2=create(kind,root.id());var level3=create(kind,level2.id());var level4=create(kind,level3.id());var level5=create(kind,level4.id());
        assertThatThrownBy(()->create(kind,level5.id())).hasMessage("CATALOGUE_RULE_VIOLATION");
        var other=create(kind,null);create(kind,other.id());
        assertThatThrownBy(()->write(kind,other.id(),0,EventCodec.JSON.valueToTree(Map.of("parentId",level4.id())),"subtree-depth")).hasMessage("CATALOGUE_RULE_VIOLATION");
        assertThatThrownBy(()->write(kind,root.id(),0,EventCodec.JSON.valueToTree(Map.of("parentId",level5.id())),"ancestor-cycle")).hasMessage("CATALOGUE_RULE_VIOLATION");
        assertThatThrownBy(()->write(kind,root.id(),0,EventCodec.JSON.valueToTree(Map.of("parentId",root.id())),"self-cycle")).hasMessage("CATALOGUE_RULE_VIOLATION");
        assertThat(jdbc.queryForObject("SELECT parent_id FROM commerce.category WHERE id=?",UUID.class,root.id())).isNull();
    }
    @Test void simultaneousReparentingCannotCreateCycle()throws Exception{
        var kind=TaxonomyRepository.Kind.CATEGORY;var a=create(kind,null);var b=create(kind,null);
        var barrier=new CyclicBarrier(2);try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var futures=new ArrayList<Future<Boolean>>();for(var pair:List.of(List.of(a.id(),b.id()),List.of(b.id(),a.id())))futures.add(pool.submit(()->{
                barrier.await(5,TimeUnit.SECONDS);try{write(kind,pair.get(0),0,EventCodec.JSON.valueToTree(Map.of("parentId",pair.get(1))),UUID.randomUUID().toString());return true;}
                catch(Problem.Failure failure){assertThat(failure.code).isEqualTo(ProblemCode.CATALOGUE_RULE_VIOLATION);return false;}
            }));int accepted=0;for(var f:futures)if(f.get(10,TimeUnit.SECONDS))accepted++;assertThat(accepted).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.category WHERE id IN (?,?) AND parent_id IS NOT NULL",Integer.class,a.id(),b.id())).isEqualTo(1);
    }
    @Test void currentRoleOrganizationAndExactTypesDenyWithoutAudit(){
        var kind=TaxonomyRepository.Kind.CATEGORY;
        for(String input:List.of("{\"name\":true,\"slug\":\"synthetic\"}","{\"name\":\"Synthetic\",\"slug\":\"synthetic\",\"sortOrder\":\"1\"}","{\"name\":\"Synthetic\",\"slug\":\"synthetic\",\"owner\":\"synthetic\"}"))assertThatThrownBy(()->write(kind,null,0,EventCodec.JSON.readTree(input),UUID.randomUUID().toString())).isInstanceOf(Problem.Failure.class);
        var foreign=new ActorAssertion.Actor(editor,issuer,editor.toString(),UUID.randomUUID(),Set.of("CONTENT_EDITOR"),Set.of(),null);
        assertThatThrownBy(()->service.write(foreign,kind,null,0,EventCodec.JSON.readTree("{}"),"foreign",UUID.randomUUID())).hasMessage("ACCESS_DENIED");
        jdbc.update("UPDATE commerce.staff_role_assignment SET revoked_at=now() WHERE party_id=?",editor);
        assertThatThrownBy(()->create(kind,null)).hasMessage("ACCESS_DENIED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE created_by=?",Integer.class,editor.toString())).isZero();
    }
    @Test void deniedAuditInsertionRollsBackTreeMutationAndPermitsReplay(){
        var bootstrap=new JdbcTemplate(new DriverManagerDataSource(System.getenv("NIGSON_TEST_JDBC_URL"),"nigson_bootstrap","mock-bootstrap"));
        var input=EventCodec.JSON.valueToTree(Map.of("name","Synthetic category","slug","synthetic-"+UUID.randomUUID()));
        bootstrap.execute("REVOKE INSERT ON commerce.audit_record FROM nigson_commerce_app");
        try{assertThatThrownBy(()->write(TaxonomyRepository.Kind.CATEGORY,null,0,input,"audit-denial")).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.category WHERE slug=?",Integer.class,input.get("slug").asString())).isZero();
        }finally{bootstrap.execute("GRANT INSERT ON commerce.audit_record TO nigson_commerce_app");}
        assertThat(write(TaxonomyRepository.Kind.CATEGORY,null,0,input,"audit-denial").version()).isZero();
    }
}
