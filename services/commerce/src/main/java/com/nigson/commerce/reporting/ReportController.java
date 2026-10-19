package com.nigson.commerce.reporting;

import java.io.IOException;
import java.util.Map;
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

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class ReportController {
    private final ReportService service;
    public ReportController(ReportService service){this.service=service;}
    private ActorAssertion.Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor a))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return a;}
    @GetMapping(value="/api/v1/staff/reports/commerce",produces="application/json")
    public ResponseEntity<ReportValues.Summary> commerce(HttpServletRequest request)throws IOException{
        if(!Set.of("from","to","currency","organizationId").containsAll(request.getParameterMap().keySet()))throw ApiValues.invalid();
        var values=new java.util.HashMap<String,String>();request.getParameterMap().forEach((k,v)->{if(v==null||v.length!=1||v[0]==null||v[0].isEmpty()||v[0].length()>100)throw ApiValues.invalid();values.put(k,v[0]);});
        if(request.getInputStream().read()!=-1)throw ApiValues.invalid();
        try{var query=new ReportValues.Query(ApiValues.instant(values.get("from")),ApiValues.instant(values.get("to")),values.get("currency"),values.containsKey("organizationId")?ApiValues.uuid(values.get("organizationId")):null);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.commerce(actor(),query));}catch(IllegalArgumentException bad){throw ApiValues.invalid();}
    }
}
