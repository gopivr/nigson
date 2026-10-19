package com.nigson.commerce.quote;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.commerce.order.OrderValues;

@RestController
@ConditionalOnProperty(name="NIGSON_QUOTES_ENABLED",havingValue="true")
public final class QuoteController {
 private final QuoteService service;public QuoteController(QuoteService service){this.service=service;}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 private void query(HttpServletRequest r){if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();}
 private ActorAssertion.Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor a))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return a;}
 private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
 private ResponseEntity<QuoteValues.Quote> response(QuoteValues.Quote q,int status){return ResponseEntity.status(status).header("Cache-Control","no-store").header("ETag","\""+q.version()+"\"").body(q);}
 @PostMapping(value="/api/v1/staff/quotes",consumes="application/json",produces="application/json") public ResponseEntity<QuoteValues.Quote> create(HttpServletRequest r,@RequestBody tools.jackson.databind.JsonNode body){query(r);return response(service.create(actor(),QuoteValues.create(body),header(r,"Idempotency-Key"),correlation()),201);}
 @PostMapping(value="/api/v1/staff/quotes/{id}/revisions",consumes="application/json",produces="application/json") public ResponseEntity<QuoteValues.Quote> revise(HttpServletRequest r,@PathVariable String id,@RequestBody tools.jackson.databind.JsonNode body){query(r);return response(service.revise(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),QuoteValues.revision(body),header(r,"Idempotency-Key"),correlation()),201);}
 @PostMapping(value="/api/v1/staff/quotes/{id}/offer",consumes="application/json",produces="application/json") public ResponseEntity<QuoteValues.Quote> offer(HttpServletRequest r,@PathVariable String id,@RequestBody tools.jackson.databind.JsonNode body){query(r);return response(service.offer(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),QuoteValues.offer(body),header(r,"Idempotency-Key"),correlation()),200);}
 @GetMapping(value="/api/v1/quotes/{id}",produces="application/json") public ResponseEntity<QuoteValues.Quote> get(HttpServletRequest r,@PathVariable String id){query(r);var q=service.get(actor(),ApiValues.uuid(id));return response(q,200);}
 @PostMapping(value="/api/v1/quotes/{id}/accept",consumes="application/json",produces="application/json") public ResponseEntity<OrderValues.Checkout> accept(HttpServletRequest r,@PathVariable String id,@RequestBody tools.jackson.databind.JsonNode body){query(r);var result=service.accept(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),QuoteValues.accept(body),header(r,"Idempotency-Key"),correlation());return ResponseEntity.status(201).header("Cache-Control","no-store").body(result);}
 @PostMapping(value="/api/v1/staff/quotes/{id}/revisions/{revisionId}/approval",consumes="application/json",produces="application/json") public ResponseEntity<QuoteValues.Quote> approve(HttpServletRequest r,@PathVariable String id,@PathVariable String revisionId,@RequestBody tools.jackson.databind.JsonNode body){query(r);return response(service.approve(actor(),ApiValues.uuid(id),ApiValues.uuid(revisionId),ApiValues.version(header(r,"If-Match")),QuoteValues.decision(body),header(r,"Idempotency-Key"),correlation()),200);}
}
