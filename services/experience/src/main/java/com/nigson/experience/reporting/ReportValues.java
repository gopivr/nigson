package com.nigson.experience.reporting;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.nigson.shared.problem.ApiValues;

/** API-110 UTC window and sanitized aggregate DTOs. */
public final class ReportValues {
    private ReportValues() {}
    public record Query(Instant from, Instant to) {
        public Query {
            if (from == null || to == null || !from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0) throw ApiValues.invalid();
        }
    }
    public record Campaign(java.util.UUID id, long entries) {}
    public record Summary(Map<String,Long> leadsByState, Map<String,Long> applicationsByState,
                          Map<String,Long> reviewsByState, long subscriptions, List<Campaign> campaigns) {
        public Summary {
            leadsByState = Map.copyOf(leadsByState); applicationsByState = Map.copyOf(applicationsByState);
            reviewsByState = Map.copyOf(reviewsByState); campaigns = List.copyOf(campaigns);
        }
    }
}
