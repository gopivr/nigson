package com.nigson.commerce.provider;
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
import com.nigson.commerce.security.SessionCookies;

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class JobController {
    private final JobService service;private final SessionCookies cookies;
    public JobController(JobService service,SessionCookies cookies){this.service=service;this.cookies=cookies;}
    private ActorAssertion.Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return actor;}
    private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
    private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
    @GetMapping(value="/api/v1/staff/integration-jobs",produces="application/json")
    public ResponseEntity<JobValues.Page> list(HttpServletRequest r)throws java.io.IOException{
        if(!Set.of("state","page","size","sort").containsAll(r.getParameterMap().keySet())||r.getInputStream().read()!=-1)throw ApiValues.invalid();for(var v:r.getParameterMap().values())if(v.length!=1||v[0].length()>200)throw ApiValues.invalid();
        int page=number(r.getParameter("page"),0),size=number(r.getParameter("size"),20);String sort=r.getParameter("sort");var q=new JobValues.Query(r.getParameter("state"),page,size,sort==null?"nextAttemptAt,asc":sort);
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.list(actor(),q,correlation()));
    }
    private int number(String value,int fallback){if(value==null)return fallback;try{if(!value.matches("0|[1-9][0-9]{0,9}"))throw ApiValues.invalid();return Integer.parseInt(value);}catch(NumberFormatException bad){throw ApiValues.invalid();}}
    @PostMapping(value="/api/v1/staff/integration-jobs/{id}/retry",consumes="application/json",produces="application/json")
    public ResponseEntity<JobValues.Job> retry(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body){
        if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();var result=service.retry(actor(),cookies.read(r),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),JobValues.retry(body),header(r,"Idempotency-Key"),correlation());
        return ResponseEntity.accepted().eTag(Long.toString(result.version())).header("Cache-Control","no-store").body(result.job());
    }
}
