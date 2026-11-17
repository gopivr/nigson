package com.nigson.commerce.wishlist;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.observability.WireContext;
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class WishlistController {
 private final WishlistService service;
 public WishlistController(WishlistService service){this.service=service;}
 private ActorAssertion.Actor actor(HttpServletRequest r)throws java.io.IOException{
  var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
  if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
  if(!r.getParameterMap().isEmpty()||r.getInputStream().read()!=-1)throw ApiValues.invalid();return actor;
 }
 private String key(HttpServletRequest r){var values=Collections.list(r.getHeaders("Idempotency-Key"));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
 @GetMapping(value="/api/v1/me/wishlist",produces="application/json") public ResponseEntity<List<WishlistValues.Item>> list(HttpServletRequest r)throws java.io.IOException{return ResponseEntity.ok().header("Cache-Control","no-store").body(service.list(actor(r)));}
 @PutMapping("/api/v1/me/wishlist/items/{skuId}") public ResponseEntity<Void> save(HttpServletRequest r,@PathVariable String skuId)throws java.io.IOException{service.set(actor(r),ApiValues.uuid(skuId),true,key(r),correlation());return ResponseEntity.noContent().header("Cache-Control","no-store").build();}
 @DeleteMapping("/api/v1/me/wishlist/items/{skuId}") public ResponseEntity<Void> remove(HttpServletRequest r,@PathVariable String skuId)throws java.io.IOException{service.set(actor(r),ApiValues.uuid(skuId),false,key(r),correlation());return ResponseEntity.noContent().header("Cache-Control","no-store").build();}
}
