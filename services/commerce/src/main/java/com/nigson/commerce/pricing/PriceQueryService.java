package com.nigson.commerce.pricing;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
public final class PriceQueryService {
 private final Pricing pricing;private final PricingAuthority authority;private final JdbcTemplate jdbc;private final Clock clock;private final TransactionTemplate reads;private final int ttl,budget;private long second=-1;private int calls;
 public PriceQueryService(Pricing pricing,PricingAuthority authority,JdbcTemplate jdbc,Clock clock,org.springframework.transaction.PlatformTransactionManager manager,int ttl,int budget){if(ttl<1||ttl>300||budget<1||budget>10000)throw new IllegalArgumentException("PRICING_READ_CONFIGURATION_INVALID");this.pricing=pricing;this.authority=authority;this.jdbc=jdbc;this.clock=clock;this.ttl=ttl;this.budget=budget;reads=new TransactionTemplate(manager);reads.setReadOnly(true);reads.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);reads.setTimeout(3);}
 private synchronized void admit(){long now=clock.instant().getEpochSecond();if(now!=second){second=now;calls=0;}if(++calls>budget)throw new Problem.Failure(ProblemCode.RATE_LIMITED);}
 public PricingValues.Price price(ActorAssertion.Actor actor,UUID sku,String market,String quantity,UUID organization){admit();if(market==null||!market.matches("[A-Z][A-Z0-9_-]{1,31}"))throw ApiValues.invalid();BigDecimal qty=ApiValues.quantity(quantity);if(organization!=null)authority.organization(actor,organization,false);return reads.execute(s->{jdbc.execute("SET LOCAL statement_timeout='1s'");if(actor!=null)authority.require(actor,"CUSTOMER");if(organization!=null)authority.organization(actor,organization,false);var rows=jdbc.query("SELECT id FROM commerce.market WHERE code=?",(r,n)->r.getObject(1,UUID.class),market);if(rows.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);Instant now=clock.instant();var price=pricing.resolve(sku,qty,rows.getFirst(),organization,now,now.plusSeconds(ttl),false);return new PricingValues.Price(sku,price.unitAmount().toPlainString(),"NGN",PricingValues.decimal(price.minimumQuantity()),PricingValues.decimal(price.packMultiple()),price.bookRevision(),price.validUntil());});}
}
