package com.nigson.experience.content;

import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.shared.security.PrivateWorkload;
import tools.jackson.databind.JsonNode;

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class ContentKnowledgeController {
    private final ContentKnowledgeService service;
    public ContentKnowledgeController(ContentKnowledgeService service) { this.service=service; }

    @PostMapping(value="/internal/v1/content/revision-validation", consumes="application/json", produces="application/json")
    public ResponseEntity<ContentKnowledgeValues.ValidationResult> validate(HttpServletRequest request, @RequestBody JsonNode body) {
        emptyQuery(request);
        if (body==null || !body.isObject() || !body.propertyNames().equals(Set.of("items")) || !body.path("items").isArray()) throw ApiValues.invalid();
        var items=new java.util.ArrayList<ContentKnowledgeValues.RequestedRevision>();
        for (var item:body.path("items")) {
            if (!item.isObject() || !item.propertyNames().equals(Set.of("contentItemId","revision"))
                    || !item.path("contentItemId").isString() || !item.path("revision").isIntegralNumber()
                    || !item.path("revision").canConvertToInt()) throw ApiValues.invalid();
            items.add(new ContentKnowledgeValues.RequestedRevision(ApiValues.uuid(item.path("contentItemId").asString()), item.path("revision").intValue()));
        }
        var result=service.validate(proof("API-125"), new ContentKnowledgeValues.ValidationInput(items), correlation(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    @GetMapping(value="/internal/v1/content/knowledge/{id}", produces="application/json")
    public ResponseEntity<ContentKnowledgeValues.ContentKnowledge> read(HttpServletRequest request, @PathVariable String id) throws java.io.IOException {
        emptyQuery(request);
        if (request.getInputStream().read()!=-1) throw ApiValues.invalid();
        var content=service.read(proof("API-126"), ApiValues.uuid(id), correlation(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(content);
    }

    private static void emptyQuery(HttpServletRequest request) {
        if (request.getQueryString()!=null || !request.getParameterMap().isEmpty()) throw ApiValues.invalid();
    }
    private static PrivateWorkload.Proof proof(String api) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth==null || !auth.isAuthenticated()) throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        if (!(auth.getPrincipal() instanceof PrivateWorkload.Proof p) || !p.route().api().equals(api)) throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        return p;
    }
    private static UUID correlation(HttpServletRequest request) {
        String value=request.getHeader("X-Correlation-ID");
        try { return value==null?UUID.randomUUID():UUID.fromString(value); }
        catch (IllegalArgumentException invalid) { throw ApiValues.invalid(); }
    }
}
