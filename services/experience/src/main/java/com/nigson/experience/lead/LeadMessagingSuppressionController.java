package com.nigson.experience.lead;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;

@RestController
public final class LeadMessagingSuppressionController {
    private final LeadMessagingSuppressionCheck check;
    public LeadMessagingSuppressionController(LeadMessagingSuppressionCheck check){this.check=check;}
    @PostMapping(value="/internal/v1/notification-suppression-checks",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LeadMessagingSuppressionCheck.Result> read(HttpServletRequest request,@RequestBody tools.jackson.databind.JsonNode body){
        if(!request.getParameterMap().isEmpty()||body==null||!body.isObject()||!body.propertyNames().equals(Set.of("destinationHash","channel"))||!body.path("destinationHash").isString()||!body.path("channel").isString())throw ApiValues.invalid();
        var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        var wire=WireContext.current();UUID correlation=wire==null?WireContext.correlation(null):wire.correlationId;
        var result=check.read(proof,body.path("destinationHash").asString(),body.path("channel").asString(),correlation);
        return ResponseEntity.ok().header("Cache-Control","no-store").body(result);
    }
}
