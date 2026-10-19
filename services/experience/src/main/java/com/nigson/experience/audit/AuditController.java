package com.nigson.experience.audit;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.security.PrivateWorkload;

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class AuditController {
    private final AuditQueryService service;
    public AuditController(AuditQueryService service){this.service=service;}
    private PrivateWorkload.Proof proof(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof p))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var route=p.route();if(!route.api().equals("API-138")||!route.owner().equals("experience")||!route.client().equals("nigson-commerce")||!route.scope().equals("audit.read_WITH_SIGNED_ADMIN")||p.actor()==null)throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return p;}
    @GetMapping(value="/api/v1/experience/staff/audit-records",produces="application/json")
    public ResponseEntity<AuditValues.Page> search(HttpServletRequest request)throws IOException{
        if(!Set.of("resourceId","action","from","to","page","size","sort").containsAll(request.getParameterMap().keySet()))throw ApiValues.invalid();
        var q=new java.util.HashMap<String,String>();request.getParameterMap().forEach((k,v)->{if(v==null||v.length!=1||v[0]==null||v[0].isEmpty()||v[0].length()>200)throw ApiValues.invalid();q.put(k,v[0]);});if(request.getInputStream().read()!=-1)throw ApiValues.invalid();
        try{var filters=new AuditValues.Query(q.containsKey("resourceId")?ApiValues.uuid(q.get("resourceId")):null,q.get("action"),q.containsKey("from")?ApiValues.instant(q.get("from")):null,q.containsKey("to")?ApiValues.instant(q.get("to")):null,integer(q.getOrDefault("page","0")),integer(q.getOrDefault("size","20")),q.getOrDefault("sort","occurredAt,desc"));return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.search(proof().actor(),filters));}catch(NumberFormatException invalid){throw ApiValues.invalid();}
    }
    private static int integer(String value){if(value==null||!value.matches("0|[1-9][0-9]{0,9}"))throw ApiValues.invalid();return Integer.parseInt(value);}
}
