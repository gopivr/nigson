package com.nigson.commerce.notification;
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
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class NotificationController {
    private final NotificationService service;public NotificationController(NotificationService service){this.service=service;}
    private PrivateWorkload.Proof proof(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return proof;}
    private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
    private void query(HttpServletRequest r){if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();}
    @GetMapping(value="/internal/v1/notification-context/{orderId}",produces="application/json")
    public ResponseEntity<NotificationValues.Context> context(HttpServletRequest r,@PathVariable String orderId)throws java.io.IOException{query(r);if(r.getInputStream().read()!=-1)throw ApiValues.invalid();return ResponseEntity.ok().header("Cache-Control","no-store").body(service.context(proof(),ApiValues.uuid(orderId)));}
    @PostMapping(value="/internal/v1/notification-deliveries",consumes="application/json",produces="application/json")
    public ResponseEntity<NotificationValues.Acknowledged> delivery(HttpServletRequest r,@RequestBody JsonNode input){query(r);UUID correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;return ResponseEntity.ok().header("Cache-Control","no-store").body(service.delivery(proof(),NotificationValues.delivery(input),header(r,"Idempotency-Key"),correlation));}
}
