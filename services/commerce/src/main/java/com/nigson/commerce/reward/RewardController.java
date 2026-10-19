package com.nigson.commerce.reward;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import tools.jackson.databind.JsonNode;
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class RewardController {
 private final RewardService service;public RewardController(RewardService service){this.service=service;}
 private ActorAssertion.Actor actor(HttpServletRequest r){var a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!a.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(a.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();return actor;}
 private void noBody(HttpServletRequest r)throws java.io.IOException{if(r.getInputStream().read()!=-1)throw ApiValues.invalid();}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
 @GetMapping(value="/api/v1/me/rewards",produces="application/json")public RewardValues.Ledger ledger(HttpServletRequest r)throws java.io.IOException{var actor=actor(r);noBody(r);return service.ledger(actor);}
 @GetMapping(value="/api/v1/me/referral-code",produces="application/json")public RewardValues.Referral referral(HttpServletRequest r)throws java.io.IOException{var actor=actor(r);noBody(r);return service.referral(actor);}
 @PostMapping(value="/api/v1/me/referral-code",consumes="application/json",produces="application/json")public ResponseEntity<RewardValues.Referral> create(HttpServletRequest r,@RequestBody JsonNode body){var value=service.createReferral(actor(r),body,header(r,"Idempotency-Key"),correlation());return ResponseEntity.status(201).header("Cache-Control","no-store").body(value);}
 @PostMapping(value="/api/v1/me/referral-attribution",consumes="application/json",produces="application/json")public ResponseEntity<RewardValues.Attribution> attribute(HttpServletRequest r,@RequestBody JsonNode body){var value=service.attribute(actor(r),body,header(r,"Idempotency-Key"),correlation());return ResponseEntity.status(201).header("Cache-Control","no-store").body(value);}
 @PostMapping(value="/api/v1/staff/organizations/{id}/dealer-rewards",consumes="application/json",produces="application/json")public ResponseEntity<RewardValues.DealerReward> issue(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body){var value=service.issueDealer(actor(r),ApiValues.uuid(id),body,header(r,"Idempotency-Key"),correlation());return ResponseEntity.status(201).header("Cache-Control","no-store").body(value);}

}
