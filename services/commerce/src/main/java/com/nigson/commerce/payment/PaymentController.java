package com.nigson.commerce.payment;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.security.PrivateWorkload;
import com.nigson.shared.observability.WireContext;

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class PaymentController {
 private final PaymentService service;private final ObjectProvider<WebhookIngress> ingress;private final ObjectProvider<PaymentReconciler> reconciler;
 public PaymentController(PaymentService service,ObjectProvider<WebhookIngress> ingress,ObjectProvider<PaymentReconciler> reconciler){this.service=service;this.ingress=ingress;this.reconciler=reconciler;}
 private ActorAssertion.Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return actor;}
 private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
 private void query(HttpServletRequest r,boolean empty)throws java.io.IOException{if(!r.getParameterMap().isEmpty()||empty&&r.getInputStream().read()!=-1)throw ApiValues.invalid();}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 private ResponseEntity<PaymentValues.Refund> refund(PaymentValues.Refund value,int status){return ResponseEntity.status(status).header("Cache-Control","no-store").header("ETag","\""+value.version()+"\"").body(value);}
 @GetMapping(value="/api/v1/orders/{id}/payment",produces="application/json")public ResponseEntity<PaymentValues.Payment> payment(HttpServletRequest r,@PathVariable String id)throws java.io.IOException{query(r,true);return ResponseEntity.ok().header("Cache-Control","no-store").body(service.payment(actor(),ApiValues.uuid(id)));}
 @PostMapping(value="/api/v1/webhooks/payments/paystack",consumes="application/json",produces="application/json")public ResponseEntity<Map<String,Boolean>> callback(HttpServletRequest r,@RequestBody byte[] raw)throws java.io.IOException{query(r,false);String signature=header(r,"X-Paystack-Signature");var active=ingress.getIfAvailable();if(active==null)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("received",active.receive(raw,signature,correlation())));}
 @PostMapping(value="/api/v1/staff/orders/{id}/refunds",consumes="application/json",produces="application/json")public ResponseEntity<PaymentValues.Refund> request(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body)throws java.io.IOException{query(r,false);return refund(service.request(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),PaymentValues.refund(body),header(r,"Idempotency-Key"),correlation()),201);}
 @PostMapping(value="/api/v1/staff/refunds/{id}/approval",consumes="application/json",produces="application/json")public ResponseEntity<PaymentValues.Refund> approval(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body)throws java.io.IOException{query(r,false);return refund(service.approve(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),PaymentValues.approval(body),header(r,"Idempotency-Key"),correlation()),200);}
 @GetMapping(value="/api/v1/staff/refunds/{id}",produces="application/json")public ResponseEntity<PaymentValues.RefundDetail> detail(HttpServletRequest r,@PathVariable String id)throws java.io.IOException{query(r,true);var value=service.detail(actor(),ApiValues.uuid(id));return ResponseEntity.ok().header("Cache-Control","no-store").header("ETag","\""+value.version()+"\"").body(value);}
 @PostMapping(value="/internal/v1/payments/reconciliation",consumes="application/json",produces="application/json")public ResponseEntity<PaymentValues.ReconciliationResult> reconciliation(HttpServletRequest r,@RequestBody JsonNode body)throws java.io.IOException{query(r,false);var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var active=reconciler.getIfAvailable();if(active==null)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);return ResponseEntity.accepted().header("Cache-Control","no-store").body(active.reconcile(proof,PaymentValues.reconciliation(body),header(r,"Idempotency-Key"),correlation()));}
}
