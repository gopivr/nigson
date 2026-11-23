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
@RestController
@ConditionalOnProperty(name="NIGSON_CARRIER_CANCELLATION_ENABLED",havingValue="true")
public final class PandagoCancellationController {
 private final PandagoCancellationService service;public PandagoCancellationController(PandagoCancellationService service){this.service=service;}
 @PostMapping(value="/internal/v1/carrier/cancellations",consumes="application/json",produces="application/json")
 public ResponseEntity<JsonNode> request(HttpServletRequest request,@RequestBody JsonNode body){if(!request.getParameterMap().isEmpty())throw ApiValues.invalid();var headers=Collections.list(request.getHeaders("Idempotency-Key"));if(headers.size()!=1)throw ApiValues.invalid();var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var correlations=Collections.list(request.getHeaders("X-Correlation-ID"));if(correlations.size()!=1)throw ApiValues.invalid();var correlation=ApiValues.uuid(correlations.getFirst());return ResponseEntity.accepted().header("Cache-Control","no-store").body(service.request(proof,body,headers.getFirst(),correlation));}
}
