package com.nigson.commerce.notification;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import tools.jackson.databind.JsonNode;
import com.nigson.commerce.security.SessionCookies;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.observability.WireContext;
@RestController
@ConditionalOnProperty(name="NIGSON_PHONE_CONSENT_ENABLED",havingValue="true")
public final class PhoneController {
 private final PhoneRelay relay;private final SessionCookies cookies;public PhoneController(PhoneRelay relay,SessionCookies cookies){this.relay=relay;this.cookies=cookies;}
 private ActorAssertion.Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);return actor;}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 private ResponseEntity<JsonNode> execute(HttpServletRequest r,String api,UUID id,JsonNode body){if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();boolean read=api.equals("API-203");String key=read?null:header(r,"Idempotency-Key");UUID correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;var result=relay.execute(actor(),cookies.read(r),api,id,body,key,correlation);var reply=ResponseEntity.status(result.status()).header("Cache-Control","no-store");if(!read&&result.body().has("version"))reply.eTag(Long.toString(result.body().path("version").asLong()));return result.status()==204?reply.build():reply.body(result.body());}
 @PostMapping(value="/api/v1/me/communication-destinations",consumes="application/json",produces="application/json")public ResponseEntity<JsonNode> create(HttpServletRequest r,@RequestBody JsonNode body){if(body==null||!body.isObject()||!body.propertyNames().equals(Set.of("channel","address","policyVersion","consentedPurposes")))throw ApiValues.invalid();return execute(r,"API-201",null,body);}
 @PostMapping(value="/api/v1/me/communication-destinations/{id}/verification",consumes="application/json",produces="application/json")public ResponseEntity<JsonNode> confirm(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body){if(body==null||!body.isObject()||!body.propertyNames().equals(Set.of("code"))||!body.path("code").isString()||!body.path("code").asString().matches("[0-9]{6}"))throw ApiValues.invalid();var privateBody=((tools.jackson.databind.node.ObjectNode)body).deepCopy();privateBody.put("version",ApiValues.version(header(r,"If-Match")));return execute(r,"API-202",ApiValues.uuid(id),privateBody);}
 @GetMapping(value="/api/v1/me/communication-destinations",produces="application/json")public ResponseEntity<JsonNode> list(HttpServletRequest r){if(r.getContentLengthLong()>0||hasBody(r))throw ApiValues.invalid();return execute(r,"API-203",null,null);}
 @DeleteMapping(value="/api/v1/me/communication-destinations/{id}")public ResponseEntity<JsonNode> withdraw(HttpServletRequest r,@PathVariable String id){if(r.getContentLengthLong()>0||hasBody(r))throw ApiValues.invalid();return execute(r,"API-204",ApiValues.uuid(id),EventCodec.JSON.createObjectNode().put("version",ApiValues.version(header(r,"If-Match"))));}
 private boolean hasBody(HttpServletRequest r){try{return r.getInputStream().readNBytes(1).length>0;}catch(java.io.IOException invalid){throw ApiValues.invalid();}}
}
