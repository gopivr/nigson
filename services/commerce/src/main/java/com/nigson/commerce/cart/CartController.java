package com.nigson.commerce.cart;
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
public final class CartController {
 private final CartService service;public CartController(CartService service){this.service=service;}
 private ActorAssertion.Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return actor;}
 private void query(HttpServletRequest r,boolean emptyBody)throws java.io.IOException{if(!r.getParameterMap().isEmpty()||emptyBody&&r.getInputStream().read()!=-1)throw ApiValues.invalid();}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
 private ResponseEntity<CartValues.Cart> response(CartValues.Cart c,int status){return ResponseEntity.status(status).header("Cache-Control","no-store").header("ETag","\""+c.version()+"\"").body(c);}
 @PostMapping(value="/api/v1/carts",consumes="application/json",produces="application/json")public ResponseEntity<CartValues.Cart> create(HttpServletRequest r,@RequestBody JsonNode input)throws java.io.IOException{query(r,false);return response(service.create(actor(),CartValues.create(input),header(r,"Idempotency-Key"),correlation()),201);}
 @GetMapping(value="/api/v1/carts/{id}",produces="application/json")public ResponseEntity<CartValues.Cart> get(HttpServletRequest r,@PathVariable String id)throws java.io.IOException{query(r,true);return response(service.get(actor(),ApiValues.uuid(id)),200);}
 @PutMapping(value="/api/v1/carts/{id}/lines/{skuId}",consumes="application/json",produces="application/json")public ResponseEntity<CartValues.Cart> set(HttpServletRequest r,@PathVariable String id,@PathVariable String skuId,@RequestBody JsonNode input)throws java.io.IOException{query(r,false);return response(service.set(actor(),ApiValues.uuid(id),ApiValues.uuid(skuId),ApiValues.version(header(r,"If-Match")),CartValues.quantity(input),header(r,"Idempotency-Key"),correlation()),200);}
 @DeleteMapping(value="/api/v1/carts/{id}/lines/{skuId}")public ResponseEntity<Void> remove(HttpServletRequest r,@PathVariable String id,@PathVariable String skuId)throws java.io.IOException{query(r,true);var removed=service.remove(actor(),ApiValues.uuid(id),ApiValues.uuid(skuId),ApiValues.version(header(r,"If-Match")),header(r,"Idempotency-Key"),correlation());return ResponseEntity.noContent().header("Cache-Control","no-store").header("ETag","\""+removed.version()+"\"").build();}
}
