package com.nigson.commerce.pricing;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
/** BP1 has no promotion rules. Higher phases fail closed until their owning calculation port exists. */
public final class Bp1PriceAdjustments implements PriceAdjustments {
 private final JdbcTemplate jdbc;private final int phase;public Bp1PriceAdjustments(JdbcTemplate jdbc,int phase){this.jdbc=jdbc;this.phase=phase;}
 public Result quote(UUID party,UUID organization,UUID market,String coupon,Map<UUID,BigDecimal> amounts,Map<UUID,BigDecimal> quantities,BigDecimal delivery,Instant now,Instant expires){if(coupon!=null)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);if(phase>=2&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.promotion_rule WHERE market_id=? AND enabled AND starts_at<=? AND ends_at>?)",Boolean.class,market,java.sql.Timestamp.from(now),java.sql.Timestamp.from(now))))throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);var discounts=new LinkedHashMap<UUID,BigDecimal>();for(UUID id:amounts.keySet())discounts.put(id,BigDecimal.ZERO.setScale(2));return new Result(Collections.unmodifiableMap(discounts),BigDecimal.ZERO.setScale(2),EventCodec.JSON.createArrayNode(),expires);}
}
