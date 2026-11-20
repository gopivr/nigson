package com.nigson.commerce.affiliate;

import java.util.*;
import java.time.*;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.observability.WireContext;

@RestController @ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class AffiliateLinkController {
 private final AffiliateLinks links;private final boolean insecure;
 public AffiliateLinkController(AffiliateLinks links,@Value("${APP_ENV}")String env,@Value("${NIGSON_INSECURE_LOCAL_COOKIE:false}")boolean insecure){if(insecure&&!env.equals("local"))throw new IllegalArgumentException("INSECURE_COOKIE_LOCAL_ONLY");this.links=links;this.insecure=insecure;}
 public static String cookieName(boolean insecure){return insecure?"nigson-local-affiliate":"__Host-nigson-affiliate";}
 public static UUID cookie(HttpServletRequest request,boolean insecure){UUID found=null;int count=0;if(request.getCookies()!=null)for(var c:request.getCookies())if(cookieName(insecure).equals(c.getName())){count++;try{found=UUID.fromString(c.getValue());}catch(Exception invalid){return null;}}return count==1?found:null;}
 private void clean(HttpServletRequest r)throws java.io.IOException{if(!r.getParameterMap().isEmpty()||r.getInputStream().read()!=-1)throw ApiValues.invalid();}
 private ActorAssertion.Actor actor(){var a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!a.isAuthenticated()||!(a.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);return actor;}
 @GetMapping("/api/v1/me/affiliate")public ResponseEntity<AffiliateLinks.Status> status(HttpServletRequest r)throws java.io.IOException{clean(r);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(links.status(actor()));}
 @PutMapping("/api/v1/me/affiliate/links/{id}")public ResponseEntity<AffiliateLinks.Link> link(HttpServletRequest r,@PathVariable String id)throws java.io.IOException{clean(r);var keys=Collections.list(r.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw ApiValues.invalid();var w=WireContext.current();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(links.link(actor(),ApiValues.uuid(id),keys.getFirst(),w==null?WireContext.correlation(null):w.correlationId));}
 @GetMapping("/api/v1/affiliate-links/{code}")public ResponseEntity<Void> visit(HttpServletRequest r,@PathVariable String code)throws java.io.IOException{clean(r);var visit=links.visit(code,cookie(r,insecure));var response=ResponseEntity.status(302).header("Location","/products/"+visit.productId()).header("Referrer-Policy","no-referrer").cacheControl(CacheControl.noStore());if(visit.attributed())response.header(HttpHeaders.SET_COOKIE,ResponseCookie.from(cookieName(insecure),visit.id().toString()).httpOnly(true).secure(!insecure).sameSite("Lax").path("/").maxAge(Duration.ofDays(7)).build().toString());return response.build();}
}
