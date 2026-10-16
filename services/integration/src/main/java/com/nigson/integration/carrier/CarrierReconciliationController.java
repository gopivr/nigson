package com.nigson.integration.carrier;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;
import com.nigson.shared.observability.WireContext;
@RestController
@ConditionalOnProperty(name={"NIGSON_SECURITY_ENABLED","NIGSON_CARRIER_ENABLED","NIGSON_CARRIER_RECONCILIATION_ENABLED"},havingValue="true")
public final class CarrierReconciliationController {
 private final CarrierReconciliationService service;public CarrierReconciliationController(CarrierReconciliationService service){this.service=service;}
 @PostMapping(value="/internal/v1/carrier/shipments/{id}/reconciliation",consumes="application/json",produces="application/json")public ResponseEntity<CarrierReconciliationService.Result> reconcile(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body){if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var keys=Collections.list(r.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw ApiValues.invalid();UUID correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;return ResponseEntity.accepted().header("Cache-Control","no-store").body(service.reconcile(proof,ApiValues.uuid(id),CarrierReconciliationService.request(body),ApiValues.uuid(keys.getFirst()),correlation));}
}
