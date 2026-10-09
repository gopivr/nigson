package com.nigson.commerce.security;
import java.net.URI;
import java.util.*;
import jakarta.servlet.http.*;
import com.nigson.shared.security.ActorAssertion;

/** Exact credentialed CORS. No wildcard, host suffix matching or request-selected destination. */
public final class BrowserBoundary {
    private final Set<String> origins;
    public BrowserBoundary(String environment,Collection<String> origins){
        if(origins.isEmpty()||origins.size()>16)throw new IllegalArgumentException("BFF_ORIGINS_REQUIRED");
        for(String s:origins){var u=URI.create(s);if(u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!u.getPath().isEmpty()||!"https".equals(u.getScheme())&&!(environment.equals("local")&&"http".equals(u.getScheme())&&Set.of("localhost","127.0.0.1").contains(u.getHost()))||!Set.of("local","test").contains(environment)&&(u.getHost().endsWith(".test")||u.getHost().endsWith(".example")||u.getHost().equals("localhost")))throw new IllegalArgumentException("BFF_ORIGIN_INVALID");}this.origins=Set.copyOf(origins);
    }
    public void check(HttpServletRequest req,HttpServletResponse res){
        var values=Collections.list(req.getHeaders("Origin"));if(values.size()>1)throw ActorAssertion.denied();String origin=values.isEmpty()?null:values.getFirst();if(origin==null)return;
        if(!origins.contains(origin)||req.getRequestURI().startsWith("/internal/"))throw ActorAssertion.denied();
        res.setHeader("Access-Control-Allow-Origin",origin);res.setHeader("Access-Control-Allow-Credentials","true");res.addHeader("Vary","Origin");
        if(req.getMethod().equals("OPTIONS")) {
            String method=req.getHeader("Access-Control-Request-Method");if(!Set.of("GET","POST","PUT","PATCH","DELETE").contains(method))throw ActorAssertion.denied();
            String headers=req.getHeader("Access-Control-Request-Headers");if(headers!=null){if(headers.length()>512)throw ActorAssertion.denied();for(String h:headers.split(","))if(!Set.of("content-type","x-csrf-token","x-correlation-id","if-match","idempotency-key").contains(h.trim().toLowerCase(Locale.ROOT)))throw ActorAssertion.denied();}
            res.setHeader("Access-Control-Allow-Methods","GET, POST, PUT, PATCH, DELETE");res.setHeader("Access-Control-Allow-Headers","Content-Type, X-CSRF-TOKEN, X-Correlation-ID, If-Match, Idempotency-Key");res.addHeader("Vary","Access-Control-Request-Method");res.addHeader("Vary","Access-Control-Request-Headers");
        }
    }
}
