package com.nigson.commerce.notification;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.observability.WireContext;
import com.nigson.commerce.security.SessionCookies;
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class TemplateController {
    private final TemplateService service;private final SessionCookies cookies;private final int phase;
    public TemplateController(TemplateService service,SessionCookies cookies,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase){this.service=service;this.cookies=cookies;this.phase=phase;}
    private ActorAssertion.Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return actor;}
    private String header(HttpServletRequest r,String name,boolean required){var values=Collections.list(r.getHeaders(name));if(values.size()>1||required&&values.isEmpty())throw ApiValues.invalid();return values.isEmpty()?null:values.getFirst();}
    @PostMapping(value="/api/v1/staff/notification-templates",consumes="application/json",produces="application/json")
    public ResponseEntity<TemplateValues.View> put(HttpServletRequest r,@RequestBody JsonNode input){
        if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();String tag=header(r,"If-Match",false);Long version=tag==null?null:ApiValues.version(tag);UUID correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;
        var result=service.put(actor(),cookies.read(r),TemplateValues.template(input,phase),version,header(r,"Idempotency-Key",true),correlation);var response=ResponseEntity.ok().header("Cache-Control","no-store");if(result.version()!=null)response.eTag(Long.toString(result.version()));return response.body(new TemplateValues.View(result.code(),result.revision()));
    }
}
