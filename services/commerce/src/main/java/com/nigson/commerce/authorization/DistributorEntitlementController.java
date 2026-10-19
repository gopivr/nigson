package com.nigson.commerce.authorization;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class DistributorEntitlementController {
 private final DistributorEntitlementService service;
 public DistributorEntitlementController(DistributorEntitlementService service){this.service=service;}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 private void query(HttpServletRequest r){if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();}
 @PostMapping(value="/internal/v1/distributor-entitlements",consumes="application/json",produces="application/json")
 public ResponseEntity<EntitlementValues.Acknowledgement> decide(HttpServletRequest request,@RequestBody JsonNode body)throws java.io.IOException{
  query(request);var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
  var context=com.nigson.shared.observability.WireContext.current();UUID correlation=context==null?com.nigson.shared.observability.WireContext.correlation(request.getHeader("X-Correlation-ID")):context.correlationId;
  var ack=service.execute(proof,EntitlementValues.parse(body),ApiValues.version(header(request,"If-Match")),header(request,"Idempotency-Key"),correlation);
  return ResponseEntity.ok().eTag(Long.toString(ack.revision())).header("Cache-Control","no-store").body(ack);
 }
}
