package com.nigson.commerce.inventory;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.shared.problem.*;

/** Read-only advisory capacity. Checkout still locks and reserves the actual balance. */
public final class StockAvailability {
 public record Stock(String status,String availableQuantity,String lowStockThreshold){}
 private final JdbcTemplate jdbc;private final List<UUID> locations;private final BigDecimal low;
 public StockAvailability(JdbcTemplate jdbc,Collection<UUID> locations,BigDecimal low){if(low==null||low.signum()<0||low.scale()>3||low.compareTo(InventoryValues.MAX)>0||locations!=null&&(locations.size()>10||new HashSet<>(locations).size()!=locations.size()))throw new IllegalArgumentException("STOCK_AVAILABILITY_POLICY_INVALID");this.jdbc=jdbc;this.locations=locations==null?null:List.copyOf(locations);this.low=low;}
 public static Stock classify(BigDecimal available,BigDecimal pack,BigDecimal low){var qty=available.max(BigDecimal.ZERO);return new Stock(qty.compareTo(pack)<0?"OUT_OF_STOCK":qty.compareTo(low)<=0?"LOW_STOCK":"IN_STOCK",qty.stripTrailingZeros().toPlainString(),low.stripTrailingZeros().toPlainString());}
 public Map<UUID,Stock> product(UUID product){return read("s.product_id",product);}
 public Stock sku(UUID sku){return read("s.id",sku).getOrDefault(sku,classify(BigDecimal.ZERO,BigDecimal.ONE,low));}
 public String locationPredicate(){return locations==null?"l.active":locations.isEmpty()?"FALSE":"l.active AND l.id IN ("+String.join(",",locations.stream().map(id->"'"+id+"'::uuid").toList())+")";}
 private Map<UUID,Stock> read(String column,UUID id){String scope=locations==null?"l.active":"l.active AND l.id=ANY(?::uuid[])";String sql="SELECT s.id,s.pack_size,COALESCE(MAX(CASE WHEN "+scope+" THEN GREATEST(0,b.on_hand-b.reserved-b.allocated) ELSE 0 END),0) FROM commerce.sku s LEFT JOIN commerce.inventory_balance b ON b.sku_id=s.id LEFT JOIN commerce.stock_location l ON l.id=b.location_id WHERE "+column+"=? AND s.active AND s.deleted_at IS NULL GROUP BY s.id,s.pack_size ORDER BY s.id LIMIT 101";
  var result=new LinkedHashMap<UUID,Stock>();jdbc.query(sql,p->{int index=1;if(locations!=null)p.setArray(index++,p.getConnection().createArrayOf("uuid",locations.toArray()));p.setObject(index,id);},(org.springframework.jdbc.core.RowCallbackHandler)r->result.put(r.getObject(1,UUID.class),classify(r.getBigDecimal(3),r.getBigDecimal(2),low)));if(result.size()>100)throw new Problem.Failure(ProblemCode.CATALOGUE_RULE_VIOLATION);return result;}
 public void require(UUID sku,BigDecimal quantity){Stock stock=sku(sku);if("OUT_OF_STOCK".equals(stock.status())||quantity.compareTo(new BigDecimal(stock.availableQuantity()))>0)throw new Problem.Failure(ProblemCode.CART_RULE_VIOLATION);}
}
