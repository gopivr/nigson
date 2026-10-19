package com.nigson.commerce.order;
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
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class OrderController {
 private final OrderService service;public OrderController(OrderService service){this.service=service;}
 private ActorAssertion.Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return actor;}
 private PrivateWorkload.Proof proof(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return proof;}
 private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
 private void query(HttpServletRequest r,boolean empty)throws java.io.IOException{if(!r.getParameterMap().isEmpty()||empty&&r.getInputStream().read()!=-1)throw ApiValues.invalid();}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 private ResponseEntity<OrderValues.Order> response(OrderValues.Order order){return ResponseEntity.ok().header("Cache-Control","no-store").header("ETag","\""+order.version()+"\"").body(order);}
 private OrderValues.Query filters(HttpServletRequest r,boolean staff)throws java.io.IOException{if(r.getInputStream().read()!=-1)throw ApiValues.invalid();var allowed=staff?Set.of("status","paymentStatus","from","to","page","size","sort"):Set.of("organizationId","status","page","size","sort");for(var entry:r.getParameterMap().entrySet())if(!allowed.contains(entry.getKey())||entry.getValue().length!=1||entry.getValue()[0].isEmpty())throw ApiValues.invalid();try{return new OrderValues.Query(r.getParameter("organizationId")==null?null:ApiValues.uuid(r.getParameter("organizationId")),r.getParameter("status"),r.getParameter("paymentStatus"),r.getParameter("from")==null?null:ApiValues.instant(r.getParameter("from")),r.getParameter("to")==null?null:ApiValues.instant(r.getParameter("to")),integer(r,"page",0),integer(r,"size",20),r.getParameter("sort")==null?"createdAt,desc":r.getParameter("sort"));}catch(NumberFormatException invalid){throw ApiValues.invalid();}}
 private int integer(HttpServletRequest r,String key,int fallback){String raw=r.getParameter(key);if(raw==null)return fallback;if(!raw.matches("0|[1-9][0-9]{0,9}"))throw ApiValues.invalid();return Integer.parseInt(raw);}
 @PostMapping(value="/api/v1/checkout/attempts",consumes="application/json",produces="application/json")public ResponseEntity<OrderValues.Checkout> checkout(HttpServletRequest r,@RequestBody JsonNode body)throws java.io.IOException{query(r,false);var result=service.checkout(actor(),OrderValues.checkout(body),header(r,"Idempotency-Key"),correlation());return ResponseEntity.status(Set.of("CREATED","INITIATING","UNKNOWN").contains(result.payment().state())?202:201).header("Cache-Control","no-store").header("ETag","\""+result.order().version()+"\"").body(result);}
 @GetMapping(value="/api/v1/orders",produces="application/json")public ResponseEntity<OrderValues.Page> list(HttpServletRequest r)throws java.io.IOException{return ResponseEntity.ok().header("Cache-Control","no-store").body(service.list(actor(),filters(r,false),false));}
 @GetMapping(value="/api/v1/orders/{id}",produces="application/json")public ResponseEntity<OrderValues.Order> get(HttpServletRequest r,@PathVariable String id)throws java.io.IOException{query(r,true);return response(service.get(actor(),ApiValues.uuid(id)));}
 @PostMapping(value="/api/v1/orders/{id}/cancellation",consumes="application/json",produces="application/json")public ResponseEntity<OrderValues.Order> cancelled(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body)throws java.io.IOException{query(r,false);return response(service.cancelled(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),OrderValues.reason(body),header(r,"Idempotency-Key"),correlation()));}
 @GetMapping(value="/api/v1/staff/orders",produces="application/json")public ResponseEntity<OrderValues.Page> staff(HttpServletRequest r)throws java.io.IOException{return ResponseEntity.ok().header("Cache-Control","no-store").body(service.list(actor(),filters(r,true),true));}
 @PostMapping(value="/api/v1/staff/orders/{id}/processing",consumes="application/json",produces="application/json")public ResponseEntity<OrderValues.Order> processing(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body)throws java.io.IOException{query(r,false);return response(service.processing(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),OrderValues.reason(body),header(r,"Idempotency-Key"),correlation()));}
 @PostMapping(value="/internal/v1/purchase-proofs",consumes="application/json",produces="application/json")public ResponseEntity<OrderValues.Proof> purchase(HttpServletRequest r,@RequestBody JsonNode body)throws java.io.IOException{query(r,false);return ResponseEntity.status(201).header("Cache-Control","no-store").body(service.proof(proof(),OrderValues.proof(body)));}
 @PostMapping(value="/internal/v1/ai/order-status",consumes="application/json",produces="application/json")public ResponseEntity<OrderValues.AiStatus> aiStatus(HttpServletRequest r,@RequestBody JsonNode body)throws java.io.IOException{query(r,false);return ResponseEntity.ok().header("Cache-Control","no-store").body(service.aiStatus(proof(),OrderValues.aiStatusId(body),correlation()));}
}
