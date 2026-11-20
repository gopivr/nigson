package com.nigson.commerce.affiliate;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.problem.*;

@RestController @ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class AffiliateEarningsController {
 private final AffiliateLinks links;private final AffiliateCommissions commissions;
 public AffiliateEarningsController(AffiliateLinks links,AffiliateCommissions commissions){this.links=links;this.commissions=commissions;}
 @GetMapping("/api/v1/me/affiliate/earnings")public ResponseEntity<AffiliateCommissions.Earnings> earnings(HttpServletRequest request)throws java.io.IOException{
  var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
  if(!Set.of("ACTIVE","SUSPENDED").contains(links.status(actor).state()))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
  if(!Set.of("page").containsAll(request.getParameterMap().keySet())||request.getInputStream().read()!=-1)throw ApiValues.invalid();
  String[] values=request.getParameterValues("page");if(values!=null&&(values.length!=1||!values[0].matches("0|[1-9][0-9]{0,4}")))throw ApiValues.invalid();int page=values==null?0:Integer.parseInt(values[0]);
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(commissions.earnings(actor.party(),page));
 }
}
