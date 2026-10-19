package com.nigson.experience.content;

import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class ContentKnowledgeValues {
    private ContentKnowledgeValues() {}

    public record RequestedRevision(UUID contentItemId, int revision) {}
    public record ValidationInput(List<RequestedRevision> items) {
        public ValidationInput { items = List.copyOf(items); }
    }
    public record ValidatedRevision(UUID contentItemId, int revision, boolean approved, boolean current) {}
    public record ValidationResult(List<ValidatedRevision> items) {
        public ValidationResult { items = List.copyOf(items); }
    }
    public record ContentKnowledge(UUID id, String type, String slug, String locale, int revision,
                                   String title, JsonNode body, JsonNode metadata, java.time.Instant publishedAt,
                                   boolean approved, String checksum, String visibility) {}
}
