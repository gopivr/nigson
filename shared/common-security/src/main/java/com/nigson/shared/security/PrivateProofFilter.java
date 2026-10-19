package com.nigson.shared.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Shared private wire boundary only; safe error rendering and ownership remain owner supplied. */
public final class PrivateProofFilter extends OncePerRequestFilter {
    public interface Denial { void write(HttpServletRequest request,HttpServletResponse response)throws IOException; }
    private final PrivateWorkload trust;private final Denial denied;
    public PrivateProofFilter(PrivateWorkload trust,Denial denied){this.trust=trust;this.denied=denied;}
    protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws IOException,ServletException {
        HttpServletRequest forwarded=r;
        try {
            if(r.getParameterMap().containsKey("access_token")||r.getParameterMap().containsKey("id_token")||r.getParameterMap().containsKey("refresh_token"))throw ActorAssertion.denied();
            boolean signedStaffFacade=false;
            if(!r.getRequestURI().startsWith("/internal/"))try{var route=trust.match(r.getMethod(),r.getRequestURI());signedStaffFacade=route.scope().contains("_WITH_SIGNED_");}catch(RuntimeException ignored){}
            if(r.getRequestURI().startsWith("/internal/")||signedStaffFacade){
                if(r.getHeader("Origin")!=null||r.getHeader("Cookie")!=null)throw ActorAssertion.denied();
                var context=com.nigson.shared.observability.WireContext.current();UUID correlation=context==null?com.nigson.shared.observability.WireContext.correlation(r.getHeader("X-Correlation-ID")):context.correlationId;
                var body=new PrivateBody(r);forwarded=body;var proof=trust.verify(r.getRemoteAddr(),r.getMethod(),r.getRequestURI(),single(r,"Authorization"),single(r,"X-Nigson-Actor"),correlation,body.hash());
                SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(proof,null,List.of()));
            } else if(r.getHeader("X-Nigson-Actor")!=null||r.getHeader("Authorization")!=null)throw ActorAssertion.denied();
        }catch(RuntimeException failure){denied.write(r,s);return;}
        chain.doFilter(forwarded,s);
    }
    private static String single(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()>1)throw ActorAssertion.denied();return values.isEmpty()?null:values.getFirst();}
}
