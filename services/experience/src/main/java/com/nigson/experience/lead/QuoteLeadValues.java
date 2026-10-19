package com.nigson.experience.lead;

import com.nigson.shared.problem.ApiValues;
import java.math.BigDecimal;
import java.util.*;
import tools.jackson.databind.JsonNode;

public final class QuoteLeadValues {
 private QuoteLeadValues(){}
 public record Product(UUID skuId, BigDecimal quantity){}
 public record Contact(String name,String email,String phone){}
 public record Request(UUID marketId,UUID organizationId,List<Product> products,String subject,String message,Contact contact){ public Request{products=List.copyOf(products);} }
 public record Created(UUID leadId,String state){}
 public record EligibleSku(UUID skuId,int quantityScale){}
 public static Request parse(JsonNode n){
  exact(n,Set.of("marketId","products","subject","message","contact"),Set.of("organizationId"));
  UUID market=uuid(n,"marketId"),org=uuidOptional(n,"organizationId");JsonNode items=n.path("products");if(!items.isArray()||items.isEmpty()||items.size()>50)throw ApiValues.invalid();
  var products=new ArrayList<Product>();var seen=new HashSet<UUID>();for(JsonNode item:items){exact(item,Set.of("skuId","quantity"),Set.of());UUID sku=uuid(item,"skuId");if(!seen.add(sku)||!item.path("quantity").isString())throw ApiValues.invalid();BigDecimal quantity=ApiValues.quantity(item.path("quantity").asString());if(quantity.precision()>20||quantity.scale()>3)throw ApiValues.invalid();products.add(new Product(sku,quantity));}
  String subject=text(n,"subject",200),message=text(n,"message",10000);if(subject.strip().isEmpty()||message.strip().isEmpty())throw ApiValues.invalid();JsonNode c=n.path("contact");exact(c,Set.of("name","email"),Set.of("phone"));String name=text(c,"name",160),email=text(c,"email",254),phone=c.has("phone")?text(c,"phone",32):null;if(name.strip().isEmpty()||!email.matches("(?i)^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$")||phone!=null&&phone.isBlank())throw ApiValues.invalid();return new Request(market,org,products,subject,message,new Contact(name,email,phone));
 }
 private static UUID uuid(JsonNode n,String k){if(!n.path(k).isString())throw ApiValues.invalid();return ApiValues.uuid(n.path(k).asString());}
 private static UUID uuidOptional(JsonNode n,String k){return !n.has(k)||n.path(k).isNull()?null:uuid(n,k);}
 private static String text(JsonNode n,String k,int max){if(!n.path(k).isString())throw ApiValues.invalid();String s=n.path(k).asString();if(s.isBlank()||s.length()>max||s.chars().anyMatch(Character::isISOControl))throw ApiValues.invalid();return s;}
 private static void exact(JsonNode n,Set<String> required,Set<String> optional){if(n==null||!n.isObject()||!n.propertyNames().containsAll(required))throw ApiValues.invalid();for(String key:n.propertyNames())if(!required.contains(key)&&!optional.contains(key))throw ApiValues.invalid();}
}
