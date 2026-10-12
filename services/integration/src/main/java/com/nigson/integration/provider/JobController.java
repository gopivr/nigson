package com.nigson.integration.provider;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;
import com.nigson.shared.observability.WireContext;
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class JobController {
 private final JobService service;public JobController(JobService service){this.service=service;}
 private PrivateWorkload.Proof proof(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return proof;}
 private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
 @GetMapping(value="/internal/v1/jobs",produces="application/json")public ResponseEntity<JobValues.Page> list(HttpServletRequest r)throws java.io.IOException{
  if(!Set.of("kind","state","page","size","sort").containsAll(r.getParameterMap().keySet())||r.getInputStream().read()!=-1)throw ApiValues.invalid();for(var v:r.getParameterMap().values())if(v.length!=1||v[0].length()>200)throw ApiValues.invalid();
  var query=new JobValues.Query(JobValues.kind(r.getParameter("kind")),JobValues.state(r.getParameter("state")),JobValues.number(r.getParameter("page"),0),JobValues.number(r.getParameter("size"),20),r.getParameter("sort")==null?"nextAttemptAt,asc":r.getParameter("sort"));
  return ResponseEntity.ok().header("Cache-Control","no-store").body(service.list(proof(),query));
 }
 @PostMapping(value="/internal/v1/jobs/{id}/retry",consumes="application/json",produces="application/json")public ResponseEntity<JobValues.Job> retry(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body){
  if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();UUID correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;
  var accepted=service.retry(proof(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),JobValues.retry(body),header(r,"Idempotency-Key"),correlation);
  return ResponseEntity.accepted().header("Cache-Control","no-store").header("ETag","\""+accepted.version()+"\"").body(accepted.job());
 }
}
