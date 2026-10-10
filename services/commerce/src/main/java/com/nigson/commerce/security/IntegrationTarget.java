package com.nigson.commerce.security;
import java.net.URI;
import java.util.Set;
/** Explicit private integration endpoint policy shared by compiled commerce proxies. */
public final class IntegrationTarget {
    private IntegrationTarget(){}
    public static URI of(String env,String input){
        if(!Set.of("local","test","dev","qa","stage","prod").contains(env))throw new IllegalArgumentException("INTEGRATION_TARGET_REQUIRED");
        try{URI uri=URI.create(input);boolean local=Set.of("local","test").contains(env);
            if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getFragment()!=null||!Set.of("","/").contains(uri.getRawPath())||uri.getPort()==0||uri.getPort()<-1||uri.getPort()>65535||!(local?Set.of("http","https").contains(uri.getScheme())&&Set.of("localhost","127.0.0.1","integration","integration-mock").contains(uri.getHost()):"https".equals(uri.getScheme())&&uri.getHost().matches("[a-z0-9]+(?:[.-][a-z0-9]+)*\\.internal")))throw new IllegalArgumentException();
            return URI.create(uri.getScheme()+"://"+uri.getRawAuthority());
        }catch(RuntimeException invalid){throw new IllegalArgumentException("INTEGRATION_TARGET_REQUIRED");}
    }
}
