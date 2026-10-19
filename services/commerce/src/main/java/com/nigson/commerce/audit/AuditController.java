package com.nigson.commerce.audit;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.shared.security.ActorAssertion;

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED", havingValue="true")
public final class AuditController {
    private final AuditQueryService service;
    public AuditController(AuditQueryService service) { this.service = service; }

    private ActorAssertion.Actor actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        if (!(authentication.getPrincipal() instanceof ActorAssertion.Actor actor)) throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        return actor;
    }

    @GetMapping(value="/api/v1/commerce/staff/audit-records", produces="application/json")
    public ResponseEntity<AuditValues.Page> search(HttpServletRequest request) throws IOException {
        if (!Set.of("resourceId", "action", "from", "to", "page", "size", "sort").containsAll(request.getParameterMap().keySet())) throw ApiValues.invalid();
        var query = new java.util.HashMap<String, String>();
        request.getParameterMap().forEach((key, values) -> {
            if (values == null || values.length != 1 || values[0] == null || values[0].length() > 200) throw ApiValues.invalid();
            query.put(key, values[0]);
        });
        if (request.getInputStream().read() != -1) throw ApiValues.invalid();
        try {
            var filters = new AuditValues.Query(query.containsKey("resourceId") ? ApiValues.uuid(query.get("resourceId")) : null,
                    query.get("action"), query.containsKey("from") ? ApiValues.instant(query.get("from")) : null,
                    query.containsKey("to") ? ApiValues.instant(query.get("to")) : null,
                    integer(query.getOrDefault("page", "0")), integer(query.getOrDefault("size", "20")),
                    query.getOrDefault("sort", "occurredAt,desc"));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.search(actor(), filters));
        } catch (NumberFormatException invalid) {
            throw ApiValues.invalid();
        }
    }

    private static int integer(String input) {
        if (input == null || !input.matches("0|[1-9][0-9]{0,9}")) throw ApiValues.invalid();
        return Integer.parseInt(input);
    }
}
