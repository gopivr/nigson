package com.nigson.experience.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.nigson.shared.problem.ApiValues;
import tools.jackson.databind.JsonNode;

/** API-138 query and wire DTOs. The public AuditDTO shape is shared with API-134. */
public final class AuditValues {
    private AuditValues() {}
    public record Query(UUID resourceId, String action, Instant from, Instant to, int page, int size, String sort) {
        public Query {
            ApiValues.page(page, size);
            if (action != null && !action.matches("[A-Z][A-Z0-9_]{0,99}")) throw ApiValues.invalid();
            if ((from != null && to != null && (!from.isBefore(to) || Duration.between(from,to).compareTo(Duration.ofDays(366)) > 0))) throw ApiValues.invalid();
            ApiValues.sort(sort, Set.of("occurredAt,desc", "occurredAt,asc", "actor,asc", "actor,desc", "action,asc", "action,desc", "resourceType,asc", "resourceType,desc"));
        }
    }
    public record Item(UUID id, String actor, String action, String resourceType, UUID resourceId, Instant occurredAt,
                       String reason, JsonNode oldValue, JsonNode newValue) {}
    public record Page(List<Item> items, int page, int size, long totalElements, long totalPages) {
        public Page { items = List.copyOf(items); }
    }
}
