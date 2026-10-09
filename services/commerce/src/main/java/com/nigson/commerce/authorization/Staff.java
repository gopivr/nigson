package com.nigson.commerce.authorization;

import java.util.*;
import com.nigson.shared.problem.ApiValues;

/** Public values; never includes identity subjects, sessions or persistence records. */
public final class Staff {
    private Staff() {}
    public enum Role { ADMIN, CONTENT_EDITOR, CONTENT_PUBLISHER, SALES, OPERATIONS, FINANCE, ANALYST }
    public record View(UUID id, String displayName, String email, List<Role> roles, long version) {
        public View { roles = List.copyOf(roles); }
    }
    public record Page(List<View> items, int page, int size, long totalElements, long totalPages) {
        public Page { items = List.copyOf(items); }
    }
    public record Change(List<Role> roles, String reason) {
        @com.fasterxml.jackson.annotation.JsonCreator(mode=com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
        public static Change decode(tools.jackson.databind.JsonNode input) {
            if (input == null || !input.isObject() || input.size() != 2
                    || !input.has("roles") || !input.has("reason")
                    || !input.get("roles").isArray() || !input.get("reason").isString()
                    || input.get("roles").size() > Role.values().length) throw ApiValues.invalid();
            var roles = new ArrayList<Role>();
            for (var value : input.get("roles")) {
                if (!value.isString()) throw ApiValues.invalid();
                try { roles.add(Role.valueOf(value.asString())); }
                catch (IllegalArgumentException invalid) { throw ApiValues.invalid(); }
            }
            return new Change(roles, input.get("reason").asString());
        }
        public Change {
            if (roles == null || roles.size() > Role.values().length || roles.stream().anyMatch(Objects::isNull)
                    || new HashSet<>(roles).size() != roles.size() || reason == null
                    || reason.strip().length() < 10 || reason.length() > 500
                    || reason.codePoints().anyMatch(Character::isISOControl)) throw ApiValues.invalid();
            roles = roles.stream().sorted().toList();
        }
    }
}
