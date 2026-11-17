package com.nigson.commerce.wishlist;
import java.util.*;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.nigson.commerce.party.*;
import com.nigson.commerce.platform.*;
import com.nigson.shared.security.ActorAssertion;
import static org.assertj.core.api.Assertions.*;
class WishlistPersistenceTest {
 JdbcTemplate jdbc;DataSourceTransactionManager manager;WishlistService service;UUID party,other,product,sku,market;boolean createdMarket,marketWasEnabled;
 static final String ISSUER="https://identity.example.invalid/realms/test";
 final List<UUID> skus=new ArrayList<>();
 @BeforeEach void setup(){
  String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"Isolated PostgreSQL required");if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("ISOLATED_TEST_DB_REQUIRED");
  var source=new DriverManagerDataSource(url+"?currentSchema=commerce","nigson_commerce_app","mock-commerce-runtime");jdbc=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);service=newService();
  party=party();other=party();product=UUID.randomUUID();
  jdbc.update("INSERT INTO commerce.currency(id,created_by,updated_by,code,minor_unit) VALUES (?,'test','test','NGN',2) ON CONFLICT(code) DO NOTHING",UUID.randomUUID());
  var markets=jdbc.query("SELECT id FROM commerce.market WHERE code='NG'",(r,n)->r.getObject(1,UUID.class));createdMarket=markets.isEmpty();market=createdMarket?UUID.randomUUID():markets.getFirst();
  if(createdMarket)jdbc.update("INSERT INTO commerce.market(id,created_by,updated_by,code,name,currency_code,enabled) VALUES (?,'test','test','NG','Synthetic wishlist market','NGN',true)",market);
  marketWasEnabled=Boolean.TRUE.equals(jdbc.queryForObject("SELECT enabled FROM commerce.market WHERE id=?",Boolean.class,market));jdbc.update("UPDATE commerce.market SET enabled=true WHERE id=?",market);
  jdbc.update("INSERT INTO commerce.product(id,created_by,updated_by,name,slug,description,status) VALUES (?,'test','test','Synthetic wishlist product',?,'Synthetic fixture','ACTIVE')",product,"wishlist-"+product);
  jdbc.update("INSERT INTO commerce.product_market_eligibility(id,created_by,updated_by,product_id,market_id,eligible) VALUES (?,'test','test',?,?,true)",UUID.randomUUID(),product,market);sku=sku();
 }
 WishlistService newService(){var clock=Clock.systemUTC();var metrics=new SimpleMeterRegistry();var audit=new AuditWriter(jdbc,clock,metrics);var tx=new TransactionTemplate(manager);tx.setTimeout(5);return new WishlistService(new JdbcWishlistRepository(jdbc),new JdbcPartyRepository(jdbc,clock,1,audit),new PlatformStore(jdbc,manager,clock,metrics),audit,tx);}
 UUID party(){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_subject,identity_issuer,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic wishlist customer')",id,id.toString(),ISSUER,id+"@example.invalid");return id;}
 UUID sku(){UUID id=UUID.randomUUID();skus.add(id);jdbc.update("INSERT INTO commerce.sku(id,created_by,updated_by,product_id,code,variant_name,unit) VALUES (?,'test','test',?,?,'Synthetic variant','piece')",id,product,"W-"+id);return id;}
 ActorAssertion.Actor actor(UUID id){return new ActorAssertion.Actor(id,ISSUER,id.toString(),null,Set.of("CUSTOMER"),Set.of(),null);}
 void save(UUID owner,UUID id,String key){service.set(actor(owner),id,true,key,UUID.randomUUID());}
 int count(String table,String where,Object...args){return jdbc.queryForObject("SELECT count(*) FROM commerce."+table+" WHERE "+where,Integer.class,args);}
 @AfterEach void cleanup(){if(jdbc==null||party==null)return;for(UUID p:List.of(party,other)){jdbc.update("DELETE FROM commerce.wishlist_item WHERE party_id=?",p);jdbc.update("DELETE FROM commerce.idempotency_record WHERE principal_key=?",p.toString());jdbc.update("DELETE FROM commerce.party WHERE id=?",p);}for(UUID id:skus)jdbc.update("DELETE FROM commerce.sku WHERE id=?",id);jdbc.update("DELETE FROM commerce.product_market_eligibility WHERE product_id=?",product);jdbc.update("DELETE FROM commerce.product WHERE id=?",product);if(createdMarket)jdbc.update("DELETE FROM commerce.market WHERE id=?",market);else jdbc.update("UPDATE commerce.market SET enabled=? WHERE id=?",marketWasEnabled,market);}
 @Test void savesOnceWithStableTimestampAndPersistsAcrossServiceReload(){save(party,sku,"save-one");var first=service.list(actor(party)).getFirst();save(party,sku,"save-two");assertThat(newService().list(actor(party))).containsExactly(first);assertThat(count("wishlist_item","party_id=?",party)).isEqualTo(1);assertThat(count("audit_record","actor_subject=? AND action='WISHLIST_SAVE'",party.toString())).isEqualTo(1);}
 @Test void accountIsolationAlsoAppliesToDeletes(){save(party,sku,"save");assertThat(service.list(actor(other))).isEmpty();service.set(actor(other),sku,false,"remove-other",UUID.randomUUID());assertThat(service.list(actor(party))).hasSize(1);assertThat(count("audit_record","actor_subject=? AND action='WISHLIST_REMOVE'",other.toString())).isZero();}
 @Test void unavailableSkuRemainsRemovableButCannotBeNewlySaved(){save(party,sku,"save");jdbc.update("UPDATE commerce.sku SET active=false WHERE id=?",sku);assertThat(service.list(actor(party)).getFirst().available()).isFalse();assertThatThrownBy(()->save(other,sku,"new-save")).hasMessage("NOT_FOUND");service.set(actor(party),sku,false,"remove",UUID.randomUUID());assertThat(service.list(actor(party))).isEmpty();}
 @Test void staleIdempotencyReplayDoesNotResurrectAfterExplicitRemoval(){save(party,sku,"save");service.set(actor(party),sku,false,"remove",UUID.randomUUID());save(party,sku,"save");assertThat(service.list(actor(party))).isEmpty();}
 @Test void inactiveIdentityCannotReadOrReplay(){save(party,sku,"save");jdbc.update("UPDATE commerce.party SET status='SUSPENDED' WHERE id=?",party);assertThatThrownBy(()->service.list(actor(party))).hasMessage("AUTH_REQUIRED");assertThatThrownBy(()->save(party,sku,"save")).hasMessage("AUTH_REQUIRED");}
 @Test void anonymousWrongIdentityAndOrganizationContextAreDenied(){assertThatThrownBy(()->service.list(null)).hasMessage("AUTH_REQUIRED");var wrong=new ActorAssertion.Actor(party,ISSUER,"wrong-subject",null,Set.of("CUSTOMER"),Set.of(),null);assertThatThrownBy(()->service.list(wrong)).hasMessage("AUTH_REQUIRED");var organization=new ActorAssertion.Actor(party,ISSUER,party.toString(),UUID.randomUUID(),Set.of("CUSTOMER"),Set.of(),null);assertThatThrownBy(()->service.list(organization)).hasMessage("ACCESS_DENIED");}
 @Test void concurrentSavesHaveOneMembershipAndOneAudit()throws Exception{var gate=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->{gate.await();save(party,sku,"parallel-a");return true;});var b=pool.submit(()->{gate.await();save(party,sku,"parallel-b");return true;});gate.countDown();assertThat(a.get(10,TimeUnit.SECONDS)).isTrue();assertThat(b.get(10,TimeUnit.SECONDS)).isTrue();}assertThat(service.list(actor(party))).hasSize(1);assertThat(count("audit_record","actor_subject=? AND action='WISHLIST_SAVE'",party.toString())).isEqualTo(1);}
 @Test void hundredItemLimitDoesNotBlockExistingMembershipOrRemoval(){for(int i=0;i<100;i++){UUID id=sku();jdbc.update("INSERT INTO commerce.wishlist_item(party_id,sku_id,created_by,updated_by) VALUES (?,?,'test','test')",party,id);}assertThat(service.list(actor(party))).hasSize(100);assertThatThrownBy(()->save(party,sku,"over-limit")).hasMessage("BUSINESS_RULE_VIOLATION");save(party,skus.get(1),"existing-at-limit");service.set(actor(party),skus.get(1),false,"remove-at-limit",UUID.randomUUID());save(party,sku,"after-remove");assertThat(service.list(actor(party))).hasSize(100);}
 @Test void reusedKeyForAnotherSkuIsRejected(){save(party,sku,"same-key");UUID different=sku();assertThatThrownBy(()->save(party,different,"same-key")).hasMessage("IDEMPOTENCY_HASH_MISMATCH");assertThat(service.list(actor(party))).hasSize(1);}
}
