package com.nigson.acceptance.catalogue;

import com.nigson.commerce.catalogue.*;
import com.nigson.commerce.files.JdbcCatalogueAssets;
import com.nigson.commerce.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.problem.*;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class ProductPersistenceTest {
    JdbcTemplate jdbc;ProductService service;CatalogueReads reads;ProductRepository repository;RecommendationService recommendations;UUID editor,publisher,sales,market,category;Clock clock=Clock.systemUTC();String code,issuer="https://identity.synthetic.invalid/realms/test";
    static final String PACKAGING="{\"schemaVersion\":1,\"maximumUnitsPerPackage\":\"10\",\"unitWeightKg\":\"1\",\"tareWeightKg\":\"0\",\"lengthCm\":\"10\",\"widthCm\":\"10\",\"heightCm\":\"10\"}";
    @BeforeEach void setup(){
        String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"catalogue-check supplies isolated PostgreSQL");
        if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");
        var ds=new DriverManagerDataSource(url+"?currentSchema=commerce","nigson_commerce_app","mock-commerce-runtime");jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);var metrics=new SimpleMeterRegistry();
        editor=party("CONTENT_EDITOR");publisher=party("CONTENT_PUBLISHER");sales=party("SALES");code="SYNTH"+UUID.randomUUID().toString().substring(0,8).toUpperCase();market=UUID.randomUUID();category=UUID.randomUUID();
        // Explicit synthetic fixture records, never runtime seeds or activation facts.
        jdbc.update("INSERT INTO commerce.currency(id,created_by,updated_by,code,minor_unit) VALUES (?,'test','test','NGN',2) ON CONFLICT(code) DO NOTHING",UUID.randomUUID());
        jdbc.update("INSERT INTO commerce.market(id,created_by,updated_by,code,name,currency_code,enabled) VALUES (?,'test','test',?,'Synthetic market','NGN',true)",market,code);
        jdbc.update("INSERT INTO commerce.category(id,created_by,updated_by,name,slug) VALUES (?,'test','test','Synthetic category',?)",category,"synthetic-"+category);
        jdbc.update("INSERT INTO commerce.feature_configuration(id,created_by,updated_by,key,revision,value,active) VALUES (?,'test','test','catalogue.metadata',1,?::jsonb,true)",UUID.randomUUID(),"{\"schemaVersion\":1,\"units\":[{\"code\":\"SYNTHETIC\",\"scales\":[0,2]}],\"divisions\":[{\"code\":\"synthetic\",\"attributes\":[{\"key\":\"product_type\",\"type\":\"TEXT\",\"unit\":null,\"maximumLength\":100}]}]}");
        var assets=new JdbcCatalogueAssets(jdbc,"test","https://cdn.synthetic.test/assets/");repository=new JdbcProductRepository(jdbc,clock,assets);var authority=new CatalogueAuthority(jdbc);
        var commands=new PlatformStore(jdbc,manager,clock,metrics);var audit=new AuditWriter(jdbc,clock,metrics);service=new ProductService(repository,new JdbcTaxonomyRepository(jdbc,clock),authority,commands,audit,clock);recommendations=new RecommendationService(jdbc,repository,assets,authority,commands,audit,clock,metrics);
        var tx=new TransactionTemplate(manager);tx.setReadOnly(true);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);tx.setTimeout(3);
        reads=new CatalogueReads(new JdbcCatalogueQueries(jdbc,repository,assets),authority,jdbc,tx,clock,10000);
    }
    @AfterEach void clean(){if(jdbc!=null){jdbc.update("DELETE FROM commerce.feature_configuration WHERE key='catalogue.metadata' AND created_by='test'");for(var id:List.of(editor,publisher,sales)){jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id=?",id);jdbc.update("DELETE FROM commerce.party WHERE id=?",id);}}}
    UUID party(String role){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic catalogue actor')",id,issuer,id.toString(),id+"@example.invalid");jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,?,?)",UUID.randomUUID(),id,role,id);return id;}
    ActorAssertion.Actor actor(UUID id,String role){return new ActorAssertion.Actor(id,issuer,id.toString(),null,Set.of(role),Set.of(),clock.instant());}
    ActorAssertion.Actor editor(){return actor(editor,"CONTENT_EDITOR");}ActorAssertion.Actor publisher(){return actor(publisher,"CONTENT_PUBLISHER");}
    JsonNode product(String name){return EventCodec.JSON.valueToTree(Map.of("name",name,"slug","synthetic-"+UUID.randomUUID(),"description","Synthetic fixture description","specifications",Map.of("product_type","synthetic"),"divisionCode","synthetic","categoryIds",List.of(category)));}
    CatalogueValues.ProductAdmin create(String name){return service.write(editor(),null,0,product(name),UUID.randomUUID().toString(),UUID.randomUUID());}
    JsonNode sku(String code){return EventCodec.JSON.readTree("{\"code\":\""+code+"\",\"variantName\":\"Synthetic SKU\",\"unit\":\"SYNTHETIC\",\"quantityScale\":0,\"packSize\":\"1\",\"carrierPackaging\":"+PACKAGING+"}");}
    void eligibility(UUID product){jdbc.update("INSERT INTO commerce.product_market_eligibility(id,created_by,updated_by,product_id,market_id,eligible) VALUES (?,'test','test',?,?,true)",UUID.randomUUID(),product,market);}
    UUID asset(UUID product,String state,String purpose,String classification){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO commerce.asset_reference(id,created_by,updated_by,product_id,object_key,purpose,mime_type,byte_size,checksum_sha256,status,classification) VALUES (?,'test','test',?,?,?,?,1,?,?,?)",id,product,"synthetic/"+id+"/image.png",purpose,"image/png","1".repeat(64),state,classification);return id;}
    CatalogueValues.ProductAdmin publish(CatalogueValues.ProductAdmin product,long version,String action,String key){return service.publish(publisher(),product.id(),version,EventCodec.JSON.valueToTree(Map.of("action",action,"reason","Synthetic publication review")),key,UUID.randomUUID());}
    CatalogueValues.ProductAdmin ready(String name,String skuCode){var product=create(name);service.sku(editor(),product.id(),true,0,sku(skuCode),UUID.randomUUID().toString(),UUID.randomUUID());eligibility(product.id());asset(product.id(),"CLEAN","IMAGE","PUBLIC");return publish(product,1,"PUBLISH",UUID.randomUUID().toString());}
    CatalogueQueryRepository.Search search(String q,int page,int size){return new CatalogueQueryRepository.Search(code,q,null,null,null,page,size,"relevance,desc");}
    @Test void productReplayStaleVersionAndOwnedOutboxAuditAreAtomic(){
        var input=product("Synthetic create");String key=UUID.randomUUID().toString();var first=service.write(editor(),null,0,input,key,UUID.randomUUID());
        assertThat(first.status()).isEqualTo("DRAFT");assertThat(first.version()).isZero();assertThat(first.revision()).isEqualTo(1);
        assertThat(service.write(editor(),null,0,input,key,UUID.randomUUID())).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.outbox_event WHERE aggregate_id=?",Integer.class,first.id())).isEqualTo(1);
        String wire=jdbc.queryForObject("SELECT payload::text FROM commerce.outbox_event WHERE aggregate_id=?",String.class,first.id());assertThat(EventCodec.decode(wire).path("payload").path("publication_state").asString()).isEqualTo("DRAFT");assertThat(EventCodec.decode(wire).path("aggregate_version").asLong()).isZero();assertThat(EventCodec.decode(wire).path("payload").path("revision").asLong()).isEqualTo(1);
        var changed=service.write(editor(),first.id(),0,EventCodec.JSON.readTree("{\"description\":\"Synthetic changed description\"}"),"patch",UUID.randomUUID());assertThat(changed.version()).isEqualTo(1);assertThat(changed.revision()).isEqualTo(2);
        assertThatThrownBy(()->service.write(editor(),first.id(),0,EventCodec.JSON.readTree("{\"name\":\"Stale\"}"),"stale",UUID.randomUUID())).hasMessage("VERSION_MISMATCH");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE resource_id=?",Integer.class,first.id())).isEqualTo(2);
        assertThatThrownBy(()->reads.product(first.id(),code)).hasMessage("NOT_FOUND");
    }
    @Test void publicationRequiresSkuTaxonomyEligibilityAndCleanPublicImageThenArchiveHidesImmediately(){
        var product=create("Synthetic publication");assertThatThrownBy(()->publish(product,0,"PUBLISH","missing")).hasMessage("CATALOGUE_RULE_VIOLATION");
        service.sku(editor(),product.id(),true,0,sku("SYNTHETIC-"+product.id()),"sku",UUID.randomUUID());eligibility(product.id());
        UUID quarantined=asset(product.id(),"QUARANTINED","IMAGE","PUBLIC");asset(product.id(),"CLEAN","IMAGE","PRIVATE");
        assertThatThrownBy(()->publish(product,1,"PUBLISH","quarantined")).hasMessage("CATALOGUE_RULE_VIOLATION");
        jdbc.update("UPDATE commerce.asset_reference SET status='CLEAN' WHERE id=?",quarantined);asset(product.id(),"PUBLISHED","SHIPPING_LABEL","PRIVATE");
        var published=publish(product,1,"PUBLISH","publish");assertThat(published.status()).isEqualTo("ACTIVE");assertThat(published.assets()).noneMatch(a->a.purpose().equals("SHIPPING_LABEL"));
        var view=reads.product(product.id(),code);assertThat(view.assets()).hasSize(1);assertThat(view.assets().getFirst().url()).startsWith("https://cdn.synthetic.test/assets/");
        assertThat(reads.search(search("",0,100)).items()).extracting(CatalogueValues.Summary::id).contains(product.id());
        publish(product,published.version(),"ARCHIVE","archive");assertThatThrownBy(()->reads.product(product.id(),code)).hasMessage("NOT_FOUND");
        assertThat(reads.search(search("",0,100)).items()).extracting(CatalogueValues.Summary::id).doesNotContain(product.id());
    }
    @Test void skuMetadataPackagingScaleImmutabilityAndDeactivationAreEnforced(){
        var product=create("Synthetic SKU constraints");var first=service.sku(editor(),product.id(),true,0,sku("SYNTHETIC-"+product.id()),"sku",UUID.randomUUID());
        assertThat(repository.find(product.id(),false).revision()).isEqualTo(2);
        assertThatThrownBy(()->service.sku(editor(),first.id(),false,0,EventCodec.JSON.readTree("{\"code\":\"REPLACED\",\"carrierPackaging\":"+PACKAGING+"}"),"immutable",UUID.randomUUID())).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(()->service.sku(editor(),first.id(),false,0,EventCodec.JSON.readTree("{\"variantName\":\"Changed\"}"),"missing-packaging",UUID.randomUUID())).hasMessage("VALIDATION_ERROR");
        var input=EventCodec.JSON.readTree("{\"active\":false,\"carrierPackaging\":"+PACKAGING+"}");var disabled=service.sku(editor(),first.id(),false,0,input,"disable",UUID.randomUUID());assertThat(disabled.version()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT active FROM commerce.sku WHERE id=?",Boolean.class,first.id())).isFalse();
        assertThatThrownBy(()->service.sku(editor(),first.id(),false,0,input,"stale-sku",UUID.randomUUID())).hasMessage("VERSION_MISMATCH");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.outbox_event WHERE aggregate_id=?",Integer.class,product.id())).isEqualTo(2);
        var fractional=sku("SYNTHETIC-OTHER");((tools.jackson.databind.node.ObjectNode)fractional).put("packSize","1.5");assertThatThrownBy(()->service.sku(editor(),product.id(),true,2,fractional,"fraction",UUID.randomUUID())).hasMessage("VALIDATION_ERROR");
    }
    @Test void searchRanksExactSkuBeforeNameThenDescriptionWithStablePagesAndFilters(){
        var exact=ready("Synthetic other","SYNTHETIC-SEARCH");var name=ready("SYNTHETIC SEARCH","SYNTHETIC-"+UUID.randomUUID());var description=ready("Other synthetic item","SYNTHETIC-"+UUID.randomUUID());
        service.write(editor(),description.id(),description.version(),EventCodec.JSON.readTree("{\"description\":\"SYNTHETIC SEARCH in description\"}"),"description",UUID.randomUUID());
        var ranked=reads.search(search("SYNTHETIC-SEARCH",0,10));assertThat(ranked.items()).extracting(CatalogueValues.Summary::id).containsExactly(exact.id(),name.id(),description.id());
        assertThat(reads.search(search("SYNTHETIC-SEARCH",0,1)).items().getFirst().id()).isEqualTo(exact.id());assertThat(reads.search(search("SYNTHETIC-SEARCH",1,1)).items().getFirst().id()).isEqualTo(name.id());
        assertThat(reads.search(new CatalogueQueryRepository.Search(code,"",category,null,"synthetic",0,100,"name,asc")).totalElements()).isEqualTo(3);
        jdbc.update("UPDATE commerce.product_market_eligibility SET eligible=false WHERE product_id=?",exact.id());assertThatThrownBy(()->reads.product(exact.id(),code)).hasMessage("NOT_FOUND");
        assertThat(reads.search(search("SYNTHETIC-SEARCH",0,10)).items()).extracting(CatalogueValues.Summary::id).doesNotContain(exact.id());
        assertThatThrownBy(()->reads.search(search("a ".repeat(21),0,10))).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(()->reads.search(new CatalogueQueryRepository.Search(code,"",null,null,null,0,10,"private,asc"))).hasMessage("VALIDATION_ERROR");
    }
    @Test void revokedRolesAndAuditDenialCannotCommitProductOrPublication(){
        var product=create("Synthetic protected");var bootstrap=new JdbcTemplate(new DriverManagerDataSource(System.getenv("NIGSON_TEST_JDBC_URL"),"nigson_bootstrap","mock-bootstrap"));
        bootstrap.execute("REVOKE INSERT ON commerce.audit_record FROM nigson_commerce_app");
        try{assertThatThrownBy(()->service.write(editor(),product.id(),0,EventCodec.JSON.readTree("{\"name\":\"Changed\"}"),"audit-denial",UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(repository.find(product.id(),false).version()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.outbox_event WHERE aggregate_id=?",Integer.class,product.id())).isEqualTo(1);
        }finally{bootstrap.execute("GRANT INSERT ON commerce.audit_record TO nigson_commerce_app");}
        assertThat(service.write(editor(),product.id(),0,EventCodec.JSON.readTree("{\"name\":\"Changed\"}"),"audit-denial",UUID.randomUUID()).version()).isEqualTo(1);
        jdbc.update("UPDATE commerce.staff_role_assignment SET revoked_at=now() WHERE party_id=?",editor);
        assertThatThrownBy(()->service.write(editor(),product.id(),0,EventCodec.JSON.readTree("{\"name\":\"Changed\"}"),"audit-denial",UUID.randomUUID())).hasMessage("ACCESS_DENIED");
        assertThatThrownBy(()->service.publish(editor(),product.id(),1,EventCodec.JSON.valueToTree(Map.of("action","ARCHIVE","reason","Synthetic request")),"publisher-denial",UUID.randomUUID())).hasMessage("ACCESS_DENIED");
    }
    @Test void publicationAuditFailureRollsBackAssetRootAndEventThenSameKeyRecovers(){
        var product=create("Synthetic atomic publication");service.sku(editor(),product.id(),true,0,sku("SYNTHETIC-"+product.id()),"sku",UUID.randomUUID());eligibility(product.id());UUID image=asset(product.id(),"CLEAN","IMAGE","PUBLIC");
        var bootstrap=new JdbcTemplate(new DriverManagerDataSource(System.getenv("NIGSON_TEST_JDBC_URL"),"nigson_bootstrap","mock-bootstrap"));bootstrap.execute("REVOKE INSERT ON commerce.audit_record FROM nigson_commerce_app");
        try{assertThatThrownBy(()->publish(product,1,"PUBLISH","audit-publication")).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(repository.find(product.id(),false).status()).isEqualTo("DRAFT");assertThat(repository.find(product.id(),false).version()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM commerce.asset_reference WHERE id=?",String.class,image)).isEqualTo("CLEAN");assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.outbox_event WHERE aggregate_id=?",Integer.class,product.id())).isEqualTo(1);
        }finally{bootstrap.execute("GRANT INSERT ON commerce.audit_record TO nigson_commerce_app");}
        assertThat(publish(product,1,"PUBLISH","audit-publication").status()).isEqualTo("ACTIVE");assertThat(jdbc.queryForObject("SELECT status FROM commerce.asset_reference WHERE id=?",String.class,image)).isEqualTo("PUBLISHED");assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.outbox_event WHERE aggregate_id=?",Integer.class,product.id())).isEqualTo(2);
    }
    @Test void simultaneousProductCreateReplayHasOneRootAuditAndOutbox()throws Exception{
        var input=product("Synthetic concurrent product");String key=UUID.randomUUID().toString();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var futures=new ArrayList<Future<CatalogueValues.ProductAdmin>>();for(int n=0;n<12;n++)futures.add(pool.submit(()->{
                for(int attempt=0;attempt<100;attempt++){
                    try{return service.write(editor(),null,0,input,key,UUID.randomUUID());}
                    catch(PlatformStore.Conflict busy){assertThat(busy.getMessage()).isEqualTo("IDEMPOTENCY_IN_PROGRESS");Thread.sleep(10);}
                }throw new AssertionError("Bounded concurrent replay did not recover");
            }));var first=futures.getFirst().get(10,TimeUnit.SECONDS);for(var f:futures)assertThat(f.get(10,TimeUnit.SECONDS)).isEqualTo(first);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.product WHERE slug=?",Integer.class,first.slug())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.outbox_event WHERE aggregate_id=?",Integer.class,first.id())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE resource_id=?",Integer.class,first.id())).isEqualTo(1);
        }
    }
    @Test void recommendationsReplaceReplayRaceAndPublicMarketFilteringAreAtomic()throws Exception{
        var root=ready("Synthetic recommendation root","SYNTHETIC-ROOT-"+UUID.randomUUID());var related=ready("Synthetic recommendation related","SYNTHETIC-RELATED-"+UUID.randomUUID());var draft=create("Synthetic unpublished recommendation");
        var request=EventCodec.JSON.readTree("{\"items\":[{\"relatedProductId\":\""+related.id()+"\",\"kind\":\"FEATURED\",\"priority\":7}]}");var actor=actor(sales,"SALES");String key=UUID.randomUUID().toString();var first=recommendations.replace(actor,root.id(),root.version(),request,key,UUID.randomUUID());
        assertThat(first.version()).isEqualTo(root.version()+1);assertThat(first.items()).containsExactly(new RecommendationValues.Recommendation(related.id(),"FEATURED",7));assertThat(recommendations.replace(actor,root.id(),root.version(),request,key,UUID.randomUUID())).isEqualTo(first);
        assertThatThrownBy(()->recommendations.replace(actor,root.id(),root.version(),request,"stale-"+key,UUID.randomUUID())).hasMessage("VERSION_MISMATCH");
        var organizationActor=new ActorAssertion.Actor(sales,issuer,sales.toString(),UUID.randomUUID(),Set.of("SALES"),Set.of(),clock.instant());assertThatThrownBy(()->recommendations.replace(organizationActor,root.id(),first.version(),request,"org-"+key,UUID.randomUUID())).hasMessage("ACCESS_DENIED");
        var badSelf=EventCodec.JSON.readTree("{\"items\":[{\"relatedProductId\":\""+root.id()+"\",\"kind\":\"RELATED\",\"priority\":0}]}");assertThatThrownBy(()->recommendations.replace(actor,root.id(),first.version(),badSelf,"self-"+key,UUID.randomUUID())).hasMessage("VALIDATION_ERROR");
        var badInactive=EventCodec.JSON.readTree("{\"items\":[{\"relatedProductId\":\""+draft.id()+"\",\"kind\":\"RELATED\",\"priority\":0}]}");assertThatThrownBy(()->recommendations.replace(actor,root.id(),first.version(),badInactive,"inactive-"+key,UUID.randomUUID())).hasMessage("CATALOGUE_RULE_VIOLATION");
        jdbc.update("INSERT INTO commerce.product_recommendation(id,created_by,updated_by,product_id,related_product_id,kind,priority,active) VALUES (?,'test','test',?,?, 'RELATED',0,true)",UUID.randomUUID(),root.id(),draft.id());
        assertThat(recommendations.publicList(root.id(),code,6)).containsExactly(new RecommendationValues.PublicItem(related.id(),"Synthetic recommendation related","FEATURED"));assertThat(recommendations.publicList(root.id(),code,6)).isEqualTo(recommendations.publicList(root.id(),code,6));
        jdbc.update("UPDATE commerce.product_market_eligibility SET eligible=false WHERE product_id=?",related.id());assertThat(recommendations.publicList(root.id(),code,6)).isEmpty();
        assertThatThrownBy(()->recommendations.publicList(root.id(),"DISABLED",6)).hasMessage("NOT_FOUND");jdbc.update("UPDATE commerce.product_market_eligibility SET eligible=true WHERE product_id=?",related.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE resource_id=? AND action='PRODUCT_UPDATE'",Integer.class,root.id())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.outbox_event WHERE aggregate_id=?",Integer.class,root.id())).isEqualTo(2);
        String concurrentKey="concurrent-"+UUID.randomUUID();var concurrent=EventCodec.JSON.readTree("{\"items\":[{\"relatedProductId\":\""+related.id()+"\",\"kind\":\"RELATED\",\"priority\":2}]}");var bootstrap=new JdbcTemplate(new DriverManagerDataSource(System.getenv("NIGSON_TEST_JDBC_URL"),"nigson_bootstrap","mock-bootstrap"));bootstrap.execute("REVOKE INSERT ON commerce.audit_record FROM nigson_commerce_app");try{assertThatThrownBy(()->recommendations.replace(actor,root.id(),first.version(),concurrent,"audit-denied-"+concurrentKey,UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(repository.find(root.id(),false).version()).isEqualTo(first.version());assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.product_recommendation WHERE product_id=? AND active AND kind='FEATURED'",Integer.class,root.id())).isEqualTo(1);}finally{bootstrap.execute("GRANT INSERT ON commerce.audit_record TO nigson_commerce_app");}
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){var start=new CountDownLatch(1);var futures=new ArrayList<Future<RecommendationValues.Result>>();for(int n=0;n<8;n++)futures.add(pool.submit(()->{start.await();for(int attempt=0;attempt<100;attempt++){try{return recommendations.replace(actor,root.id(),first.version(),concurrent,concurrentKey,UUID.randomUUID());}catch(PlatformStore.Conflict busy){Thread.sleep(10);}}throw new AssertionError("Recommendation replay did not recover");}));start.countDown();var winner=futures.getFirst().get(10,TimeUnit.SECONDS);for(var f:futures)assertThat(f.get(10,TimeUnit.SECONDS)).isEqualTo(winner);assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.product_recommendation WHERE product_id=? AND active",Integer.class,root.id())).isEqualTo(1);}
    }
    @Test void aiKnowledgeProjectionRequiresExactWorkloadAndContainsPublishedPublicFieldsOnly(){
        var product=ready("Synthetic AI knowledge product","SYNTHETIC-AI-"+UUID.randomUUID());var route=com.nigson.shared.security.PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-123")).findFirst().orElseThrow();var proof=new com.nigson.shared.security.PrivateWorkload.Proof("nigson-ai","synthetic-ai",route,null);var value=recommendations.knowledge(proof,product.id());assertThat(value.approved()).isTrue();assertThat(value.marketCodes()).containsExactly(code);assertThat(value.publicUrl()).startsWith("https://cdn.synthetic.test/assets/");assertThat(value.checksum()).matches("[a-f0-9]{64}");assertThat(value.name()).isEqualTo("Synthetic AI knowledge product");
        var wrong=new com.nigson.shared.security.PrivateWorkload.Proof("nigson-integration","synthetic-ai",route,null);assertThatThrownBy(()->recommendations.knowledge(wrong,product.id())).hasMessage("ACTOR_TRUST_REJECTED");
        var unpublished=create("Synthetic AI unpublished product");assertThatThrownBy(()->recommendations.knowledge(proof,unpublished.id())).hasMessage("NOT_FOUND");
    }
}
