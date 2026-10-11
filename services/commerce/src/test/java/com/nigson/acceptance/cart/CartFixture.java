package com.nigson.acceptance.cart;
import com.nigson.acceptance.pricing.PricingFixture;
import com.nigson.commerce.cart.*;
import com.nigson.commerce.pricing.*;
import com.nigson.commerce.platform.*;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
public final class CartFixture implements AutoCloseable {
 public final PricingFixture f;public final java.util.List<java.util.UUID> extraSkus=new java.util.ArrayList<>();public final CartService carts;public final CartCheckout checkout;
 public CartFixture(JdbcTemplate jdbc,PlatformTransactionManager manager){f=new PricingFixture(jdbc,manager);Clock clock=Clock.systemUTC();carts=new CartService(new JdbcCartRepository(jdbc,clock,new JdbcPricing(jdbc,3)),new PricingAuthority(jdbc,clock,3,"SYNTHETIC_DISTRIBUTOR_BUY"),jdbc,new PlatformStore(jdbc,manager,clock,f.metrics),new AuditWriter(jdbc,clock,f.metrics),f.metrics,manager);checkout=new JdbcCartCheckout(jdbc,new JdbcCartRepository(jdbc,clock,new JdbcPricing(jdbc,3)),clock,new AuditWriter(jdbc,clock,f.metrics),f.metrics);}
 public void close(){f.jdbc.update("UPDATE commerce.cart SET converted_order_id=NULL WHERE party_id=? AND market_id=?",f.customer.party(),f.market);f.admin.update("DELETE FROM commerce.payment_transaction WHERE attempt_id IN(SELECT a.id FROM commerce.payment_attempt a JOIN commerce.sales_order o ON o.id=a.order_id WHERE o.party_id=? AND o.market_id=?)",f.customer.party(),f.market);f.jdbc.update("DELETE FROM commerce.payment_attempt WHERE order_id IN(SELECT id FROM commerce.sales_order WHERE party_id=? AND market_id=?)",f.customer.party(),f.market);f.jdbc.update("DELETE FROM commerce.sales_order WHERE party_id=? AND market_id=?",f.customer.party(),f.market);f.admin.update("DELETE FROM commerce.checkout_quote WHERE cart_id IN(SELECT id FROM commerce.cart WHERE party_id=? AND market_id=?)",f.customer.party(),f.market);f.jdbc.update("DELETE FROM commerce.carrier_quote WHERE cart_id IN(SELECT id FROM commerce.cart WHERE party_id=? AND market_id=?)",f.customer.party(),f.market);f.jdbc.update("DELETE FROM commerce.cart_line WHERE cart_id IN(SELECT id FROM commerce.cart WHERE party_id=? AND market_id=?)",f.customer.party(),f.market);f.jdbc.update("DELETE FROM commerce.cart WHERE party_id=? AND market_id=?",f.customer.party(),f.market);for(var sku:extraSkus)f.jdbc.update("DELETE FROM commerce.sku WHERE id=?",sku);f.close();}
}
