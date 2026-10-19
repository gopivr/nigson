package com.nigson.commerce.fulfillment;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.shared.problem.ApiValues;

/** API-160 is deliberately non-accepting until a provider signature contract is approved. */
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class CarrierWebhookController {
    @PostMapping("/api/v1/webhooks/logistics/{provider}")
    public ResponseEntity<Void> disabled(HttpServletRequest request, @PathVariable String provider) {
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) throw ApiValues.invalid();
        if (provider == null || !provider.matches("[A-Za-z][A-Za-z0-9_-]{0,39}")) throw ApiValues.invalid();
        throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);
    }
}
