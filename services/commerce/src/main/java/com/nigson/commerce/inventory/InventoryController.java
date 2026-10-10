package com.nigson.commerce.inventory;
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
public final class InventoryController {
 private final InventoryService service;public InventoryController(InventoryService service){this.service=service;}
 private ActorAssertion.Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return actor;}
 private Map<String,String> query(HttpServletRequest r,Set<String> allowed,boolean read)throws java.io.IOException{if(!allowed.containsAll(r.getParameterMap().keySet()))throw ApiValues.invalid();var values=new HashMap<String,String>();for(var entry:r.getParameterMap().entrySet()){if(entry.getValue().length!=1||entry.getValue()[0].length()>100)throw ApiValues.invalid();values.put(entry.getKey(),entry.getValue()[0]);}if(read&&r.getInputStream().read()!=-1)throw ApiValues.invalid();return values;}
 private int number(String value){try{if(!value.matches("0|[1-9][0-9]{0,9}"))throw ApiValues.invalid();return Integer.parseInt(value);}catch(NumberFormatException invalid){throw ApiValues.invalid();}}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
 @GetMapping(value="/api/v1/staff/inventory",produces="application/json")public ResponseEntity<InventoryValues.Page> list(HttpServletRequest r)throws java.io.IOException{var q=query(r,Set.of("skuId","locationId","page","size","sort"),true);var request=new InventoryValues.Query(q.containsKey("skuId")?ApiValues.uuid(q.get("skuId")):null,q.containsKey("locationId")?ApiValues.uuid(q.get("locationId")):null,number(q.getOrDefault("page","0")),number(q.getOrDefault("size","20")),q.getOrDefault("sort","skuId,asc"));return ResponseEntity.ok().header("Cache-Control","no-store").body(service.list(actor(),request));}
 @PostMapping(value="/api/v1/staff/stock-locations",consumes="application/json",produces="application/json")public ResponseEntity<InventoryValues.Location> create(HttpServletRequest r,@RequestBody JsonNode input)throws java.io.IOException{query(r,Set.of(),false);var value=service.create(actor(),InventoryValues.location(input),header(r,"Idempotency-Key"),correlation());return ResponseEntity.status(201).header("Cache-Control","no-store").header("ETag","\""+value.version()+"\"").body(value);}
 @PostMapping(value="/api/v1/staff/stock-adjustments",consumes="application/json",produces="application/json")public ResponseEntity<InventoryValues.Stock> adjust(HttpServletRequest r,@RequestBody JsonNode input)throws java.io.IOException{query(r,Set.of(),false);var value=service.adjust(actor(),InventoryValues.adjustment(input),ApiValues.version(header(r,"If-Match")),header(r,"Idempotency-Key"),correlation());return ResponseEntity.status(201).header("Cache-Control","no-store").header("ETag","\""+value.version()+"\"").body(value);}
}
