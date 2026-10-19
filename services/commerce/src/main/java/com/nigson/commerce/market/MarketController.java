package com.nigson.commerce.market;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.observability.WireContext;
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class MarketController {
 private final MarketService service;public MarketController(MarketService service){this.service=service;}
 private ActorAssertion.Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return actor;}
 private Map<String,String> query(HttpServletRequest r,Set<String> allowed,boolean read)throws java.io.IOException{if(!allowed.containsAll(r.getParameterMap().keySet()))throw ApiValues.invalid();var values=new HashMap<String,String>();for(var entry:r.getParameterMap().entrySet()){if(entry.getValue().length!=1||entry.getValue()[0].length()>100)throw ApiValues.invalid();values.put(entry.getKey(),entry.getValue()[0]);}if(read&&r.getInputStream().read()!=-1)throw ApiValues.invalid();return values;}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
 @GetMapping(value="/api/v1/markets",produces="application/json")public ResponseEntity<List<MarketValues.PublicMarket>> list(HttpServletRequest r)throws java.io.IOException{query(r,Set.of(),true);return ResponseEntity.ok().header("Cache-Control","no-store").body(service.list());}
 @PostMapping(value="/api/v1/staff/markets",consumes="application/json",produces="application/json")public ResponseEntity<MarketValues.Market> create(HttpServletRequest r,@RequestBody JsonNode input)throws java.io.IOException{query(r,Set.of(),false);var value=service.create(actor(),MarketValues.create(input),header(r,"Idempotency-Key"),correlation());return ResponseEntity.status(201).header("Cache-Control","no-store").header("ETag","\""+value.version()+"\"").body(value);}
 @PutMapping(value="/api/v1/staff/markets/{id}",consumes="application/json",produces="application/json")public ResponseEntity<MarketValues.Market> update(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input)throws java.io.IOException{query(r,Set.of(),false);var value=service.update(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),MarketValues.update(input),header(r,"Idempotency-Key"),correlation());return ResponseEntity.ok().header("Cache-Control","no-store").header("ETag","\""+value.version()+"\"").body(value);}
 @PutMapping(value="/api/v1/staff/markets/{id}/retail-price-book",consumes="application/json",produces="application/json")public ResponseEntity<MarketValues.Market> book(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input)throws java.io.IOException{query(r,Set.of(),false);var value=service.book(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),MarketValues.book(input),header(r,"Idempotency-Key"),correlation());return ResponseEntity.ok().header("Cache-Control","no-store").header("ETag","\""+value.version()+"\"").body(value);}
 @PutMapping(value="/api/v1/staff/products/{id}/markets/{marketId}",consumes="application/json",produces="application/json")public ResponseEntity<MarketValues.Restriction> restriction(HttpServletRequest r,@PathVariable String id,@PathVariable String marketId,@RequestBody JsonNode input)throws java.io.IOException{query(r,Set.of(),false);var value=service.restriction(actor(),ApiValues.uuid(id),ApiValues.uuid(marketId),ApiValues.version(header(r,"If-Match")),MarketValues.eligibility(input),header(r,"Idempotency-Key"),correlation());return ResponseEntity.ok().header("Cache-Control","no-store").header("ETag","\""+value.version()+"\"").body(value);}
}
