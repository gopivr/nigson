package com.nigson.experience.lead;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;

@RestController
@ConditionalOnProperty(name="NIGSON_MESSAGING_INBOUND_ENABLED",havingValue="true")
public final class LeadMessagingContextController {
    private final LeadMessagingContext context;
    public LeadMessagingContextController(LeadMessagingContext context){this.context=context;}
    @GetMapping(value="/internal/v1/leads/{id}/messaging-context",produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LeadMessagingContext.Context> read(HttpServletRequest request,@PathVariable String id){
        if(!request.getParameterMap().isEmpty()||request.getContentLengthLong()>0)throw ApiValues.invalid();
        var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        var wire=WireContext.current();UUID correlation=wire==null?WireContext.correlation(null):wire.correlationId;
        var result=context.read(proof,ApiValues.uuid(id),correlation);return ResponseEntity.ok().header("Cache-Control","no-store").body(result);
    }
}
