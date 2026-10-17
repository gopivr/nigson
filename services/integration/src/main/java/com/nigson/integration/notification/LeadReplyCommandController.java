package com.nigson.integration.notification;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;
import com.nigson.shared.events.EventCodec;

@RestController
@ConditionalOnProperty(name="NIGSON_MESSAGING_STAFF_REPLY_ENABLED",havingValue="true")
public final class LeadReplyCommandController {
    private final LeadReplyCommandService service;
    public LeadReplyCommandController(LeadReplyCommandService service){this.service=service;}
    @PostMapping(value="/internal/v1/notification-commands",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LeadReplyValues.Response> enqueue(HttpServletRequest request, @RequestBody byte[] bytes){
        if(!request.getParameterMap().isEmpty()||bytes==null||bytes.length>8192)throw ApiValues.invalid();tools.jackson.databind.JsonNode body;try{body=EventCodec.JSON.readTree(bytes);}catch(RuntimeException invalid){throw ApiValues.invalid();}var command=LeadReplyValues.command(body);
        var idem=Collections.list(request.getHeaders("Idempotency-Key"));if(idem.size()!=1||!command.commandId().toString().equals(idem.getFirst()))throw ApiValues.invalid();
        var delegated=Collections.list(request.getHeaders("X-Nigson-Context-Actor"));if(delegated.size()!=1||delegated.getFirst().length()>8192)throw ApiValues.invalid();
        var authentication=SecurityContextHolder.getContext().getAuthentication();if(authentication==null||!authentication.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(authentication.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        var wire=WireContext.current();UUID correlation=wire==null?WireContext.correlation(null):wire.correlationId;
        var result=service.enqueue(proof,delegated.getFirst(),command,correlation);return ResponseEntity.accepted().header("Cache-Control","no-store").body(result);
    }
}
