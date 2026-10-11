package com.nigson.commerce.payment;

import java.util.*;
import java.security.MessageDigest;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;

/** Authentication precedes parsing. Callback data never substitutes for provider verification. */
public final class PaystackWebhookProtocol {
 @FunctionalInterface public interface SignatureVerifier{boolean authentic(byte[] raw,String signature);}
 public record Event(String type,String eventKey,String bodyHash,JsonNode data,boolean recognized,boolean matchingMode){}
 private static final tools.jackson.databind.json.JsonMapper JSON=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(33).maxStringLength(262144).maxNumberLength(120).build()).enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private static final Set<String> EVENTS=Set.of("charge.success","refund.pending","refund.processing","refund.processed","refund.failed");
 private final SignatureVerifier verifier;private final java.util.function.Function<byte[],String> keyedDigest;private final String mode;
 public PaystackWebhookProtocol(SignatureVerifier verifier,java.util.function.Function<byte[],String> keyedDigest,String mode){this.verifier=Objects.requireNonNull(verifier);this.keyedDigest=Objects.requireNonNull(keyedDigest);if(!Set.of("MOCK","TEST","LIVE").contains(mode))throw new IllegalArgumentException("PAYMENT_PROVIDER_MODE_REQUIRED");this.mode=mode;}
 private static void shape(JsonNode node){if(node==null)throw ApiValues.invalid();record Item(JsonNode value,int depth){}var pending=new ArrayDeque<Item>();pending.push(new Item(node,0));while(!pending.isEmpty()){var item=pending.pop();var value=item.value();if(item.depth()>32||value.isArray()&&value.size()>100||value.isObject()&&value.size()>100)throw ApiValues.invalid();if(value.isContainer())for(var child:value)pending.push(new Item(child,item.depth()+1));}}
 private static Object canonical(JsonNode node){if(node.isObject()){var sorted=new TreeMap<String,Object>();node.properties().forEach(e->sorted.put(e.getKey(),canonical(e.getValue())));return sorted;}if(node.isArray()){var array=new ArrayList<Object>();node.forEach(n->array.add(canonical(n)));return array;}if(node.isNumber())return node.decimalValue().stripTrailingZeros();if(node.isBoolean())return node.asBoolean();return node.isNull()?null:node.asString();}
 public Event authenticate(byte[] raw,String signature){if(raw==null||raw.length==0)throw ApiValues.invalid();if(raw.length>262144)throw new Problem.Failure(ProblemCode.BODY_TOO_LARGE);if(!verifier.authentic(raw,signature))throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
  return stored(raw);
 }
 /** Only receipt processing may decode bytes already authenticated by the owned cipher. */
 Event stored(byte[] raw){if(raw==null||raw.length==0||raw.length>262144)throw ApiValues.invalid();
  try{var body=JSON.readTree(raw);shape(body);if(body==null||!body.isObject()||!body.path("event").isString()||!body.path("event").asString().matches("[a-z][a-z0-9._]{0,79}")||!body.path("data").isObject())throw ApiValues.invalid();String type=body.path("event").asString(),hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));JsonNode data=body.path("data");var id=data.get("id");String stable=id!=null&&(id.isString()||id.isIntegralNumber())?id.isString()?id.asString():id.toString():null;String identity=stable!=null&&stable.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")?EventCodec.hash(EventCodec.JSON.valueToTree(Map.of("event",type,"providerObjectId",stable))):keyedDigest.apply(JSON.writeValueAsBytes(canonical(body)));if(identity==null||!identity.matches("[a-f0-9]{64}"))throw ApiValues.invalid();String key=type+":"+identity;boolean matching=!data.has("domain")||data.path("domain").isString()&&data.path("domain").asString().equals(mode.equals("LIVE")?"live":"test");return new Event(type,key,hash,data.deepCopy(),EVENTS.contains(type),matching);
  }catch(Problem.Failure known){throw known;}catch(Exception invalid){throw ApiValues.invalid();}
 }
}
