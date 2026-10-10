package com.nigson.commerce.inventory;
import java.util.*;
import java.math.*;
import java.text.Normalizer;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
/** Closed staff wire values. Quantities are decimal strings, never floating-point JSON. */
public final class InventoryValues {
 private InventoryValues(){}
 public static final BigDecimal MAX=new BigDecimal("99999999999999999.999");
 public record Stock(UUID skuId,UUID locationId,String onHand,String reserved,String allocated,String available,long version){}
 public record Location(UUID id,String code,String name,boolean active,long version){}
 public record NewLocation(String code,String name){}
 public record Adjustment(UUID skuId,UUID locationId,String delta,String reason){}
 public record Query(UUID skuId,UUID locationId,int page,int size,String sort){public Query{ApiValues.page(page,size);ApiValues.sort(sort,Set.of("skuId,asc","skuId,desc","locationId,asc","locationId,desc","available,asc","available,desc"));}}
 public record Page(List<Stock> items,int page,int size,long totalElements,long totalPages){}
 public static void closed(JsonNode input,Set<String> names){if(input==null||!input.isObject()||input.size()!=names.size()||!names.containsAll(input.propertyNames())||EventCodec.JSON.writeValueAsBytes(input).length>4096)throw ApiValues.invalid();}
 public static String text(JsonNode input,String name,int min,int max){var n=input.get(name);if(n==null||!n.isString())throw ApiValues.invalid();String raw=n.asString();if(raw.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();String value=Normalizer.normalize(raw,Normalizer.Form.NFC).strip();if(value.length()<min||value.length()>max||value.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();return value;}
 public static NewLocation location(JsonNode input){closed(input,Set.of("code","name"));String code=text(input,"code",1,40);if(!code.matches("[A-Z0-9][A-Z0-9_-]{0,39}"))throw ApiValues.invalid();return new NewLocation(code,text(input,"name",1,120));}
 public static Adjustment adjustment(JsonNode input){closed(input,Set.of("skuId","locationId","delta","reason"));var n=input.get("delta");if(n==null||!n.isString())throw ApiValues.invalid();String delta=n.asString();quantity(delta,3,true);return new Adjustment(ApiValues.uuid(text(input,"skuId",36,36)),ApiValues.uuid(text(input,"locationId",36,36)),delta,text(input,"reason",10,500));}
 public static BigDecimal quantity(String value,int scale,boolean signed){if(scale<0||scale>3||value==null||!value.matches((signed?"-?":"")+"(0|[1-9][0-9]{0,16})(\\.[0-9]{1,3})?"))throw ApiValues.invalid();var decimal=new BigDecimal(value);if(decimal.signum()==0||decimal.stripTrailingZeros().scale()>scale)throw ApiValues.invalid();return decimal.setScale(scale);}
 public static String wire(BigDecimal value,int scale){try{return value.setScale(scale,RoundingMode.UNNECESSARY).toPlainString();}catch(ArithmeticException invalid){throw rule();}}
 public static Problem.Failure rule(){return new Problem.Failure(ProblemCode.INVENTORY_RULE_VIOLATION);}
}
