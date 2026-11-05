package com.nigson.commerce.backoffice;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.http.ResponseEntity;
import com.nigson.commerce.security.CommerceAuthority;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.events.EventCodec;
/** Bounded Commerce-owned staff read projections, with current role checks. */
@RestController @ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class AdminReadController {
 private final JdbcTemplate jdbc;private final CommerceAuthority authority;private final TransactionTemplate read;
 public AdminReadController(JdbcTemplate jdbc,CommerceAuthority authority,PlatformTransactionManager manager){this.jdbc=jdbc;this.authority=authority;read=new TransactionTemplate(manager);read.setReadOnly(true);read.setTimeout(3);}
 private void role(String role){var a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!(a.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);authority.requireRole(actor,role);}
 private ResponseEntity<?> page(HttpServletRequest r,String role,int page,int size,String count,String query){role(role);if(page<0||page>10000||size<1||size>100||!Set.of("page","size").containsAll(r.getParameterMap().keySet()))throw ApiValues.invalid();return ResponseEntity.ok().header("Cache-Control","no-store").body(read.execute(s->{jdbc.execute("SET LOCAL statement_timeout='2s'");long total=jdbc.queryForObject(count,Long.class);var items=jdbc.query(query,(row,n)->EventCodec.JSON.readTree(row.getString(1)),size,page*size);return Map.of("items",items,"page",page,"size",size,"totalElements",total,"totalPages",(total+size-1)/size);}));}
 @GetMapping("/api/v1/staff/quotes") public ResponseEntity<?> quotes(HttpServletRequest r,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="100")int size){return page(r,"SALES",page,size,"SELECT count(*) FROM commerce.quote", "SELECT jsonb_build_object('id',q.id,'state',q.status,'version',q.version,'createdAt',q.created_at,'partyId',q.party_id,'customerName',p.display_name,'customerEmail',p.email,'revisionId',v.id,'approvalStatus',v.approval_status,'terms',v.terms,'total',v.total,'validUntil',v.valid_until)::text FROM commerce.quote q JOIN commerce.party p ON p.id=q.party_id LEFT JOIN LATERAL(SELECT * FROM commerce.quote_revision WHERE quote_id=q.id ORDER BY revision DESC LIMIT 1)v ON true ORDER BY q.created_at DESC,q.id LIMIT ? OFFSET ?");}
 @GetMapping("/api/v1/staff/inventory-movements") public ResponseEntity<?> movements(HttpServletRequest r,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="100")int size){return page(r,"OPERATIONS",page,size,"SELECT count(*) FROM commerce.inventory_movement", "SELECT jsonb_build_object('id',m.id,'skuId',b.sku_id,'locationId',b.location_id,'sku',s.code,'product',p.name,'change',m.delta_on_hand,'reservedChange',m.delta_reserved,'allocatedChange',m.delta_allocated,'reason',m.reason,'date',m.created_at,'by',m.created_by,'type',m.movement_type)::text FROM commerce.inventory_movement m JOIN commerce.inventory_balance b ON b.id=m.balance_id JOIN commerce.sku s ON s.id=b.sku_id JOIN commerce.product p ON p.id=s.product_id ORDER BY m.created_at DESC,m.id DESC LIMIT ? OFFSET ?");}
 @GetMapping("/api/v1/staff/customers") public ResponseEntity<?> customers(HttpServletRequest r,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="100")int size){return page(r,"SALES",page,size,"SELECT count(*) FROM commerce.party p JOIN commerce.customer_profile c ON c.party_id=p.id", "SELECT jsonb_build_object('id',p.id,'name',p.display_name,'email',p.email,'phone',c.phone,'joined',p.created_at,'status',p.status,'orders',(SELECT count(*) FROM commerce.sales_order o WHERE o.party_id=p.id),'spend',(SELECT coalesce(sum(o.grand_total-o.refunded_total),0) FROM commerce.sales_order o WHERE o.party_id=p.id AND o.payment_status IN ('PAID','PARTIALLY_REFUNDED','REFUNDED')),'quotes',(SELECT count(*) FROM commerce.quote q WHERE q.party_id=p.id))::text FROM commerce.party p JOIN commerce.customer_profile c ON c.party_id=p.id ORDER BY p.created_at DESC,p.id LIMIT ? OFFSET ?");}
}
