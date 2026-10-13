package com.nigson.commerce.fulfillment;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.nigson.shared.security.PrivateWorkload;
import com.nigson.shared.problem.*;
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class CarrierContextController {
 private final CarrierContextService service;
 public CarrierContextController(CarrierContextService service){this.service=service;}
 @GetMapping(value="/internal/v1/shipments/{id}/provider-context",produces="application/json")
 public ResponseEntity<CarrierShipmentContext> context(HttpServletRequest request,@PathVariable String id)throws java.io.IOException{
  if(!request.getParameterMap().isEmpty()||request.getInputStream().read()!=-1)throw ApiValues.invalid();var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
  return ResponseEntity.ok().header("Cache-Control","no-store").body(service.context(proof,ApiValues.uuid(id)));
 }
}
