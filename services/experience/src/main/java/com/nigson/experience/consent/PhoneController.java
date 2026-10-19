package com.nigson.experience.consent;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.security.PrivateWorkload;
import com.nigson.shared.problem.*;
import com.nigson.shared.observability.WireContext;
@RestController
@ConditionalOnProperty(name="NIGSON_PHONE_CONSENT_ENABLED",havingValue="true")
public final class PhoneController {
 private final PhoneConsent consent;private final org.springframework.beans.factory.ObjectProvider<NewsletterService> newsletter;public PhoneController(PhoneConsent consent,org.springframework.beans.factory.ObjectProvider<NewsletterService> newsletter){this.consent=consent;this.newsletter=newsletter;}
 private PrivateWorkload.Proof proof(String api,HttpServletRequest r){if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof PrivateWorkload.Proof p))throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!p.route().api().equals(api)||!p.route().owner().equals("experience"))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return p;}
 private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
 private String key(HttpServletRequest r){var values=Collections.list(r.getHeaders("Idempotency-Key"));if(values.size()!=1)throw ApiValues.invalid();return ApiValues.idempotencyKey(values.getFirst());}
 private long version(JsonNode input,Set<String> fields){if(input==null||!input.isObject()||!input.propertyNames().equals(fields)||!input.path("version").isIntegralNumber()||!input.path("version").canConvertToLong()||input.path("version").asLong()<0)throw ApiValues.invalid();return input.path("version").asLong();}
 @PostMapping(value="/internal/v1/communication-destinations",consumes="application/json",produces="application/json")public ResponseEntity<PhoneValues.Destination> create(HttpServletRequest r,@RequestBody JsonNode input){var actor=proof("API-201",r).actor();var result=consent.create(actor,PhoneValues.input(input),key(r),correlation());return ResponseEntity.status(201).eTag(Long.toString(result.version())).body(result);}
 @PostMapping(value="/internal/v1/communication-destinations/{id}/verification",consumes="application/json",produces="application/json")public ResponseEntity<PhoneValues.Destination> confirm(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input){var actor=proof("API-202",r).actor();long version=version(input,Set.of("version","code"));if(!input.path("code").isString())throw ApiValues.invalid();var result=consent.confirm(actor,ApiValues.uuid(id),version,input.path("code").asString(),key(r),correlation());return ResponseEntity.ok().eTag(Long.toString(result.version())).body(result);}
 @GetMapping(value="/internal/v1/communication-destinations",produces="application/json")public List<PhoneValues.Destination> list(HttpServletRequest r){if(r.getContentLengthLong()>0||hasBody(r))throw ApiValues.invalid();return consent.list(proof("API-203",r).actor());}
 @PostMapping(value="/internal/v1/communication-destinations/{id}/withdrawal",consumes="application/json")public ResponseEntity<Void> withdraw(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input){var actor=proof("API-204",r).actor();consent.withdraw(actor,ApiValues.uuid(id),version(input,Set.of("version")),key(r),correlation());return ResponseEntity.noContent().build();}
 @PostMapping(value="/internal/v1/communication-verifications/{id}/notification-context",consumes="application/json",produces="application/json")public PhoneValues.Context context(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input){var p=proof("API-187",r);if(input==null||!input.isObject()||!input.propertyNames().equals(Set.of("jobId"))||!input.path("jobId").isString())throw ApiValues.invalid();return consent.context(p,ApiValues.uuid(id),ApiValues.uuid(input.path("jobId").asString()));}
 private boolean hasBody(HttpServletRequest r){try{return r.getInputStream().readNBytes(1).length>0;}catch(java.io.IOException invalid){throw ApiValues.invalid();}}
 @PostMapping(value="/internal/v1/communication-eligibility",consumes="application/json",produces="application/json")public PhoneValues.Eligibility eligibility(HttpServletRequest r,@RequestBody JsonNode input){var p=proof("API-127",r);var parsed=PhoneValues.eligibility(input);if("EMAIL".equals(parsed.channel())){var service=newsletter.getIfAvailable();if(service==null)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);return service.emailEligibility(p,parsed,correlation());}return consent.eligibility(p,parsed);}
 @PostMapping(value="/internal/v1/communication-suppressions",consumes="application/json",produces="application/json")public Map<String,Boolean> suppress(HttpServletRequest r,@RequestBody JsonNode input){var p=proof("API-175",r);return Map.of("acknowledged",consent.suppress(p,PhoneValues.signal(input),correlation()));}
}
