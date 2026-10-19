package com.nigson.commerce.quote;

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
@ConditionalOnProperty(name="NIGSON_QUOTES_ENABLED",havingValue="true")
public final class QuoteSkuEligibilityController {
 private final QuoteSkuEligibilityService service;public QuoteSkuEligibilityController(QuoteSkuEligibilityService service){this.service=service;}
 @PostMapping(value="/internal/v1/quote-sku-eligibility",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
 public ResponseEntity<QuoteSkuEligibilityService.Result> validate(HttpServletRequest request,@RequestBody JsonNode body){if(!request.getParameterMap().isEmpty())throw ApiValues.invalid();var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);if(body==null||!body.isObject()||!body.propertyNames().equals(Set.of("partyId","marketId","organizationId","items"))||!body.path("partyId").isString()||!body.path("marketId").isString()||!body.path("items").isArray())throw ApiValues.invalid();UUID org=body.path("organizationId").isNull()?null:uuid(body.path("organizationId"));var items=new ArrayList<QuoteSkuEligibilityService.Item>();for(JsonNode item:body.path("items")){if(!item.isObject()||!item.propertyNames().equals(Set.of("skuId","quantity"))||!item.path("skuId").isString()||!item.path("quantity").isString())throw ApiValues.invalid();items.add(new QuoteSkuEligibilityService.Item(uuid(item.path("skuId")),item.path("quantity").asString()));}var input=new QuoteSkuEligibilityService.Input(uuid(body.path("partyId")),uuid(body.path("marketId")),org,items);return ResponseEntity.ok().header("Cache-Control","no-store").body(service.validate(proof,input));}
 private static UUID uuid(JsonNode node){try{UUID id=UUID.fromString(node.asString());if(id.equals(new UUID(0,0)))throw ApiValues.invalid();return id;}catch(IllegalArgumentException bad){throw ApiValues.invalid();}}
}
