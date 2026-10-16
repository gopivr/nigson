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
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class FulfillmentController {
 private final DeliveryRuleService rules;private final ShipmentService shipments;
 public FulfillmentController(DeliveryRuleService rules,ShipmentService shipments){this.rules=rules;this.shipments=shipments;}
 private ActorAssertion.Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return actor;}
 private void query(HttpServletRequest r){if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
 private <T> ResponseEntity<T> response(T value,int status,long version){return ResponseEntity.status(status).header("Cache-Control","no-store").header("ETag","\""+version+"\"").body(value);}
 @PostMapping(value="/api/v1/staff/delivery-rules",consumes="application/json",produces="application/json")public ResponseEntity<FulfillmentValues.Rule> createRule(HttpServletRequest r,@RequestBody JsonNode input){query(r);var result=rules.create(actor(),FulfillmentValues.deliveryRule(input),header(r,"Idempotency-Key"),correlation());return response(result,201,result.version());}
 @PutMapping(value="/api/v1/staff/delivery-rules/{id}",consumes="application/json",produces="application/json")public ResponseEntity<FulfillmentValues.Rule> updateRule(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input){query(r);var result=rules.update(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),FulfillmentValues.deliveryRule(input),header(r,"Idempotency-Key"),correlation());return response(result,200,result.version());}
 @PostMapping(value="/api/v1/staff/orders/{id}/shipments",consumes="application/json",produces="application/json")public ResponseEntity<FulfillmentValues.Shipment> createShipment(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input){query(r);var result=shipments.create(actor(),ApiValues.uuid(id),FulfillmentValues.shipment(input),header(r,"Idempotency-Key"),correlation());return response(result,201,result.version());}
 @PostMapping(value="/api/v1/staff/shipments/{id}/dispatch",consumes="application/json",produces="application/json")public ResponseEntity<FulfillmentValues.Shipment> dispatch(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input){query(r);var request=FulfillmentValues.dispatch(input);var result=shipments.dispatch(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),request.reason(),request.dispatchedAt(),header(r,"Idempotency-Key"),correlation());return response(result,200,result.version());}
 @PostMapping(value="/api/v1/staff/shipments/{id}/delivery",consumes="application/json",produces="application/json")public ResponseEntity<FulfillmentValues.Shipment> delivered(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input){query(r);var result=shipments.delivered(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),FulfillmentValues.delivered(input),header(r,"Idempotency-Key"),correlation());return response(result,200,result.version());}
 @PostMapping(value="/api/v1/staff/orders/{id}/stock-returns",consumes="application/json",produces="application/json")public ResponseEntity<FulfillmentValues.Return> returned(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input){query(r);return ResponseEntity.status(201).header("Cache-Control","no-store").body(shipments.returned(actor(),ApiValues.uuid(id),FulfillmentValues.returned(input),header(r,"Idempotency-Key"),correlation()));}
}
