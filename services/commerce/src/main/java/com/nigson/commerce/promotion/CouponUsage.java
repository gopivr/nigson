package com.nigson.commerce.promotion;
import java.time.*;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import tools.jackson.databind.JsonNode;

/** Counters and order redemption state change in the caller's Commerce transaction. */
public final class CouponUsage {
 private CouponUsage(){}
 public static void reserve(JdbcTemplate jdbc,Clock clock,UUID order,UUID party,JsonNode commercial,String actor){
  JsonNode revisions=commercial.path("promotionRevisions");if(!revisions.isArray())throw PromotionValues.rule();JsonNode chosen=null;for(JsonNode revision:revisions)if(revision.has("couponId")){if(chosen!=null)throw PromotionValues.rule();chosen=revision;}
  if(chosen==null)return;UUID coupon=ApiValues.uuid(chosen.path("couponId").asString());BigDecimal amount=new BigDecimal(chosen.path("discount").asString()).add(new BigDecimal(chosen.path("deliveryDiscount").asString())).setScale(2);var rows=jdbc.query("SELECT maximum_uses,maximum_uses_per_party,reserved_uses,consumed_uses,party_id,expires_at,active FROM commerce.coupon WHERE id=? FOR UPDATE",(r,n)->new Object[]{r.getInt(1),r.getInt(2),r.getInt(3),r.getInt(4),r.getObject(5,UUID.class),r.getTimestamp(6).toInstant(),r.getBoolean(7)},coupon);if(rows.size()!=1)throw PromotionValues.rule();var row=rows.getFirst();int max=(int)row[0],perParty=(int)row[1],reserved=(int)row[2],consumed=(int)row[3];if(!Boolean.TRUE.equals(row[6])||((Instant)row[5]).compareTo(clock.instant())<=0||row[4]!=null&&!party.equals(row[4])||reserved+consumed>=max)throw PromotionValues.rule();Integer partyCount=jdbc.queryForObject("SELECT count(*) FROM commerce.coupon_redemption WHERE coupon_id=? AND party_id=? AND state IN ('RESERVED','CONSUMED')",Integer.class,coupon,party);if(partyCount==null||partyCount>=perParty)throw PromotionValues.rule();UUID id=EventCodec.uuid7(clock.instant());jdbc.update("UPDATE commerce.coupon SET reserved_uses=reserved_uses+1,version=version+1,updated_at=?,updated_by=? WHERE id=?",Timestamp.from(clock.instant()),actor,coupon);jdbc.update("INSERT INTO commerce.coupon_redemption(id,created_by,updated_by,coupon_id,party_id,order_id,state,amount,expires_at) VALUES (?,?,?,?,?,?,'RESERVED',?,?)",id,actor,actor,coupon,party,order,amount,Timestamp.from((Instant)row[5]));
 }
 public static void consume(JdbcTemplate jdbc,Clock clock,UUID order,String actor){transition(jdbc,clock,order,actor,true);}
 public static void release(JdbcTemplate jdbc,Clock clock,UUID order,String actor){transition(jdbc,clock,order,actor,false);}
 private static void transition(JdbcTemplate jdbc,Clock clock,UUID order,String actor,boolean consume){var rows=jdbc.query("SELECT id,coupon_id,state FROM commerce.coupon_redemption WHERE order_id=? FOR UPDATE",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3)},order);if(rows.isEmpty())return;if(rows.size()!=1)throw PromotionValues.rule();var row=rows.getFirst();if(!"RESERVED".equals(row[2]))return;UUID coupon=(UUID)row[1];jdbc.query("SELECT id FROM commerce.coupon WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),coupon);int changed;if(consume){changed=jdbc.update("UPDATE commerce.coupon SET reserved_uses=reserved_uses-1,consumed_uses=consumed_uses+1,version=version+1,updated_at=?,updated_by=? WHERE id=? AND reserved_uses>0 AND consumed_uses<maximum_uses",Timestamp.from(clock.instant()),actor,coupon);if(changed==1)jdbc.update("UPDATE commerce.coupon_redemption SET state='CONSUMED',version=version+1,updated_at=?,updated_by=? WHERE id=? AND state='RESERVED'",Timestamp.from(clock.instant()),actor,row[0]);}else{changed=jdbc.update("UPDATE commerce.coupon SET reserved_uses=reserved_uses-1,version=version+1,updated_at=?,updated_by=? WHERE id=? AND reserved_uses>0",Timestamp.from(clock.instant()),actor,coupon);if(changed==1)jdbc.update("UPDATE commerce.coupon_redemption SET state='RELEASED',version=version+1,updated_at=?,updated_by=? WHERE id=? AND state='RESERVED'",Timestamp.from(clock.instant()),actor,row[0]);}if(changed!=1)throw PromotionValues.rule();}
}
