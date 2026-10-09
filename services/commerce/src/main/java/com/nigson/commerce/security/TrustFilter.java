package com.nigson.commerce.security;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.nigson.shared.security.*;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.commerce.platform.ProblemWriter;

/** Separates private workload proof from browser cookies. Domain routes still deny by default. */
public final class TrustFilter extends OncePerRequestFilter {
    private final PrivateWorkload privateTrust;private final JdbcSessionTrust sessions;private final SessionCookies cookies;private final CommerceAuthority authority;private final ProblemWriter problems;private final BrowserBoundary browser;
    public TrustFilter(PrivateWorkload privateTrust,JdbcSessionTrust sessions,SessionCookies cookies,CommerceAuthority authority,ProblemWriter problems,BrowserBoundary browser){this.privateTrust=privateTrust;this.sessions=sessions;this.cookies=cookies;this.authority=authority;this.problems=problems;this.browser=browser;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws IOException,ServletException {
        HttpServletRequest forwarded=req;
        try {
            browser.check(req,res);
            if(req.getParameterMap().containsKey("access_token")||req.getParameterMap().containsKey("id_token")||req.getParameterMap().containsKey("refresh_token"))throw ActorAssertion.denied();
            boolean internal=req.getRequestURI().startsWith("/internal/");
            if(req.getMethod().equals("OPTIONS")&&req.getHeader("Origin")!=null&&!internal&&(req.getRequestURI().startsWith("/bff/")||req.getRequestURI().startsWith("/api/v1/"))){if(single(req,"Authorization")!=null||single(req,"X-Nigson-Actor")!=null)throw ActorAssertion.denied();res.setStatus(204);return;}
            Object proof=null;
            if(internal){if(req.getCookies()!=null&&cookies.read(req)!=null)throw ActorAssertion.denied();String authorization=single(req,"Authorization"),actor=single(req,"X-Nigson-Actor");UUID correlation=com.nigson.shared.observability.WireContext.current()==null?com.nigson.shared.observability.WireContext.correlation(req.getHeader("X-Correlation-ID")):com.nigson.shared.observability.WireContext.current().correlationId;
                var body=new PrivateBody(req);forwarded=body;proof=privateTrust.verify(req.getRemoteAddr(),req.getMethod(),req.getRequestURI(),authorization,actor,correlation,body.hash());
            } else {
                if(single(req,"X-Nigson-Actor")!=null||single(req,"Authorization")!=null)throw ActorAssertion.denied();String id=cookies.read(req);
                // Login/callback/session public controllers (next task) explicitly invoke their own typed flow.
                if(id!=null&&!Set.of("/bff/login","/bff/callback","/bff/session").contains(req.getRequestURI())){
                    var record=sessions.read(id,true);if(Set.of("POST","PUT","PATCH","DELETE").contains(req.getMethod()))sessions.csrf(record,single(req,"X-CSRF-TOKEN"));proof=authority.resolve(record,null,Set.of());
                }
            }
            if(proof!=null)SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(proof,null,List.of()));
        }catch(RuntimeException denied){problems.write(req,res,ProblemCode.AUTH_REQUIRED,List.of());return;}
        chain.doFilter(forwarded,res);
    }
    private static String single(HttpServletRequest req,String name){var values=Collections.list(req.getHeaders(name));if(values.size()>1)throw ActorAssertion.denied();return values.isEmpty()?null:values.getFirst();}
}
