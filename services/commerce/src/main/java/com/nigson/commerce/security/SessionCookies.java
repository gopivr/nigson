package com.nigson.commerce.security;

import java.util.*;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.CookieSerializer;
import jakarta.servlet.http.*;

public final class SessionCookies {
    private final DefaultCookieSerializer serializer=new DefaultCookieSerializer();private final String name;
    public SessionCookies(String environment,boolean insecureLocal) {
        if(insecureLocal&&!environment.equals("local"))throw new IllegalArgumentException("INSECURE_COOKIE_LOCAL_ONLY");
        name=insecureLocal?"nigson-local-session":"__Host-nigson-session";serializer.setCookieName(name);serializer.setCookiePath("/");serializer.setUseSecureCookie(!insecureLocal);serializer.setUseHttpOnlyCookie(true);serializer.setSameSite("Lax");serializer.setUseBase64Encoding(false);
    }
    public CookieSerializer serializer(){return serializer;}
    public String read(HttpServletRequest request){Cookie[] cookies=request.getCookies();if(cookies==null)return null;String id=null;for(Cookie c:cookies)if(c.getName().equals(name)){if(id!=null)throw com.nigson.shared.security.ActorAssertion.denied();id=c.getValue();}if(id!=null){try{if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();}catch(Exception e){throw com.nigson.shared.security.ActorAssertion.denied();}}return id;}
    public void write(HttpServletRequest request,HttpServletResponse response,String id){serializer.writeCookieValue(new CookieSerializer.CookieValue(request,response,id));}
    public void clear(HttpServletRequest request,HttpServletResponse response){var value=new CookieSerializer.CookieValue(request,response,"");value.setCookieMaxAge(0);serializer.writeCookieValue(value);}
}
