package com.nigson.commerce.catalogue;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.problem.*;

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class CataloguePublicationController {
    public record Policy(boolean imageRequired){}
    private final CataloguePublicationPolicy policy;private final CatalogueAuthority authority;
    public CataloguePublicationController(CataloguePublicationPolicy policy,CatalogueAuthority authority){this.policy=policy;this.authority=authority;}
    @GetMapping(value="/api/v1/staff/catalogue/publication-policy",produces="application/json")
    public ResponseEntity<Policy> get(HttpServletRequest request)throws java.io.IOException{
        if(!request.getParameterMap().isEmpty()||request.getInputStream().read()!=-1)throw ApiValues.invalid();
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        if(!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        authority.require(actor,"CONTENT_EDITOR");
        return ResponseEntity.ok().header("Cache-Control","no-store").body(new Policy(policy.imageRequired()));
    }
}
