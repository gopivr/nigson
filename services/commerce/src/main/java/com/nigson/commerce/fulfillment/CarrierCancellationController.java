package com.nigson.commerce.fulfillment;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;
import com.nigson.shared.observability.WireContext;
@RestController
@ConditionalOnProperty(name={"NIGSON_SECURITY_ENABLED","NIGSON_CARRIER_CANCELLATION_ENABLED"},havingValue="true")
public final class CarrierCancellationController {
 private final CarrierCancellationService service;public CarrierCancellationController(CarrierCancellationService service){this.service=service;}
 private Object principal(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);return auth.getPrincipal();}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
 @PostMapping(value="/api/v1/staff/shipments/{id}/carrier-cancellation",consumes="application/json",produces="application/json")public ResponseEntity<CarrierCancellationValues.View> request(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body){if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();if(!(principal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return ResponseEntity.accepted().header("Cache-Control","no-store").body(service.request(actor,ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),CarrierReconciliationValues.reason(body),header(r,"Idempotency-Key"),correlation()));}
 @GetMapping(value="/api/v1/staff/shipments/{id}/carrier-cancellation",produces="application/json")public ResponseEntity<CarrierCancellationValues.View> read(HttpServletRequest r,@PathVariable String id)throws java.io.IOException{if(!r.getParameterMap().isEmpty()||r.getInputStream().read()!=-1)throw ApiValues.invalid();if(!(principal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return ResponseEntity.ok().header("Cache-Control","no-store").body(service.get(actor,ApiValues.uuid(id)));}
 @PostMapping(value="/internal/v1/carrier/cancellation-results",consumes="application/json",produces="application/json")public ResponseEntity<CarrierCancellationValues.View> result(HttpServletRequest r,@RequestBody JsonNode body){if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();if(!(principal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var receipt=CarrierCancellationValues.receipt(body);if(!receipt.commandId().equals(ApiValues.uuid(header(r,"Idempotency-Key"))))throw ApiValues.invalid();return ResponseEntity.ok().header("Cache-Control","no-store").body(service.accept(proof,receipt,correlation()));}
}
