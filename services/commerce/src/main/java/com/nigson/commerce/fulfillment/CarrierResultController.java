package com.nigson.commerce.fulfillment;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import com.nigson.shared.security.PrivateWorkload;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class CarrierResultController {
 private final ObjectProvider<CarrierResultService> service;
 public CarrierResultController(ObjectProvider<CarrierResultService> service){this.service=service;}
 @PostMapping(value="/internal/v1/shipments/{id}/provider-result",consumes="application/json",produces="application/json")
 public ResponseEntity<FulfillmentValues.Shipment> result(HttpServletRequest request,@PathVariable String id,@RequestBody tools.jackson.databind.JsonNode input){if(!request.getParameterMap().isEmpty())throw ApiValues.invalid();var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var active=service.getIfAvailable();if(active==null)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);var result=active.accept(proof,ApiValues.uuid(id),CarrierResultValues.result(input),WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId);return ResponseEntity.ok().header("Cache-Control","no-store").body(result);}
}
