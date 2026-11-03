package com.nigson.acceptance.party;

import com.nigson.commerce.party.JdbcPartyRepository;
import com.nigson.commerce.order.JdbcOrderRepository;
import com.nigson.commerce.order.OrderValues;
import com.nigson.shared.security.ActorAssertion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** BP1 reads must work when later organization tables are absent. Isolated harness only. */
class Bp1ReadPersistenceTest {
 @Test void customerListsNeverReferenceLaterReleaseTables(){
  String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null);
  if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");
  var bootstrap=new JdbcTemplate(new DriverManagerDataSource(url,"nigson_bootstrap","mock-bootstrap"));
  var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"nigson_commerce_app","mock-commerce-runtime"));
  var renamed=new ArrayList<String>();
  UUID party=UUID.randomUUID(),address=UUID.randomUUID();
  try {
   for(String table:List.of("organization","organization_membership","account_entitlement")){
    bootstrap.execute("ALTER TABLE commerce."+table+" RENAME TO bp1_hidden_"+table);renamed.add(table);
   }
   jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_subject,identity_issuer,email,display_name) VALUES (?,'test','test',?,'https://identity.example.invalid','bp1@example.invalid','BP1 fixture')",party,party.toString());
   jdbc.update("INSERT INTO commerce.address(id,created_by,updated_by,party_id,recipient,phone,line1,city,country_code) VALUES (?,'test','test',?,'BP1 fixture','+2340000000000','1 Test Street','Lagos','NG')",address,party);
   var parties=new JdbcPartyRepository(jdbc,Clock.systemUTC(),1,null);
   assertThat(parties.addresses(party)).extracting(a->a.id()).containsExactly(address);
   assertThat(parties.addresses(UUID.randomUUID())).isEmpty();
   var orders=new JdbcOrderRepository(jdbc,null,null,null,null,null,Clock.systemUTC(),1);
   var actor=new ActorAssertion.Actor(party,"https://identity.example.invalid",party.toString(),null,Set.of("CUSTOMER"),Set.of(),null);
   assertThat(orders.list(actor,new OrderValues.Query(null,null,null,null,null,0,20,"createdAt,desc"),false).items()).isEmpty();
   assertThatThrownBy(()->orders.list(actor,new OrderValues.Query(UUID.randomUUID(),null,null,null,null,0,20,"createdAt,desc"),false)).hasMessage("NOT_FOUND");
  } finally {
   jdbc.update("DELETE FROM commerce.address WHERE id=?",address);
   jdbc.update("DELETE FROM commerce.party WHERE id=?",party);
   Collections.reverse(renamed);
   for(String table:renamed)bootstrap.execute("ALTER TABLE commerce.bp1_hidden_"+table+" RENAME TO "+table);
  }
 }
}
