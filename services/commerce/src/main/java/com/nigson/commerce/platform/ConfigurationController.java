package com.nigson.commerce.platform;

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
public class ConfigurationController {
    private final OwnerConfiguration service;
    public ConfigurationController(OwnerConfiguration service){this.service=service;}
    private String header(HttpServletRequest request,String name){var values=Collections.list(request.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
    @PutMapping(value="/api/v1/commerce/staff/configuration/{key}",consumes="application/json",produces="application/json")
    public ResponseEntity<OwnerConfiguration.View> put(HttpServletRequest request,@PathVariable String key,@RequestBody JsonNode input){
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        if(!request.getParameterMap().isEmpty())throw ApiValues.invalid();
        UUID correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;
        var result=service.put(actor,key,ApiValues.version(header(request,"If-Match")),input,header(request,"Idempotency-Key"),correlation);
        return ResponseEntity.ok().eTag(Long.toString(result.version())).body(result);
    }
}
