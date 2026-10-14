package com.nigson.commerce.fulfillment;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.problem.*;
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class ShipmentTrackingController {
 private final ShipmentTrackingService service;
 public ShipmentTrackingController(ShipmentTrackingService service){this.service=service;}
 @GetMapping(value="/api/v1/orders/{id}/tracking",produces="application/json")
 public ResponseEntity<ShipmentTrackingService.Tracking> tracking(HttpServletRequest request,@PathVariable String id)throws java.io.IOException{if(!request.getParameterMap().isEmpty()||request.getInputStream().read()!=-1)throw ApiValues.invalid();var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return ResponseEntity.ok().header("Cache-Control","no-store").body(service.tracking(actor,ApiValues.uuid(id)));}
}
