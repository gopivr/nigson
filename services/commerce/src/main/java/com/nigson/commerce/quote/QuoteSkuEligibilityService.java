package com.nigson.commerce.quote;

import com.nigson.commerce.pricing.PricingAuthority;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Narrow Commerce-owned read used by Experience quote-request validation. */
public final class QuoteSkuEligibilityService {
 public record Item(UUID skuId,String quantity){}
 public record Input(UUID partyId,UUID marketId,UUID organizationId,List<Item> items){public Input{items=List.copyOf(items);}}
 public record Eligible(UUID skuId,int quantityScale){}
 public record Result(List<Eligible> items){public Result{items=List.copyOf(items);}}
 private final JdbcTemplate jdbc;private final PricingAuthority authority;
 public QuoteSkuEligibilityService(JdbcTemplate jdbc,PricingAuthority authority){this.jdbc=jdbc;this.authority=authority;}
 private void require(PrivateWorkload.Proof proof){var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-206")&&r.owner().equals("commerce")&&r.client().equals("nigson-experience")).findFirst().orElseThrow(()->new Problem.Failure(ProblemCode.ACCESS_DENIED));if(proof==null||!route.equals(proof.route())||proof.actor()!=null||!"nigson-experience".equals(proof.client()))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);}
 public Result validate(PrivateWorkload.Proof proof,Input input){require(proof);if(input==null||input.partyId()==null||input.marketId()==null||input.items().isEmpty()||input.items().size()>50)throw ApiValues.invalid();if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.party WHERE id=? AND status='ACTIVE')",Boolean.class,input.partyId())))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);if(input.organizationId()!=null)authority.serviceOrganization(input.partyId(),input.organizationId());var market=jdbc.queryForList("SELECT id FROM commerce.market WHERE id=? AND code='NG' AND currency_code='NGN' AND enabled",input.marketId());if(market.size()!=1)throw new Problem.Failure(ProblemCode.QUOTE_RULE_VIOLATION);var seen=new HashSet<UUID>();var result=new ArrayList<Eligible>();for(Item item:input.items()){if(item==null||item.skuId()==null||!seen.add(item.skuId())||item.quantity()==null)throw ApiValues.invalid();BigDecimal quantity=ApiValues.quantity(item.quantity());var rows=jdbc.query("SELECT s.quantity_scale FROM commerce.sku s JOIN commerce.product p ON p.id=s.product_id JOIN commerce.product_market_eligibility e ON e.product_id=p.id AND e.market_id=? WHERE s.id=? AND s.active AND s.deleted_at IS NULL AND p.status='ACTIVE' AND p.deleted_at IS NULL AND e.eligible",(r,n)->r.getInt(1),input.marketId(),item.skuId());if(rows.size()!=1)throw new Problem.Failure(ProblemCode.QUOTE_RULE_VIOLATION);int scale=rows.getFirst();if(quantity.scale()>scale)throw new Problem.Failure(ProblemCode.QUOTE_RULE_VIOLATION);result.add(new Eligible(item.skuId(),scale));}return new Result(result);}
}
