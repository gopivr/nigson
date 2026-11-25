package com.nigson.commerce.order;

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

/** Customer return requests and the separate Operations/Finance review, receipt and refund commands. */
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class ReturnController {
 private final ReturnService service;
 public ReturnController(ReturnService service){this.service=service;}
 private ActorAssertion.Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return actor;}
 private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
 private void query(HttpServletRequest r,boolean empty)throws java.io.IOException{if(!r.getParameterMap().isEmpty()||empty&&r.getInputStream().read()!=-1)throw ApiValues.invalid();}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 private ResponseEntity<ReturnValues.Item> item(ReturnValues.Item value,int status){return ResponseEntity.status(status).header("Cache-Control","no-store").header("ETag","\""+value.version()+"\"").body(value);}
 private ResponseEntity<ReturnValues.View> view(ReturnValues.View value){return ResponseEntity.ok().header("Cache-Control","no-store").header("ETag","\""+value.orderVersion()+"\"").body(value);}
 @GetMapping(value="/api/v1/orders/{id}/returns",produces="application/json")public ResponseEntity<ReturnValues.View> customerView(HttpServletRequest r,@PathVariable String id)throws java.io.IOException{query(r,true);return view(service.get(actor(),ApiValues.uuid(id),false));}
 @PostMapping(value="/api/v1/orders/{id}/returns",consumes="application/json",produces="application/json")public ResponseEntity<ReturnValues.Item> create(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body)throws java.io.IOException{query(r,false);return item(service.create(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),ReturnValues.request(body),header(r,"Idempotency-Key"),correlation()),201);}
 @GetMapping(value="/api/v1/returns/{id}",produces="application/json")public ResponseEntity<ReturnValues.Item> detail(HttpServletRequest r,@PathVariable String id)throws java.io.IOException{query(r,true);var value=service.detail(actor(),ApiValues.uuid(id));return item(value,200);}
 @PostMapping(value="/api/v1/staff/returns/{id}/review",consumes="application/json",produces="application/json")public ResponseEntity<ReturnValues.Item> review(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body)throws java.io.IOException{query(r,false);return item(service.review(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),ReturnValues.review(body),header(r,"Idempotency-Key"),correlation()),200);}
 @PostMapping(value="/api/v1/staff/returns/{id}/receipt",consumes="application/json",produces="application/json")public ResponseEntity<ReturnValues.Item> receive(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body)throws java.io.IOException{query(r,false);return item(service.receive(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),ReturnValues.receipt(body),header(r,"Idempotency-Key"),correlation()),200);}
 @PostMapping(value="/api/v1/staff/returns/{id}/refund",consumes="application/json",produces="application/json")public ResponseEntity<ReturnValues.Item> refund(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body)throws java.io.IOException{query(r,false);return item(service.refund(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),ReturnValues.refund(body),header(r,"Idempotency-Key"),correlation()),201);}
 @GetMapping(value="/api/v1/staff/orders/{id}/returns",produces="application/json")public ResponseEntity<ReturnValues.View> staffView(HttpServletRequest r,@PathVariable String id)throws java.io.IOException{query(r,true);return view(service.get(actor(),ApiValues.uuid(id),true));}
}
