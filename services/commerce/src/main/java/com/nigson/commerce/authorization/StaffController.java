package com.nigson.commerce.authorization;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.observability.WireContext;

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED", havingValue="true")
public final class StaffController {
    private final StaffService service;
    public StaffController(StaffService service) { this.service = service; }
    private ActorAssertion.Actor actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        if (!(authentication.getPrincipal() instanceof ActorAssertion.Actor actor)) throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        return actor;
    }
    private void query(HttpServletRequest request, Set<String> allowed) {
        if (!allowed.containsAll(request.getParameterMap().keySet()) || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1)) throw ApiValues.invalid();
    }
    private String header(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        if (values.size() != 1) throw ApiValues.invalid();
        return values.getFirst();
    }
    @GetMapping(value="/api/v1/staff/users", produces="application/json")
    public Staff.Page search(HttpServletRequest request,
            @RequestParam(defaultValue="") String q, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="createdAt,desc") String sort) throws java.io.IOException {
        var actor = actor(); query(request, Set.of("q", "page", "size", "sort"));
        if (request.getContentLengthLong() > 0 || request.getInputStream().read() != -1) throw ApiValues.invalid();
        return service.search(actor, q, page, size, sort);
    }
    @PutMapping(value="/api/v1/staff/users/{id}/roles", consumes="application/json", produces="application/json")
    public ResponseEntity<Staff.View> change(HttpServletRequest request, @PathVariable String id, @RequestBody Staff.Change change) {
        var actor = actor(); query(request, Set.of());
        UUID correlation = WireContext.current() == null ? WireContext.correlation(null) : WireContext.current().correlationId;
        var result = service.change(actor, ApiValues.uuid(id), ApiValues.version(header(request, "If-Match")), change, header(request, "Idempotency-Key"), correlation);
        return ResponseEntity.ok().eTag(Long.toString(result.version())).body(result);
    }
}
