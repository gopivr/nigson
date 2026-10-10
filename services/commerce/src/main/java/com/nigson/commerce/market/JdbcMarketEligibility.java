package com.nigson.commerce.market;
import java.util.*;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.shared.events.EventCodec;
import tools.jackson.databind.JsonNode;
/** Revalidate all owned policy under caller checkout transaction, before any stock effect or provider HTTP. */
public final class JdbcMarketEligibility implements MarketEligibility {
 private final JdbcTemplate jdbc;private final Clock clock;private final String environment;
 public JdbcMarketEligibility(JdbcTemplate jdbc,Clock clock,String environment){this.jdbc=jdbc;this.clock=clock;this.environment=environment;if(!Set.of("local","test","dev","qa","stage","prod").contains(environment))throw new IllegalArgumentException("MARKET_ENVIRONMENT_INVALID");}
 public Sale require(UUID market,String country,Set<UUID> products,JsonNode address,ProviderProof providers){
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");
  if(market==null||market.equals(new UUID(0,0))||!"NG".equals(country)||products==null||products.isEmpty()||products.size()>100||products.stream().anyMatch(p->p==null||p.equals(new UUID(0,0)))||providers==null||!"NGN".equals(providers.currency())||!country.equals(providers.country())||providers.validUntil()==null||!clock.instant().isBefore(providers.validUntil())||providers.mode()==null||!Set.of("MOCK","TEST","LIVE").contains(providers.mode())||!Set.of("local","test").contains(environment)&&providers.mode().equals("MOCK")||environment.equals("prod")&&!providers.mode().equals("LIVE")||!reference(providers.paymentApprovalReference())||!reference(providers.carrierApprovalReference()))throw MarketValues.rule();
  jdbc.execute("SET LOCAL lock_timeout='500ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");
  for(UUID product:products.stream().sorted(Comparator.comparing(UUID::toString)).toList()){if(jdbc.query("SELECT id FROM commerce.product WHERE id=? AND status='ACTIVE' AND deleted_at IS NULL FOR SHARE",(r,n)->r.getObject(1,UUID.class),product).isEmpty())throw MarketValues.rule();}
  var markets=jdbc.query("SELECT m.id FROM commerce.market m JOIN commerce.currency c ON c.code=m.currency_code WHERE m.id=? AND m.code='NG' AND m.enabled AND c.code='NGN' AND c.active AND c.minor_unit=2 FOR SHARE OF m,c",(r,n)->r.getObject(1,UUID.class),market);if(markets.isEmpty())throw MarketValues.rule();
  var v=new JdbcMarketRepository(jdbc,clock).find(market,false);MarketValues.TaxPolicy tax;try{tax=MarketValues.tax(v.taxPolicy());}catch(RuntimeException invalid){throw MarketValues.rule();}if(v.returnsPolicyRevision()==null||v.returnsPolicyRevision().isBlank()||v.retailPriceBookId()==null)throw MarketValues.rule();
  var destinations=jdbc.query("SELECT address_rules::text FROM commerce.market_destination WHERE market_id=? AND country_code=? AND delivery_enabled FOR SHARE",(r,n)->EventCodec.JSON.readTree(r.getString(1)),market,country);if(destinations.size()!=1)throw MarketValues.rule();var rules=destinations.getFirst();try{MarketValues.address(rules);}catch(RuntimeException invalid){throw MarketValues.rule();}validateAddress(address,rules,country);
  var books=jdbc.query("SELECT id FROM commerce.price_book WHERE id=? AND active AND segment='RETAIL' AND currency_code='NGN' AND effective_from<=? AND (effective_until IS NULL OR effective_until>?) FOR SHARE",(r,n)->r.getObject(1,UUID.class),v.retailPriceBookId(),java.sql.Timestamp.from(clock.instant()),java.sql.Timestamp.from(clock.instant()));if(books.isEmpty())throw MarketValues.rule();
  for(UUID product:products.stream().sorted(Comparator.comparing(UUID::toString)).toList()){var eligible=jdbc.query("SELECT p.id FROM commerce.product p JOIN commerce.product_market_eligibility e ON e.product_id=p.id WHERE p.id=? AND p.status='ACTIVE' AND p.deleted_at IS NULL AND e.market_id=? AND e.eligible FOR SHARE OF p,e",(r,n)->r.getObject(1,UUID.class),product,market);if(eligible.isEmpty())throw MarketValues.rule();}
  return new Sale(market,"NGN",2,v.policyRevision(),v.retailPriceBookId(),tax,v.returnsPolicyRevision());
 }
 private static boolean reference(String ref){return ref!=null&&ref.matches("[A-Za-z0-9][A-Za-z0-9_./:-]{0,119}");}
 private static void validateAddress(JsonNode address,JsonNode rules,String country){try{var parsed=com.nigson.commerce.party.PartyValues.address(address,null);if(!country.equals(parsed.countryCode())||parsed.organizationId()!=null)throw MarketValues.rule();com.nigson.commerce.party.AddressRules.parse(rules).validate(parsed);}catch(RuntimeException invalid){throw MarketValues.rule();}}
}
