package com.nigson.commerce.session;
import com.nigson.commerce.security.*;import com.nigson.shared.problem.*;import com.nigson.shared.security.ActorAssertion;
import jakarta.servlet.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import tools.jackson.databind.JsonNode;
import java.util.*;
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public class SessionController {
 private final SessionStore sessions;private final SessionLifecycle lifecycle;private final SessionCookies cookies;private final OidcTrust oidc;private final SessionService service;private final byte[] abuseKey;
 public SessionController(SessionStore sessions,SessionLifecycle lifecycle,SessionCookies cookies,OidcTrust oidc,SessionService service,@org.springframework.beans.factory.annotation.Value("${NIGSON_BFF_CLIENT_SECRET}")String secret){this.sessions=sessions;this.lifecycle=lifecycle;this.cookies=cookies;this.oidc=oidc;this.service=service;this.abuseKey=secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
 private void query(HttpServletRequest r,Set<String> allowed){for(var e:r.getParameterMap().entrySet())if(!allowed.contains(e.getKey())||e.getValue().length!=1||e.getValue()[0].isBlank()||e.getValue()[0].length()>200)throw ApiValues.invalid();}
 private void noBody(HttpServletRequest r)throws java.io.IOException{if(r.getInputStream().read()!=-1)throw ApiValues.invalid();}
 private String required(HttpServletRequest r,String name,int max){String value=r.getParameter(name);if(value==null||value.isBlank()||value.length()>max||value.codePoints().anyMatch(Character::isISOControl))throw ApiValues.invalid();return value;}
 @GetMapping("/bff/login")public ResponseEntity<Void> login(HttpServletRequest r,HttpServletResponse s)throws java.io.IOException{
  query(r,Set.of("returnTo"));noBody(r);String target=r.getParameter("returnTo");if(target==null)target="/account";sessions.requireReturnPath(target);String existing=cookies.read(r);if(existing!=null)sessions.logout(existing);var flow=sessions.begin(target,AuditValues.ipFingerprint(abuseKey,r.getRemoteAddr()));cookies.write(r,s,flow.sessionId());return ResponseEntity.status(302).location(oidc.authorization(flow.flow(),false)).build();
 }
 @GetMapping("/bff/callback")public ResponseEntity<Void> callback(HttpServletRequest r,HttpServletResponse s)throws java.io.IOException{
  query(r,Set.of("code","state"));noBody(r);String code=required(r,"code",200),state=required(r,"state",43);if(!state.matches("[A-Za-z0-9_-]{43}"))throw ApiValues.invalid();String id=cookies.read(r);if(id==null)throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
  try{var result=lifecycle.completeCallback(id,state,code);cookies.write(r,s,result.sessionId());return ResponseEntity.status(302).location(java.net.URI.create(result.returnTo())).build();}catch(org.springframework.security.access.AccessDeniedException failed){cookies.clear(r,s);throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);}
 }
 @GetMapping(value="/bff/session",produces="application/json")public SessionView view(HttpServletRequest r)throws java.io.IOException{query(r,Set.of());noBody(r);return service.view(cookies.read(r));}
 @PostMapping(value="/bff/logout",consumes="application/json")public ResponseEntity<Void> logout(HttpServletRequest r,HttpServletResponse s,@RequestBody JsonNode input){query(r,Set.of());if(!input.isObject()||!input.isEmpty())throw ApiValues.invalid();String id=cookies.read(r);if(id==null)throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);var headers=Collections.list(r.getHeaders("X-CSRF-TOKEN"));if(headers.size()!=1)throw ApiValues.invalid();try{service.logout(id,headers.getFirst());}finally{cookies.clear(r,s);}return ResponseEntity.noContent().build();}
}
