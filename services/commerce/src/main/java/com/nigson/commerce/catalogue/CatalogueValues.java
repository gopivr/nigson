package com.nigson.commerce.catalogue;
import java.util.*;import java.math.BigDecimal;import java.text.Normalizer;
import tools.jackson.databind.JsonNode;import com.nigson.shared.problem.ApiValues;
/** Public DTOs and exact, bounded command values. No client-supplied publication or ownership. */
public final class CatalogueValues {
 private CatalogueValues(){}
 public record Reference(UUID id,String name){}
 public record Sku(UUID id,String code,String variantName,String unit,String packSize,boolean available){}
 public record Asset(UUID id,String purpose,String url){}
 public record AdminAsset(UUID id,String state,String purpose,String mimeType,long size,String checksum,String url,long version){}
 public record Product(UUID id,String name,String slug,String description,JsonNode specifications,Reference brand,List<Reference> categories,List<Sku> skus,List<Asset> assets,long revision){}
 public record ProductAdmin(UUID id,String name,String slug,String description,JsonNode specifications,Reference brand,List<Reference> categories,List<Sku> skus,List<AdminAsset> assets,long revision,String status,long version){}
 public record Summary(UUID id,String name,String slug,String brandName,String imageUrl,boolean available,long revision){}
 public record Page<T>(List<T> items,int page,int size,long totalElements,long totalPages){}
 public record Taxonomy(UUID id,String name,String slug,UUID parentId,boolean active,long version){}
 public record PublicCategory(UUID id,UUID parentId,String name,String slug,int sortOrder){}
 public record PublicBrand(UUID id,String name,String slug){}
 public record SkuAdmin(UUID id,String code,String variantName,String unit,int quantityScale,String packSize,long version){}
 public static final Set<String> PRODUCT_FIELDS=Set.of("name","slug","description","brandId","divisionCode","specifications","categoryIds");
 public record ProductInput(String name,String slug,String description,UUID brandId,String divisionCode,JsonNode specifications,List<UUID> categoryIds){}
 public static JsonNode object(JsonNode node,Set<String> keys,boolean allowEmpty){if(node==null||!node.isObject()||!allowEmpty&&node.isEmpty()||!keys.containsAll(node.properties().stream().map(Map.Entry::getKey).toList()))throw ApiValues.invalid();return node;}
 public static String text(String value,int max,boolean empty){if(value==null||value.length()>max||!empty&&value.isBlank()||value.codePoints().anyMatch(Character::isISOControl))throw ApiValues.invalid();String result=Normalizer.normalize(value.strip(),Normalizer.Form.NFC);if(result.length()>max)throw ApiValues.invalid();return result;}
 public static String description(JsonNode n){var v=n.get("description");if(v==null||!v.isString())throw ApiValues.invalid();String s=v.asString();if(s.length()>20000||s.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw ApiValues.invalid();String normalized=Normalizer.normalize(s,Normalizer.Form.NFC);if(normalized.length()>20000)throw ApiValues.invalid();return normalized;}
 public static String string(JsonNode n,String key,int max,boolean nullable,boolean empty){var v=n.get(key);if(v==null||v.isNull()){if(nullable)return null;throw ApiValues.invalid();}if(!v.isString())throw ApiValues.invalid();return text(v.asString(),max,empty);}
 public static UUID uuid(JsonNode n,String key,boolean nullable){var v=n.get(key);if(v==null||v.isNull()){if(nullable)return null;throw ApiValues.invalid();}if(!v.isString())throw ApiValues.invalid();return ApiValues.uuid(v.asString());}
 public static String slug(String value,int maximum){text(value,maximum,false);if(!value.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))throw ApiValues.invalid();return value;}
 public static boolean bool(JsonNode n,String key,boolean fallback){var v=n.get(key);if(v==null)return fallback;if(!v.isBoolean())throw ApiValues.invalid();return v.asBoolean();}
 public static int integer(JsonNode n,String key,int fallback){var v=n.get(key);if(v==null)return fallback;if(!v.isIntegralNumber()||!v.canConvertToInt())throw ApiValues.invalid();return v.asInt();}
 public static List<UUID> ids(JsonNode n,String key){var a=n.get(key);if(a==null||!a.isArray()||a.size()>100)throw ApiValues.invalid();var values=new ArrayList<UUID>();for(var item:a){if(!item.isString())throw ApiValues.invalid();var id=ApiValues.uuid(item.asString());if(values.contains(id))throw ApiValues.invalid();values.add(id);}values.sort(Comparator.naturalOrder());return List.copyOf(values);}
 public static ProductInput product(JsonNode n,ProductInput before){object(n,PRODUCT_FIELDS,false);var merged=(tools.jackson.databind.node.ObjectNode)n.deepCopy();if(before!=null){var old=com.nigson.shared.events.EventCodec.JSON.valueToTree(before);for(String field:PRODUCT_FIELDS)if(!merged.has(field))merged.set(field,old.get(field));}
  String name=string(merged,"name",200,false,false),slug=slug(string(merged,"slug",220,false,false),220),description=description(merged);var specifications=merged.get("specifications");specifications(specifications);return new ProductInput(name,slug,description,uuid(merged,"brandId",true),string(merged,"divisionCode",40,true,false),specifications.deepCopy(),ids(merged,"categoryIds"));
 }
 public static void specifications(JsonNode n){if(n==null||!n.isObject()||n.size()>50||com.nigson.shared.events.EventCodec.JSON.writeValueAsBytes(n).length>65536)throw ApiValues.invalid();for(var e:n.properties()){if(!e.getKey().matches("[a-z][a-z0-9_]{0,79}"))throw ApiValues.invalid();var value=e.getValue();if(value.isArray()){if(value.size()>20)throw ApiValues.invalid();for(var item:value)primitive(item);}else primitive(value);}}
 private static void primitive(JsonNode v){if(v.isBoolean())return;if(!v.isString())throw ApiValues.invalid();String s=text(v.asString(),1000,true);if(s.indexOf('<')>=0||s.indexOf('>')>=0||s.matches("(?is).*\\b(?:javascript|data|vbscript):.*"))throw ApiValues.invalid();}
 public static String quantity(JsonNode n,String key,int scale){String text=string(n,key,30,false,false);BigDecimal value=ApiValues.quantity(text);if(value.stripTrailingZeros().scale()>scale)throw ApiValues.invalid();return value.setScale(scale).toPlainString();}
}
