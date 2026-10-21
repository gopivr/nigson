package com.nigson.experience.lead;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class LeadController {
    private final LeadService leads;
    public LeadController(LeadService leads){this.leads=leads;}
    private PrivateWorkload.Proof proof(){var a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!a.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(a.getPrincipal() instanceof PrivateWorkload.Proof p))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return p;}
    private static UUID correlation(){var w=WireContext.current();return w==null?WireContext.correlation(null):w.correlationId;}
    private static void query(HttpServletRequest r,Set<String> allowed){if(!allowed.containsAll(r.getParameterMap().keySet()))throw ApiValues.invalid();r.getParameterMap().forEach((k,v)->{if(v==null||v.length!=1||v[0]==null||v[0].length()>100)throw ApiValues.invalid();});}
    private static String one(HttpServletRequest r,String name,int max){var values=Collections.list(r.getHeaders(name));if(values.size()!=1||values.getFirst().isBlank()||values.getFirst().length()>max)throw ApiValues.invalid();return values.getFirst();}
    @PostMapping(value="/api/v1/enquiries",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LeadValues.Created> create(HttpServletRequest r,@RequestBody JsonNode body){query(r,Set.of());var created=leads.create(LeadValues.enquiry(body),one(r,"Idempotency-Key",100),r.getRemoteAddr(),correlation());return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(created);}
    @GetMapping(value="/api/v1/staff/leads",produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LeadValues.Page<LeadValues.Lead>> list(HttpServletRequest r,@RequestParam(required=false)String type,@RequestParam(required=false)String state,@RequestParam(required=false)String priority,@RequestParam(required=false)String assignedTo,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="createdAt,desc")String sort){query(r,Set.of("type","state","priority","assignedTo","page","size","sort"));var result=leads.list(proof(),type,state,priority,assignedTo==null?null:ApiValues.uuid(assignedTo),page,size,sort);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);}
    @GetMapping(value="/api/v1/staff/leads/{id}",produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LeadValues.Detail> detail(HttpServletRequest r,@PathVariable String id){query(r,Set.of());if(r.getContentLengthLong()>0)throw ApiValues.invalid();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(leads.detail(proof(),ApiValues.uuid(id)));}
    @PatchMapping(value="/api/v1/staff/leads/{id}",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LeadValues.Lead> update(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body){query(r,Set.of());String raw=one(r,"If-Match",32);if(!raw.matches("\"(0|[1-9][0-9]{0,18})\""))throw ApiValues.invalid();long version;try{version=Long.parseLong(raw.substring(1,raw.length()-1));}catch(RuntimeException invalid){throw ApiValues.invalid();}var updated=leads.update(proof(),ApiValues.uuid(id),LeadValues.patch(body),version,one(r,"Idempotency-Key",100),correlation());return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(Long.toString(updated.version())).body(updated);}
    @GetMapping(value="/internal/v1/leads/{id}/notification-context",produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LeadValues.NotificationContext> notificationContext(HttpServletRequest r,@PathVariable String id){query(r,Set.of());if(r.getContentLengthLong()>0)throw ApiValues.invalid();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(leads.notificationContext(proof(),ApiValues.uuid(id)));}
}
