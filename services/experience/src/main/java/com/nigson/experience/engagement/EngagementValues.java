package com.nigson.experience.engagement;

import com.nigson.shared.problem.ApiValues;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/** Closed request and response values for Experience engagement. */
public final class EngagementValues {
 private EngagementValues() {}
 public static final Set<String> KINDS=Set.of("PRODUCT","TESTIMONIAL");
 public static final Set<String> STATES=Set.of("PENDING","PUBLISHED","REJECTED","WITHDRAWN");
 public static final Set<String> DECISIONS=Set.of("PUBLISH","REJECT","WITHDRAW");
 public static final Set<String> SORTS=Set.of("createdAt,asc","createdAt,desc","publishedAt,desc");
 public static final Set<String> PUBLIC_SORTS=Set.of("publishedAt,desc","publishedAt,asc");
 private static final Pattern CODE=Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,79}$");
 public record ReviewInput(String kind,UUID productId,UUID orderLineId,Integer rating,String title,String body) {}
 public record Moderation(String decision,String reason) {}
 public record Review(UUID id,String state,Integer rating,boolean verifiedPurchase,long version) {}
 public record PublicReview(UUID id,String displayName,Integer rating,String title,String body,boolean verifiedPurchase,Instant publishedAt) {}
 public record AdminReview(UUID id,UUID productId,UUID orderLineId,String kind,String title,String body,Integer rating,String state,boolean verifiedPurchase,long version) {}
 public record Page<T>(List<T> items,int page,int size,long totalElements,int totalPages) {}
 public record CampaignInput(String code,String title,UUID marketId,UUID commercialRuleId,JsonNode displayConfig,Instant startsAt,Instant endsAt,boolean published) {}
 public record Campaign(UUID id,String code,String title,UUID marketId,UUID commercialRuleId,JsonNode displayConfig,Instant startsAt,Instant endsAt,boolean published,long version) {}
 public record PublicCampaign(UUID id,String code,String title,JsonNode displayConfig,Instant startsAt,Instant endsAt) {}
 public static ReviewInput review(JsonNode body){closed(body,Set.of("kind","body"),Set.of("productId","orderLineId","rating","title"));String kind=str(body,"kind",20),text=str(body,"body",1,5000);UUID product=uuid(body,"productId"),line=uuid(body,"orderLineId");Integer rating=null;if(body.has("rating")&&!body.get("rating").isNull()){if(!body.path("rating").isIntegralNumber()||!body.path("rating").canConvertToInt())throw ApiValues.invalid();rating=body.path("rating").asInt();}String title=body.has("title")&&!body.get("title").isNull()?str(body,"title",160):null;if(!KINDS.contains(kind)||text.strip().isEmpty()||control(text)||title!=null&&(title.strip().isEmpty()||control(title))||rating!=null&&(rating<1||rating>5)||kind.equals("PRODUCT")&&(product==null||line==null||rating==null)||kind.equals("TESTIMONIAL")&&(product!=null||line!=null))throw ApiValues.invalid();return new ReviewInput(kind,product,line,rating,title,text);}
 public static Moderation moderation(JsonNode body){closed(body,Set.of("decision","reason"),Set.of());String d=str(body,"decision",16),r=str(body,"reason",1,500);if(!DECISIONS.contains(d)||r.strip().length()<10||control(r))throw ApiValues.invalid();return new Moderation(d,r);}
 public static CampaignInput campaign(JsonNode b){closed(b,Set.of("code","title","marketId","displayConfig","startsAt","endsAt","published"),Set.of("commercialRuleId"));String code=str(b,"code",80),title=str(b,"title",180);UUID market=ApiValues.uuid(str(b,"marketId",36)),rule=uuid(b,"commercialRuleId");Instant starts=ApiValues.instant(str(b,"startsAt",40)),ends=ApiValues.instant(str(b,"endsAt",40));JsonNode config=b.get("displayConfig");if(!CODE.matcher(code).matches()||title.strip().isEmpty()||control(title)||config==null||!config.isObject()||config.size()>40||EventSize.bytes(config)>16384||!b.path("published").isBoolean()||!ends.isAfter(starts))throw ApiValues.invalid();validateDisplay(config);return new CampaignInput(code,title,market,rule,config.deepCopy(),starts,ends,b.path("published").asBoolean());}
 public static void query(Map<String,String> q,Set<String> allowed){if(!allowed.containsAll(q.keySet()))throw ApiValues.invalid();}
 public static void page(int page,int size){if(page<0||page>100000||size<1||size>100)throw ApiValues.invalid();}
 private static void validateDisplay(JsonNode config){var fields=Set.of("headline","subheadline","imageUrl","ctaLabel","ctaUrl","badge","theme");var it=config.propertyNames().iterator();while(it.hasNext()){String key=it.next();JsonNode v=config.get(key);if(!fields.contains(key)||!v.isString()||v.asString().length()>500||control(v.asString()))throw ApiValues.invalid();if(Set.of("imageUrl","ctaUrl").contains(key)&&!v.asString().isEmpty()&&(!v.asString().startsWith("/")||v.asString().startsWith("//")||v.asString().contains("\\")||v.asString().contains(":")||v.asString().contains("..")))throw ApiValues.invalid();}}
 private static boolean control(String s){return s.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT);}
 private static String str(JsonNode n,String k,int max){return str(n,k,1,max);}private static String str(JsonNode n,String k,int min,int max){JsonNode x=n.get(k);if(x==null||!x.isString())throw ApiValues.invalid();String s=x.asString();if(s.length()<min||s.length()>max||!s.equals(s.strip()))throw ApiValues.invalid();return s;}
 private static UUID uuid(JsonNode n,String k){return !n.has(k)||n.get(k).isNull()?null:ApiValues.uuid(str(n,k,36));}
 private static void closed(JsonNode n,Set<String> req,Set<String> optional){var all=new HashSet<>(req);all.addAll(optional);if(n==null||!n.isObject()||!n.propertyNames().containsAll(req)||!all.containsAll(n.propertyNames()))throw ApiValues.invalid();}
 private static final class EventSize {static int bytes(JsonNode n){return com.nigson.shared.events.EventCodec.JSON.writeValueAsBytes(n).length;}}
}
