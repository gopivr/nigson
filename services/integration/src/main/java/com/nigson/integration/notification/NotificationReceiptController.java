package com.nigson.integration.notification;
import java.time.Clock;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.nigson.integration.platform.AuditWriter;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"NIGSON_SECURITY_ENABLED","NIGSON_MESSAGING_STATUS_ENABLED"},havingValue="true")
class NotificationReceiptConfiguration {
 @Bean NotificationReceiptService notificationReceiptService(JdbcTemplate jdbc,PlatformTransactionManager tx,AuditWriter audit,MeterRegistry metrics){return new NotificationReceiptService(jdbc,tx,audit,Clock.systemUTC(),metrics);}
}
@RestController
@ConditionalOnProperty(name={"NIGSON_SECURITY_ENABLED","NIGSON_MESSAGING_STATUS_ENABLED"},havingValue="true")
public final class NotificationReceiptController {
 private final NotificationReceiptService service;public NotificationReceiptController(NotificationReceiptService service){this.service=service;}
 @PostMapping(value="/internal/v1/notification-provider-receipts",consumes="application/json",produces="application/json")public ResponseEntity<Map<String,Boolean>> receive(HttpServletRequest request,@RequestBody tools.jackson.databind.JsonNode body){if(!request.getParameterMap().isEmpty())throw ApiValues.invalid();var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var receipt=NotificationReceiptValues.parse(body);var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1||!receipt.eventHash().equals(keys.getFirst()))throw ApiValues.invalid();UUID correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;service.receive(proof,receipt,correlation);return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("acknowledged",true));}
}
