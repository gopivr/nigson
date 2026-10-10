package com.nigson.commerce.platform;

import tools.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;

/** Owner modules register closed, non-secret engineering configuration contracts. */
public interface ConfigurationPolicy {
    String key();
    void validate(JsonNode value);
    void activate(JdbcTemplate jdbc, JsonNode value);
}
