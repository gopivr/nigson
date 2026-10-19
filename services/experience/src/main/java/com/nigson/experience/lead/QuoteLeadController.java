package com.nigson.experience.lead;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.nigson.experience.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;

@RestController
public final class QuoteLeadController {
 private final QuoteLeadAssociation quotes;
 public QuoteLeadController(QuoteLeadAssociation quotes){this.quotes=quotes;}
 private PrivateWorkload.Proof proof(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof p))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return p;}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 private void emptyQuery(HttpServletRequest r){if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();}
 @PostMapping(value="/api/v1/quote-requests",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
 public ResponseEntity<QuoteLeadValues.Created> create(HttpServletRequest r,@RequestBody tools.jackson.databind.JsonNode body){emptyQuery(r);var wire=WireContext.current();var value=quotes.create(proof(),QuoteLeadValues.parse(body),header(r,"Idempotency-Key"),wire==null?WireContext.correlation(null):wire.correlationId);return ResponseEntity.status(201).header("Cache-Control","no-store").body(value);}
 @GetMapping(value="/internal/v1/leads/{id}/quote-context",produces=MediaType.APPLICATION_JSON_VALUE)
 public ResponseEntity<QuoteLeadAssociation.Context> context(HttpServletRequest request,@PathVariable String id){emptyQuery(request);if(request.getContentLengthLong()>0)throw ApiValues.invalid();var value=quotes.read(proof(),ApiValues.uuid(id));return ResponseEntity.ok().header("Cache-Control","no-store").body(value);}
 @PostMapping(value="/internal/v1/leads/{id}/quote-association",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
 public ResponseEntity<QuoteLeadAssociation.Acknowledgement> associate(HttpServletRequest request,@PathVariable String id,@RequestBody tools.jackson.databind.JsonNode body){emptyQuery(request);var fields=Set.of("commandId","quoteId","quoteState");if(body==null||!body.isObject()||!body.propertyNames().equals(fields)||!body.path("quoteState").isString())throw ApiValues.invalid();var input=new QuoteLeadAssociation.AssociationInput(ApiValues.uuid(body.path("commandId").asString()),ApiValues.uuid(body.path("quoteId").asString()),body.path("quoteState").asString());if(!header(request,"Idempotency-Key").equals(input.commandId().toString()))throw ApiValues.invalid();var wire=WireContext.current();var result=quotes.associate(proof(),ApiValues.uuid(id),input,wire==null?WireContext.correlation(null):wire.correlationId);return ResponseEntity.ok().header("Cache-Control","no-store").body(result);}
}
