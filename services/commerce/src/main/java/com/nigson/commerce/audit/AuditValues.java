package com.nigson.commerce.audit;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import com.nigson.shared.problem.ApiValues;

/** Closed, bounded API-134 filters. */
public final class AuditValues {
    private AuditValues() {}

    public record Query(UUID resourceId, String action, Instant from, Instant to,
                        int page, int size, String sort) {
        public Query {
            ApiValues.page(page, size);
            if (action != null && !action.matches("[A-Z][A-Z0-9_]{0,99}")) throw ApiValues.invalid();
            if (from != null && to != null && from.isAfter(to)) throw ApiValues.invalid();
            ApiValues.sort(sort, Set.of("occurredAt,desc", "occurredAt,asc", "actor,asc", "actor,desc",
                    "action,asc", "action,desc", "resourceType,asc", "resourceType,desc"));
        }
    }

    public record Item(UUID id, String actor, String action, String resourceType, UUID resourceId,
                       Instant occurredAt, String reason, tools.jackson.databind.JsonNode oldValue,
                       tools.jackson.databind.JsonNode newValue) {}

    public record Page(java.util.List<Item> items, int page, int size, long totalElements, long totalPages) {}
}
