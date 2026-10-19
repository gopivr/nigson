package com.nigson.experience.engagement;

import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
public final class EngagementController {
 private final EngagementService service;public EngagementController(EngagementService service){this.service=service;}
 private PrivateWorkload.Proof proof(){var a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!a.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(a.getPrincipal() instanceof PrivateWorkload.Proof p))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return p;}
 private static UUID correlation(){var w=WireContext.current();return w==null?WireContext.correlation(null):w.correlationId;}
 private static String header(HttpServletRequest r,String name){var v=Collections.list(r.getHeaders(name));if(v.size()!=1||v.getFirst().isBlank()||v.getFirst().length()>100)throw ApiValues.invalid();return v.getFirst();}
 private static void query(HttpServletRequest r,Set<String> allowed){var q=new HashMap<String,String>();r.getParameterMap().forEach((k,v)->{if(v==null||v.length!=1)throw ApiValues.invalid();q.put(k,v[0]);});EngagementValues.query(q,allowed);}
 private static Integer integer(String s,int fallback){if(s==null)return fallback;try{return Integer.valueOf(s);}catch(Exception e){throw ApiValues.invalid();}}
 @PostMapping(value="/api/v1/reviews",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<EngagementValues.Review> create(HttpServletRequest r,@RequestBody JsonNode body){query(r,Set.of());var value=service.create(proof(),EngagementValues.review(body),ApiValues.idempotencyKey(header(r,"Idempotency-Key")),correlation());return ResponseEntity.status(201).header("Cache-Control","no-store").body(value);}
 @GetMapping(value="/api/v1/reviews",produces=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<EngagementValues.Page<EngagementValues.PublicReview>> reviews(HttpServletRequest r,@RequestParam(required=false)String productId,@RequestParam(required=false)String kind,@RequestParam(defaultValue="0")String page,@RequestParam(defaultValue="20")String size,@RequestParam(defaultValue="publishedAt,desc")String sort){if(r.getContentLengthLong()>0)throw ApiValues.invalid();query(r,Set.of("productId","kind","page","size","sort"));return ResponseEntity.ok().header("Cache-Control","public, max-age=30").body(service.publicReviews(productId==null?null:ApiValues.uuid(productId),kind,integer(page,0),integer(size,20),sort));}
 @GetMapping(value="/api/v1/staff/reviews",produces=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<EngagementValues.Page<EngagementValues.AdminReview>> queue(HttpServletRequest r,@RequestParam(required=false)String state,@RequestParam(required=false)String kind,@RequestParam(defaultValue="0")String page,@RequestParam(defaultValue="20")String size,@RequestParam(defaultValue="createdAt,desc")String sort){if(r.getContentLengthLong()>0)throw ApiValues.invalid();query(r,Set.of("state","kind","page","size","sort"));return ResponseEntity.ok().header("Cache-Control","no-store").body(service.queue(proof(),state,kind,integer(page,0),integer(size,20),sort));}
 @PostMapping(value="/api/v1/staff/reviews/{id}/moderation",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<EngagementValues.Review> moderate(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode body){query(r,Set.of());String m=header(r,"If-Match");if(!m.matches("\"[0-9]+\""))throw ApiValues.invalid();long version;try{version=Long.parseLong(m.substring(1,m.length()-1));}catch(Exception e){throw ApiValues.invalid();}var value=service.moderate(proof(),ApiValues.uuid(id),EngagementValues.moderation(body),version,ApiValues.idempotencyKey(header(r,"Idempotency-Key")),correlation());return ResponseEntity.ok().header("Cache-Control","no-store").header("ETag","\""+value.version()+"\"").body(value);}
 @PostMapping(value="/api/v1/staff/campaigns",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<EngagementValues.Campaign> campaign(HttpServletRequest r,@RequestBody JsonNode body){query(r,Set.of());var value=service.createCampaign(proof(),EngagementValues.campaign(body),ApiValues.idempotencyKey(header(r,"Idempotency-Key")),correlation());return ResponseEntity.status(201).header("Cache-Control","no-store").body(value);}
 @GetMapping(value="/api/v1/campaigns",produces=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<List<EngagementValues.PublicCampaign>> campaigns(HttpServletRequest r,@RequestParam String market){if(r.getContentLengthLong()>0)throw ApiValues.invalid();query(r,Set.of("market"));return ResponseEntity.ok().header("Cache-Control","public, max-age=15").body(service.publicCampaigns(market));}
}
