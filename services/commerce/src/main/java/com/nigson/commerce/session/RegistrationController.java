package com.nigson.commerce.session;

import com.nigson.shared.problem.ApiValues;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import java.util.Set;

/** Anonymous browser registration is available only when the local/test identity service is explicitly enabled. */
@RestController
@ConditionalOnProperty(name = {"NIGSON_SECURITY_ENABLED", "NIGSON_REGISTRATION_ENABLED"}, havingValue = "true")
public final class RegistrationController {
    private static final Set<String> FIELDS = Set.of("firstName", "lastName", "email", "password");
    private final RegistrationService registrations;
    public RegistrationController(RegistrationService registrations) { this.registrations = registrations; }

    @PostMapping(value = "/bff/register", consumes = "application/json")
    public ResponseEntity<Void> register(@RequestBody JsonNode body, HttpServletRequest request) {
        if (request.getContentLengthLong() < 1 || request.getContentLengthLong() > 8192
            || body == null || !body.isObject() || body.size() != FIELDS.size() || !FIELDS.containsAll(body.propertyNames())
            || com.nigson.shared.events.EventCodec.JSON.writeValueAsBytes(body).length > 8192) throw ApiValues.invalid();
        for (String field : FIELDS) if (!body.path(field).isString()) throw ApiValues.invalid();
        registrations.register(new RegistrationService.Input(body.path("firstName").asString(), body.path("lastName").asString(),
            body.path("email").asString(), body.path("password").asString()), request.getRemoteAddr());
        // Deliberately does not reveal whether the address already exists.
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }
}
