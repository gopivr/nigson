package com.nigson.experience.reporting;

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
import com.nigson.shared.security.PrivateWorkload;

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class ReportController {
    private final ReportService service;
    public ReportController(ReportService service){this.service=service;}
    private PrivateWorkload.Proof proof(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof p))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var route=p.route();if(!route.api().equals("API-110")||!route.owner().equals("experience")||!route.client().equals("nigson-commerce")||!route.scope().equals("report.read_WITH_SIGNED_ANALYST")||p.actor()==null)throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return p;}
    @GetMapping(value="/api/v1/staff/reports/engagement",produces="application/json")
    public ResponseEntity<ReportValues.Summary> engagement(HttpServletRequest request)throws IOException{
        if(!Set.of("from","to").containsAll(request.getParameterMap().keySet()))throw ApiValues.invalid();var values=new java.util.HashMap<String,String>();request.getParameterMap().forEach((k,v)->{if(v==null||v.length!=1||v[0]==null||v[0].isEmpty()||v[0].length()>40)throw ApiValues.invalid();values.put(k,v[0]);});if(request.getInputStream().read()!=-1)throw ApiValues.invalid();
        try{var query=new ReportValues.Query(ApiValues.instant(values.get("from")),ApiValues.instant(values.get("to")));return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.engagement(proof().actor(),query));}catch(IllegalArgumentException invalid){throw ApiValues.invalid();}
    }
}
