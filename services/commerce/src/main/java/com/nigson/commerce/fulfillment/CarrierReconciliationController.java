package com.nigson.commerce.fulfillment;
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
import com.nigson.commerce.security.SessionCookies;
@RestController
@ConditionalOnProperty(name={"NIGSON_SECURITY_ENABLED","NIGSON_CARRIER_RECONCILIATION_ENABLED"},havingValue="true")
public final class CarrierReconciliationController {
 private final CarrierReconciliationService service;private final SessionCookies cookies;public CarrierReconciliationController(CarrierReconciliationService service,SessionCookies cookies){this.service=service;this.cookies=cookies;}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 @PostMapping(value="/api/v1/staff/shipments/{id}/booking-reconciliation",consumes="application/json",produces="application/json")public ResponseEntity<CarrierReconciliationValues.Result> reconcile(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body){if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);UUID correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;var result=service.reconcile(actor,cookies.read(r),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),CarrierReconciliationValues.reason(body),header(r,"Idempotency-Key"),correlation);return ResponseEntity.accepted().header("Cache-Control","no-store").body(result);}
}
