package com.nigson.experience.platform;

import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED", havingValue="true")
public final class ConfigurationController {
    private final OwnerConfiguration service;
    public ConfigurationController(OwnerConfiguration service) { this.service = service; }
    private static String header(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        if (values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank() || values.getFirst().length() > 100) throw ApiValues.invalid();
        return values.getFirst();
    }
    private static PrivateWorkload.Proof proof() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        if (!(auth.getPrincipal() instanceof PrivateWorkload.Proof value)) throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        return value;
    }
    @PutMapping(value="/api/v1/experience/staff/configuration/{key}", consumes="application/json", produces="application/json")
    public ResponseEntity<OwnerConfiguration.View> put(HttpServletRequest request, @PathVariable String key, @RequestBody JsonNode input) {
        if (!request.getParameterMap().isEmpty()) throw ApiValues.invalid();
        var wire = WireContext.current(); UUID correlation = wire == null ? WireContext.correlation(null) : wire.correlationId;
        var result = service.put(proof(), key, ApiValues.version(header(request, "If-Match")), input,
                ApiValues.idempotencyKey(header(request, "Idempotency-Key")), correlation);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag("\"" + result.version() + "\"").body(result);
    }
}
