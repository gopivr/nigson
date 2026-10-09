package com.nigson.acceptance.party;
import com.nigson.commerce.party.*;
import com.nigson.commerce.platform.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
class PartyPersistenceTest {
    static final String ISSUER="https://identity.example.invalid/realms/test";
    static final String RULES="{\"schemaVersion\":1,\"requiredFields\":[],\"postalPattern\":null,\"regionCodes\":[]}";
    JdbcTemplate jdbc;TransactionTemplate tx;PartyService service;UUID party,other,market;String subject;List<UUID> parties=new ArrayList<>();
    Clock clock=Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"),ZoneOffset.UTC);
    @BeforeEach void setup(){
        String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"make party-check supplies isolated owner PostgreSQL");if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");
        var source=new DriverManagerDataSource(url+"?currentSchema=commerce","nigson_commerce_app","mock-commerce-runtime");jdbc=new JdbcTemplate(source);var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);tx.setTimeout(3);var metrics=new SimpleMeterRegistry();var audit=new AuditWriter(jdbc,clock,metrics);
        service=new PartyService(new JdbcPartyRepository(jdbc,clock,3,audit),new PlatformStore(jdbc,manager,clock,metrics),tx,audit,clock);
        subject=UUID.randomUUID().toString();party=provision(subject);other=provision(UUID.randomUUID().toString());
        jdbc.update("INSERT INTO commerce.currency(id,created_by,updated_by,code,minor_unit) VALUES (?,'test','test','NGN',2) ON CONFLICT(code) DO NOTHING",UUID.randomUUID());
        market=UUID.randomUUID();jdbc.update("INSERT INTO commerce.market(id,created_by,updated_by,code,name,currency_code,enabled) VALUES (?,'test','test',?,'Synthetic address fixture','NGN',true)",market,"PARTY_"+market.toString().replace("-", "").substring(0,20));
        jdbc.update("INSERT INTO commerce.market_destination(id,created_by,updated_by,market_id,country_code,address_rules) VALUES (?,'test','test',?,'NG',?::jsonb)",UUID.randomUUID(),market,RULES);
    }
    UUID provision(String s){UUID id=service.provision(new PartyValues.VerifiedIdentity(ISSUER,s,s+"@example.invalid",true,"Synthetic party"));parties.add(id);return id;}
    @AfterEach void clean(){if(jdbc==null)return;for(UUID id:new HashSet<>(parties)){jdbc.update("DELETE FROM commerce.address WHERE party_id=?",id);jdbc.update("DELETE FROM commerce.customer_profile WHERE party_id=?",id);jdbc.update("DELETE FROM commerce.organization_membership WHERE party_id=?",id);jdbc.update("DELETE FROM commerce.party WHERE id=?",id);}if(market!=null){jdbc.update("DELETE FROM commerce.market_destination WHERE market_id=?",market);jdbc.update("DELETE FROM commerce.market WHERE id=?",market);}}
    ActorAssertion.Actor actor(UUID id){String s=jdbc.queryForObject("SELECT identity_subject FROM commerce.party WHERE id=?",String.class,id);return new ActorAssertion.Actor(id,ISSUER,s,null,Set.of("CUSTOMER"),Set.of(),null);}
    JsonNode json(String s){return EventCodec.JSON.readTree(s);}
    JsonNode address(boolean defaultAddress){return json("{\"recipient\":\"Synthetic recipient\",\"phone\":\"+2348000000000\",\"line1\":\"Synthetic road\",\"city\":\"Synthetic city\",\"countryCode\":\"NG\",\"isDefault\":"+defaultAddress+"}");}
    PartyValues.Address create(String key,boolean d){return service.createAddress(actor(party),address(d),key,UUID.randomUUID());}
    @Test void identityMappingIsStableUniqueAuditedAndNeverEmailBased()throws Exception{
        var gate=new CountDownLatch(1);var identity=new PartyValues.VerifiedIdentity(ISSUER,subject,subject+"@example.invalid",true,"Other identity display label");
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->{gate.await();return service.provision(identity);});var b=pool.submit(()->{gate.await();return service.provision(identity);});gate.countDown();assertThat(a.get(5,TimeUnit.SECONDS)).isEqualTo(party);assertThat(b.get(5,TimeUnit.SECONDS)).isEqualTo(party);}
        assertThat(service.profile(actor(party)).displayName()).isEqualTo("Synthetic party");
        UUID different=service.provision(new PartyValues.VerifiedIdentity(ISSUER,"other-"+subject,subject+"@example.invalid",true,"Synthetic different subject"));parties.add(different);assertThat(different).isNotEqualTo(party);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.customer_profile WHERE party_id=?",Integer.class,party)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE action='PARTY_PROVISION' AND resource_id=?",Integer.class,party)).isEqualTo(1);
        jdbc.update("UPDATE commerce.party SET status='SUSPENDED' WHERE id=?",party);assertThatThrownBy(()->service.provision(identity)).hasMessage("ACCESS_DENIED");
    }
    @Test void profilePatchVersionReplayEmailProtectionAndExactTypes(){
        var a=actor(party);var patch=json("{\"displayName\":\"Updated synthetic name\",\"phone\":null,\"locale\":\"en-NG\"}");String key=UUID.randomUUID().toString();
        var updated=service.patchProfile(a,0,patch,key,UUID.randomUUID());assertThat(updated.version()).isEqualTo(1);assertThat(updated.locale()).isEqualTo("en-NG");assertThat(updated.email()).isEqualTo(subject+"@example.invalid");
        assertThat(service.patchProfile(a,0,patch,key,UUID.randomUUID())).isEqualTo(updated);
        assertThatThrownBy(()->service.patchProfile(a,0,patch,"stale",UUID.randomUUID())).hasMessage("VERSION_MISMATCH");
        for(String bad:List.of("{\"email\":\"other@example.invalid\"}","{\"phone\":1234567890}","{\"displayName\":null}","{\"locale\":\"en_XX\"}","{}"))assertThatThrownBy(()->service.patchProfile(a,1,json(bad),UUID.randomUUID().toString(),UUID.randomUUID())).hasMessage("VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT new_value::text FROM commerce.audit_record WHERE action='PROFILE_UPDATE' AND resource_id=?",String.class,party)).doesNotContain("synthetic",subject);
    }
    @Test void defaultsAndDuplicatesSerializeWithHistoryAndSoftDelete()throws Exception{
        String key=UUID.randomUUID().toString();var first=create(key,true);assertThat(create(key,true)).isEqualTo(first);
        var gate=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->{gate.await();return create(UUID.randomUUID().toString(),true);});var b=pool.submit(()->{gate.await();return create(UUID.randomUUID().toString(),true);});gate.countDown();a.get(5,TimeUnit.SECONDS);b.get(5,TimeUnit.SECONDS);}
        var addresses=service.addresses(actor(party));assertThat(addresses).hasSize(3);assertThat(addresses.stream().filter(PartyValues.Address::isDefault).count()).isEqualTo(1);
        var current=addresses.stream().filter(v->v.id().equals(first.id())).findFirst().orElseThrow();assertThat(current.version()).isEqualTo(1);
        assertThatThrownBy(()->service.patchAddress(actor(party),first.id(),0,json("{\"line1\":\"Changed\"}"),"stale-address",UUID.randomUUID())).hasMessage("VERSION_MISMATCH");
        assertThatThrownBy(()->service.patchAddress(actor(other),first.id(),current.version(),json("{\"line1\":\"Changed\"}"),"idor",UUID.randomUUID())).hasMessage("NOT_FOUND");
        String deletion=UUID.randomUUID().toString();service.deleteAddress(actor(party),first.id(),current.version(),deletion,UUID.randomUUID());service.deleteAddress(actor(party),first.id(),current.version(),deletion,UUID.randomUUID());
        assertThat(service.addresses(actor(party))).hasSize(2);assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM commerce.address WHERE id=?",Boolean.class,first.id())).isTrue();
        assertThatThrownBy(()->service.deleteAddress(actor(party),first.id(),current.version(),"other-delete",UUID.randomUUID())).hasMessage("NOT_FOUND");
    }
    @Test void countryRulesFailClosedAndBoundRegex(){
        jdbc.update("UPDATE commerce.market SET enabled=false WHERE id=?",market);assertThatThrownBy(()->create("missing-policy",false)).hasMessage("FEATURE_DISABLED");jdbc.update("UPDATE commerce.market SET enabled=true WHERE id=?",market);
        jdbc.update("UPDATE commerce.market_destination SET address_rules=?::jsonb WHERE market_id=?","{\"schemaVersion\":1,\"requiredFields\":[\"REGION\",\"POSTAL_CODE\"],\"postalPattern\":\"^[0-9]{6}$\",\"regionCodes\":[\"SYNTHETIC\"]}",market);
        assertThatThrownBy(()->create("missing-region",false)).hasMessage("PARTY_RULE_VIOLATION");var valid=(tools.jackson.databind.node.ObjectNode)address(false);valid.put("region","SYNTHETIC");valid.put("postalCode","000000");assertThat(service.createAddress(actor(party),valid,"valid-policy",UUID.randomUUID()).postalCode()).isEqualTo("000000");
        for(String invalid:List.of("{\"schemaVersion\":1,\"requiredFields\":[],\"postalPattern\":\"(a)\\\\1\",\"regionCodes\":[]}","{\"schemaVersion\":4294967297,\"requiredFields\":[],\"postalPattern\":null,\"regionCodes\":[]}"))assertThatThrownBy(()->AddressRules.parse(json(invalid))).hasMessage("VALIDATION_ERROR");
        var safe=AddressRules.parse(json("{\"schemaVersion\":1,\"requiredFields\":[],\"postalPattern\":\"(a+)+$\",\"regionCodes\":[]}"));assertThatThrownBy(()->safe.validate(new PartyValues.Address(null,null,"r","p","l",null,"c",null,"aaaaaaaaaaaaaaaaaaaaaaab","NG",null,false,0))).hasMessage("PARTY_RULE_VIOLATION");
    }
    @Test void auditFailureRollsBackDefaultChangesAndCanRetry(){
        var first=create("first-default",true);var bootstrap=new JdbcTemplate(new DriverManagerDataSource(System.getenv("NIGSON_TEST_JDBC_URL"),"nigson_bootstrap","mock-bootstrap"));bootstrap.execute("REVOKE INSERT ON commerce.audit_record FROM nigson_commerce_app");
        try{assertThatThrownBy(()->create("audit-retry",true)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(service.addresses(actor(party))).containsExactly(first);}finally{bootstrap.execute("GRANT INSERT ON commerce.audit_record TO nigson_commerce_app");}
        assertThat(create("audit-retry",true).isDefault()).isTrue();assertThat(service.addresses(actor(party))).hasSize(2);
    }
    @Test void organizationalAddressesRequireCurrentMembershipAndDoNotTransferOwners(){
        UUID org=UUID.randomUUID();jdbc.update("INSERT INTO commerce.organization(id,created_by,updated_by,name,country_code,status) VALUES (?,'test','test','Synthetic organization','NG','ACTIVE')",org);
        try{var data=(tools.jackson.databind.node.ObjectNode)address(false);data.put("organizationId",org.toString());assertThatThrownBy(()->service.createAddress(actor(party),data,"no-membership",UUID.randomUUID())).hasMessage("NOT_FOUND");
            jdbc.update("INSERT INTO commerce.organization_membership(id,created_by,updated_by,organization_id,party_id,role,status) VALUES (?,'test','test',?,?,'BUYER','ACTIVE')",UUID.randomUUID(),org,party);var owned=service.createAddress(actor(party),data,"member",UUID.randomUUID());
            assertThatThrownBy(()->service.patchAddress(actor(other),owned.id(),0,json("{\"organizationId\":null}"),"other-org",UUID.randomUUID())).hasMessage("NOT_FOUND");
            jdbc.update("UPDATE commerce.organization_membership SET status='SUSPENDED' WHERE organization_id=? AND party_id=?",org,party);assertThat(service.addresses(actor(party))).isEmpty();assertThatThrownBy(()->service.deleteAddress(actor(party),owned.id(),0,"suspended",UUID.randomUUID())).hasMessage("NOT_FOUND");
        }finally{jdbc.update("DELETE FROM commerce.address WHERE organization_id=?",org);jdbc.update("DELETE FROM commerce.organization_membership WHERE organization_id=?",org);jdbc.update("DELETE FROM commerce.organization WHERE id=?",org);}
    }
}
