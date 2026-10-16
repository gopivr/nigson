package com.nigson.integration.carrier;

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
@ConditionalOnProperty(name="NIGSON_CARRIER_ENABLED",havingValue="true")
public final class CarrierQuoteController {
 private final CarrierQuoteService service;public CarrierQuoteController(CarrierQuoteService service){this.service=service;}
 @PostMapping(value="/internal/v1/carrier/quotes",consumes="application/json",produces="application/json")
 public ResponseEntity<CarrierValues.Quote> quote(HttpServletRequest request,@RequestBody JsonNode input){
  if(!request.getParameterMap().isEmpty())throw ApiValues.invalid();var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
  var correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;
  return ResponseEntity.ok().header("Cache-Control","no-store").header("X-Nigson-Carrier-Mode",service.mode()).header("X-Nigson-Carrier-Account-SHA256",service.accountHash()).body(service.quote(proof,CarrierValues.quoteInput(input),correlation));
 }
}
