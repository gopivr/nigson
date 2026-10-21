package com.nigson.experience.notification;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;

@RestController
public final class NotificationController {
    private final NotificationOutcomeService notifications;
    public NotificationController(NotificationOutcomeService notifications){this.notifications=notifications;}
    @PostMapping(value="/internal/v1/notification-deliveries",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NotificationValues.Acknowledged> delivery(HttpServletRequest request,@RequestBody JsonNode body){
        if(!request.getParameterMap().isEmpty()||request.getContentLengthLong()>8192)throw ApiValues.invalid();
        var headers=Collections.list(request.getHeaders("Idempotency-Key"));if(headers.size()!=1)throw ApiValues.invalid();
        var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        var wire=WireContext.current();UUID correlation=wire==null?WireContext.correlation(null):wire.correlationId;
        var result=notifications.record(proof,NotificationValues.delivery(body),headers.getFirst(),correlation);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
