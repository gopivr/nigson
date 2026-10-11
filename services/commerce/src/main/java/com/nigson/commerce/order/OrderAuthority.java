package com.nigson.commerce.order;
import java.util.*;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.commerce.pricing.PricingAuthority;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
/** Current historical-order read authority is separate from wholesale buying entitlement. */
public final class OrderAuthority {
 private final JdbcTemplate jdbc;private final PricingAuthority pricing;private final Clock clock;
 public OrderAuthority(JdbcTemplate jdbc,PricingAuthority pricing,Clock clock){this.jdbc=jdbc;this.pricing=pricing;this.clock=clock;}
 public void customer(ActorAssertion.Actor actor,boolean lock){if(lock)pricing.stabilize(actor,"CUSTOMER");else pricing.require(actor,"CUSTOMER");}
 public void staff(ActorAssertion.Actor actor,boolean operations,boolean lock){if(actor==null)throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(actor.organization()!=null)throw new Problem.Failure(ProblemCode.ACCESS_DENIED);if(lock)jdbc.query("SELECT id FROM commerce.party WHERE id=? FOR SHARE",(r,n)->r.getObject(1,UUID.class),actor.party());boolean found=false;for(String role:operations?List.of("OPERATIONS"):List.of("OPERATIONS","FINANCE")){if(actor.roles().contains(role)&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.party p JOIN commerce.staff_role_assignment a ON a.party_id=p.id WHERE p.id=? AND p.identity_issuer=? AND p.identity_subject=? AND p.status='ACTIVE' AND a.role=? AND a.revoked_at IS NULL)",Boolean.class,actor.party(),actor.identityIssuer(),actor.identitySubject(),role))){if(role.equals("FINANCE"))ActorAssertion.recentMfa(actor,clock);found=true;break;}}if(!found)throw new Problem.Failure(ProblemCode.ACCESS_DENIED);}
 public void organization(ActorAssertion.Actor actor,UUID org,boolean lock){customer(actor,false);if(org==null)return;if(jdbc.query("SELECT o.id FROM commerce.organization o JOIN commerce.organization_membership m ON m.organization_id=o.id WHERE o.id=? AND o.status='ACTIVE' AND m.party_id=? AND m.status='ACTIVE'"+(lock?" FOR SHARE OF o,m":""),(r,n)->r.getObject(1,UUID.class),org,actor.party()).isEmpty())throw new Problem.Failure(ProblemCode.NOT_FOUND);}
 public void owned(ActorAssertion.Actor actor,UUID order,boolean lock){customer(actor,lock);var rows=jdbc.queryForList("SELECT party_id,organization_id FROM commerce.sales_order WHERE id=?",order);if(rows.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);UUID org=(UUID)rows.getFirst().get("organization_id");if(org==null){if(!actor.party().equals(rows.getFirst().get("party_id")))throw new Problem.Failure(ProblemCode.NOT_FOUND);}else organization(actor,org,lock);}
}
