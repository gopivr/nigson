package com.nigson.commerce.party;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.observability.WireContext;
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public class PartyController {
    private final PartyService service;public PartyController(PartyService service){this.service=service;}
    private ActorAssertion.Actor actor(HttpServletRequest request){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor a))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);if(!request.getParameterMap().isEmpty())throw ApiValues.invalid();return a;}
    private void noBody(HttpServletRequest request)throws java.io.IOException{if(request.getInputStream().read()!=-1)throw ApiValues.invalid();}
    private String header(HttpServletRequest request,String name){var values=Collections.list(request.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
    private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
    @GetMapping(value="/api/v1/me",produces="application/json")public PartyValues.Profile profile(HttpServletRequest r)throws java.io.IOException{var a=actor(r);noBody(r);return service.profile(a);}
    @PatchMapping(value="/api/v1/me",consumes="application/json",produces="application/json")public ResponseEntity<PartyValues.Profile> patchProfile(HttpServletRequest r,@RequestBody JsonNode input){var p=service.patchProfile(actor(r),ApiValues.version(header(r,"If-Match")),input,header(r,"Idempotency-Key"),correlation());return ResponseEntity.ok().eTag(Long.toString(p.version())).body(p);}
    @GetMapping(value="/api/v1/me/addresses",produces="application/json")public List<PartyValues.Address> addresses(HttpServletRequest r)throws java.io.IOException{var a=actor(r);noBody(r);return service.addresses(a);}
    @GetMapping(value="/api/v1/me/organizations",produces="application/json")public List<PartyValues.Organization> organizations(HttpServletRequest r)throws java.io.IOException{var a=actor(r);noBody(r);return service.organizations(a);}
    @PostMapping(value="/api/v1/me/addresses",consumes="application/json",produces="application/json")public ResponseEntity<PartyValues.Address> create(HttpServletRequest r,@RequestBody JsonNode input){var a=service.createAddress(actor(r),input,header(r,"Idempotency-Key"),correlation());return ResponseEntity.created(java.net.URI.create("/api/v1/me/addresses/"+a.id())).eTag(Long.toString(a.version())).body(a);}
    @PatchMapping(value="/api/v1/me/addresses/{id}",consumes="application/json",produces="application/json")public ResponseEntity<PartyValues.Address> patch(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input){var a=service.patchAddress(actor(r),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),input,header(r,"Idempotency-Key"),correlation());return ResponseEntity.ok().eTag(Long.toString(a.version())).body(a);}
    @DeleteMapping("/api/v1/me/addresses/{id}")public ResponseEntity<Void> delete(HttpServletRequest r,@PathVariable String id)throws java.io.IOException{var a=actor(r);noBody(r);service.deleteAddress(a,ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),header(r,"Idempotency-Key"),correlation());return ResponseEntity.noContent().build();}
}
