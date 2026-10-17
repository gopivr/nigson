package com.nigson.commerce.notification;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.nigson.shared.problem.*;
import com.nigson.shared.observability.WireContext;
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class TwilioStatusController {
 private final ObjectProvider<TwilioStatusIngress> ingress;public TwilioStatusController(ObjectProvider<TwilioStatusIngress> ingress){this.ingress=ingress;}
 @PostMapping(value="/api/v1/webhooks/notifications/twilio/status",consumes="application/x-www-form-urlencoded",produces="application/json")
 public ResponseEntity<Map<String,Boolean>> status(HttpServletRequest request,@RequestBody byte[] body){String query=request.getQueryString();if(query==null||!query.matches("jobId=[0-9a-f-]{36}"))throw ApiValues.invalid();String text=query.substring(6);UUID job=ApiValues.uuid(text);if(!query.equals("jobId="+job))throw ApiValues.invalid();String signature=single(request,"X-Twilio-Signature");var active=ingress.getIfAvailable();if(active==null)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);UUID correlation=WireContext.current()==null?WireContext.correlation(request.getHeader("X-Correlation-ID")):WireContext.current().correlationId;active.receive(job,body,signature,correlation);return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("received",true));}
 private static String single(HttpServletRequest request,String header){var values=Collections.list(request.getHeaders(header));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
}
