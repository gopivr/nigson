package com.nigson.commerce.affiliate;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import tools.jackson.databind.JsonNode;
@RestController @ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true") @ConditionalOnExpression("${NIGSON_PRODUCT_PHASE:1} >= 3")
public final class AffiliatePayoutController {
 private final AffiliatePayouts service;public AffiliatePayoutController(AffiliatePayouts service){this.service=service;}
 private ActorAssertion.Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);return actor;}
 private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
 private String key(HttpServletRequest request){if(!request.getParameterMap().isEmpty())throw ApiValues.invalid();var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw ApiValues.invalid();return keys.getFirst();}
 private int page(HttpServletRequest request)throws java.io.IOException{if(!Set.of("page").containsAll(request.getParameterMap().keySet())||request.getInputStream().read()!=-1)throw ApiValues.invalid();var values=request.getParameterValues("page");if(values==null)return 0;if(values.length!=1||!values[0].matches("0|[1-9][0-9]{0,4}"))throw ApiValues.invalid();return Integer.parseInt(values[0]);}
 private <T> ResponseEntity<T> response(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
 @GetMapping("/api/v1/me/affiliate/payouts")public ResponseEntity<AffiliatePayouts.Overview> own(HttpServletRequest request)throws java.io.IOException{return response(service.own(actor(),page(request)));}
 @PutMapping("/api/v1/me/affiliate/bank-details")public ResponseEntity<AffiliatePayouts.Overview> save(HttpServletRequest request,@RequestBody JsonNode body){return response(service.saveBank(actor(),body,key(request),correlation()));}
 @GetMapping("/api/v1/staff/affiliates")public ResponseEntity<AffiliatePayouts.Page> list(HttpServletRequest request)throws java.io.IOException{return response(service.list(actor(),page(request)));}
 @GetMapping("/api/v1/staff/affiliates/{id}/payouts")public ResponseEntity<AffiliatePayouts.Overview> detail(HttpServletRequest request,@PathVariable String id)throws java.io.IOException{return response(service.detail(actor(),ApiValues.uuid(id),page(request),correlation()));}
 @PostMapping("/api/v1/staff/affiliates/{id}/bank-review")public ResponseEntity<Map<String,Boolean>> review(HttpServletRequest request,@PathVariable String id,@RequestBody JsonNode body){service.review(actor(),ApiValues.uuid(id),body,key(request),correlation());return response(Map.of("saved",true));}
 @PostMapping("/api/v1/staff/affiliates/{id}/payouts")public ResponseEntity<AffiliatePayouts.Payout> prepare(HttpServletRequest request,@PathVariable String id,@RequestBody JsonNode body){return response(service.prepare(actor(),ApiValues.uuid(id),body,key(request),correlation()));}
 @PostMapping("/api/v1/staff/affiliate-payouts/{id}/paid")public ResponseEntity<AffiliatePayouts.Payout> paid(HttpServletRequest request,@PathVariable String id,@RequestBody JsonNode body){return response(service.resolve(actor(),ApiValues.uuid(id),true,body,key(request),correlation()));}
 @PostMapping("/api/v1/staff/affiliate-payouts/{id}/cancel")public ResponseEntity<AffiliatePayouts.Payout> cancel(HttpServletRequest request,@PathVariable String id,@RequestBody JsonNode body){return response(service.resolve(actor(),ApiValues.uuid(id),false,body,key(request),correlation()));}
}
