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
@ConditionalOnProperty(name="NIGSON_CARRIER_QUOTES_ENABLED",havingValue="true")
public final class DeliveryQuoteController {
 private final DeliveryQuoteService service;private final SessionCookies cookies;public DeliveryQuoteController(DeliveryQuoteService service,SessionCookies cookies){this.service=service;this.cookies=cookies;}
 private String header(HttpServletRequest request,String name){var values=Collections.list(request.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 @PostMapping(value="/api/v1/carts/{id}/delivery-quotes",consumes="application/json",produces="application/json")
 public ResponseEntity<DeliveryQuoteValues.Quote> create(HttpServletRequest request,@PathVariable String id,@RequestBody JsonNode input){
  if(!request.getParameterMap().isEmpty())throw ApiValues.invalid();var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;
  return ResponseEntity.status(201).header("Cache-Control","no-store").body(service.create(actor,cookies.read(request),ApiValues.uuid(id),ApiValues.version(header(request,"If-Match")),DeliveryQuoteValues.input(input),header(request,"Idempotency-Key"),correlation));
 }
 @PostMapping(value="/api/v1/quotes/{id}/delivery-quotes",consumes="application/json",produces="application/json")
 public ResponseEntity<DeliveryQuoteValues.Quote> quote(HttpServletRequest request,@PathVariable String id,@RequestBody JsonNode input){
  if(!request.getParameterMap().isEmpty())throw ApiValues.invalid();var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;
  return ResponseEntity.status(201).header("Cache-Control","no-store").body(service.createForQuote(actor,cookies.read(request),ApiValues.uuid(id),ApiValues.version(header(request,"If-Match")),DeliveryQuoteValues.quoteInput(input),header(request,"Idempotency-Key"),correlation));
 }
}
