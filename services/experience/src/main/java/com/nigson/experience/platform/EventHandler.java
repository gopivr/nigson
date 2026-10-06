package com.nigson.experience.platform;
public interface EventHandler {
 String eventType();
 /** Local transaction only: persist a business-key unique effect/job, never call HTTP. */
 String handle(org.springframework.jdbc.core.JdbcTemplate jdbc,tools.jackson.databind.JsonNode event);
}
