package com.nigson.commerce.wishlist;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
public final class JdbcWishlistRepository implements WishlistRepository {
 private final JdbcTemplate jdbc;
 private static final String ELIGIBLE="s.active AND s.deleted_at IS NULL AND p.status='ACTIVE' AND p.deleted_at IS NULL AND EXISTS(SELECT 1 FROM commerce.product_market_eligibility e JOIN commerce.market m ON m.id=e.market_id WHERE e.product_id=p.id AND e.eligible AND m.enabled AND m.code='NG')";
 public JdbcWishlistRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<WishlistValues.Item> list(UUID party){return List.copyOf(jdbc.query("SELECT w.sku_id,s.product_id,w.created_at,("+ELIGIBLE+") AS available FROM commerce.wishlist_item w JOIN commerce.sku s ON s.id=w.sku_id JOIN commerce.product p ON p.id=s.product_id WHERE w.party_id=? ORDER BY w.created_at DESC,w.sku_id LIMIT 100",(r,n)->new WishlistValues.Item(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getTimestamp(3).toInstant(),r.getBoolean(4)),party));}
 public boolean eligible(UUID sku){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.sku s JOIN commerce.product p ON p.id=s.product_id WHERE s.id=? AND "+ELIGIBLE+")",Boolean.class,sku));}
 public boolean contains(UUID party,UUID sku){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.wishlist_item WHERE party_id=? AND sku_id=?)",Boolean.class,party,sku));}
 public int count(UUID party){return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM commerce.wishlist_item WHERE party_id=?",Integer.class,party));}
 public boolean add(UUID party,UUID sku){return jdbc.update("INSERT INTO commerce.wishlist_item(party_id,sku_id,created_by,updated_by) VALUES (?,?,?,?) ON CONFLICT(party_id,sku_id) DO NOTHING",party,sku,party.toString(),party.toString())==1;}
 public boolean remove(UUID party,UUID sku){return jdbc.update("DELETE FROM commerce.wishlist_item WHERE party_id=? AND sku_id=?",party,sku)==1;}
}
