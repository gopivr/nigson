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
@ConditionalOnProperty(name={"NIGSON_SECURITY_ENABLED","NIGSON_CARRIER_CANCELLATION_ENABLED"},havingValue="true")
public final class CarrierCancellationController {
 private final CarrierCancellationService service;public CarrierCancellationController(CarrierCancellationService service){this.service=service;}
 @PostMapping(value="/internal/v1/carrier/cancellations",consumes="application/json",produces="application/json")public ResponseEntity<CarrierCancellationValues.Acknowledgement> exchange(HttpServletRequest r,@RequestBody JsonNode body){if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var keys=Collections.list(r.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw ApiValues.invalid();UUID correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;return ResponseEntity.accepted().header("Cache-Control","no-store").body(service.exchange(proof,CarrierCancellationValues.command(body),ApiValues.uuid(keys.getFirst()),correlation));}
}
