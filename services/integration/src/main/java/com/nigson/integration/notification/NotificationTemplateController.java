package com.nigson.integration.notification;

import com.nigson.integration.platform.PlatformStore;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;
import com.nigson.shared.events.EventCodec;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/** Exact private API-142 owner endpoint. */
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class NotificationTemplateController {
    private final NotificationTemplateService service;
    public NotificationTemplateController(NotificationTemplateService service){this.service=service;}
    private PrivateWorkload.Proof proof(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof p))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return p;}
    private static String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}

    @PostMapping(value="/internal/v1/templates",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> create(HttpServletRequest request,@RequestBody byte[] bytes){
        if(!request.getParameterMap().isEmpty()||bytes==null||bytes.length>12000)throw ApiValues.invalid();String key=header(request,"Idempotency-Key");if(key.length()>100||!key.matches("[ -~]+"))throw ApiValues.invalid();var matches=Collections.list(request.getHeaders("If-Match"));if(matches.size()>1)throw ApiValues.invalid();Long expected=matches.isEmpty()?null:ApiValues.version(matches.getFirst());JsonNode body;try{body=EventCodec.JSON.readTree(bytes);}catch(RuntimeException malformed){throw ApiValues.invalid();}
        UUID correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;
        NotificationTemplateService.Created result=service.create(proof(),NotificationTemplateValues.parse(body),expected,key,correlation);
        return ResponseEntity.status(result.response().status()).header("Cache-Control","no-store").header("ETag","\""+result.version()+"\"").body(result.response().body());
    }
}
