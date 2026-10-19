package com.nigson.commerce.cart;

import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class CartRecoveryController {
    private final CartRecoveryService service;
    public CartRecoveryController(CartRecoveryService service){this.service=service;}
    private PrivateWorkload.Proof proof(){var a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!a.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(a.getPrincipal() instanceof PrivateWorkload.Proof p))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return p;}
    @PostMapping(value="/internal/v1/cart-recovery/{id}/eligibility",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CartRecoveryValues.Eligibility> eligibility(HttpServletRequest request,@PathVariable String id,@RequestBody JsonNode body){
        if(!request.getParameterMap().isEmpty()||body==null||!body.isObject()||body.size()!=0)throw ApiValues.invalid();
        UUID correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.eligibility(proof(),ApiValues.uuid(id),correlation));
    }
}
