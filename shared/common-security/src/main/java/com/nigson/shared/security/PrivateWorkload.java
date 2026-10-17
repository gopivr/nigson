package com.nigson.shared.security;

import java.time.Clock;
import java.util.*;
import java.util.regex.Pattern;
import java.security.interfaces.ECPublicKey;
import org.springframework.security.oauth2.jwt.*;

/** Compiled operation registry and wire identity only; each owner retains object authorization. */
public final class PrivateWorkload {
    public record Route(String api,String method,String path,String owner,String client,String scope,int phase) {}
    public record Proof(String client,String subject,Route route,ActorAssertion.Actor actor) {
        @Override public String toString(){return "PrivateProof[protected]";}
    }
    public static final List<Route> ROUTES;
    static {try(var in=PrivateWorkload.class.getResourceAsStream("/security/workload-scopes.json")){ROUTES=List.of(new tools.jackson.databind.json.JsonMapper().readValue(in,Route[].class));}catch(Exception e){throw new ExceptionInInitializerError("PRIVATE_CONTRACT_UNAVAILABLE");}}
    private final String owner;private final int phase;private final PrivateIngress ingress;
    private final Map<String,JwtDecoder> decoders;private final Map<String,ECPublicKey> actorKeys;private final Set<String> identityIssuers;private final Clock clock;
    public PrivateWorkload(String owner,int phase,PrivateIngress ingress,java.util.function.Function<Route,JwtDecoder> factory,Map<String,ECPublicKey> actorKeys,Set<String> identityIssuers,Clock clock) {
        if(!Set.of("commerce","experience","integration","ai").contains(owner)||phase<1||phase>5)throw new IllegalArgumentException("PRIVATE_POLICY_REQUIRED");
        this.owner=owner;this.phase=phase;this.ingress=ingress;this.actorKeys=Map.copyOf(actorKeys);this.identityIssuers=Set.copyOf(identityIssuers);this.clock=clock;
        var d=new HashMap<String,JwtDecoder>();ROUTES.stream().filter(r->r.owner().equals(owner)&&r.phase()<=phase).forEach(r->d.computeIfAbsent(r.api(),k->factory.apply(r)));decoders=Map.copyOf(d);
    }
    public Route match(String method,String path) {
        if(path==null||path.contains("%")||path.contains("\\")||path.contains(";")||path.contains("//")||path.contains("..")||path.length()>512)throw ActorAssertion.denied();
        return ROUTES.stream().filter(r->r.owner().equals(owner)&&r.phase()<=phase&&r.method().equals(method)&&matches(r.path(),path)).findFirst().orElseThrow(ActorAssertion::denied);
    }
    private static boolean matches(String template,String path) {
        // Explicit segment construction avoids URL decoding and regular-expression input from requests.
        var regex=new StringBuilder("^");for(String segment:template.split("/",-1)){if(regex.length()>1)regex.append('/');if(segment.startsWith("{")&&segment.endsWith("}"))regex.append("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");else regex.append(Pattern.quote(segment));}regex.append('$');
        if(!path.matches(regex.toString()))return false;String[] values=path.split("/",-1),segments=template.split("/",-1);for(int i=0;i<segments.length;i++)if(segments[i].startsWith("{")&&values[i].equals("00000000-0000-0000-0000-000000000000"))return false;return true;
    }
    public Proof verify(String socketPeer,String method,String path,String authorization,String actorToken,UUID correlation) {
        return verify(socketPeer,method,path,authorization,actorToken,correlation,ActorAssertion.Binding.bodyHash(new byte[0]));
    }
    public Proof verify(String socketPeer,String method,String path,String authorization,String actorToken,UUID correlation,String bodyHash) {
        if(!ingress.permits(socketPeer)||authorization==null||!authorization.startsWith("Bearer ")||authorization.length()>16400)throw ActorAssertion.denied();
        Route route=match(method,path);Jwt jwt;try{jwt=decoders.get(route.api()).decode(authorization.substring(7));}catch(Exception e){throw ActorAssertion.denied();}
        if(!route.client().equals(jwt.getClaimAsString("azp"))||jwt.getClaimAsString("scope")==null||!new HashSet<>(List.of(jwt.getClaimAsString("scope").split(" "))).contains(route.scope()))throw ActorAssertion.denied();
        ActorAssertion.Actor actor=null;
        if(route.scope().contains("_WITH_SIGNED_")) {
            actor=ActorAssertion.verify(actorToken,new ActorAssertion.Binding(owner,route.scope(),method,path,correlation,bodyHash),actorKeys,identityIssuers,clock);
            if(!actor.scopes().contains(route.scope()))throw ActorAssertion.denied();
            if(route.scope().endsWith("_WITH_SIGNED_CUSTOMER")&&(!actor.roles().contains("CUSTOMER")||actor.organization()!=null))throw ActorAssertion.denied();
            if(route.scope().endsWith("_WITH_SIGNED_ADMIN")){if(!actor.roles().contains("ADMIN"))throw ActorAssertion.denied();ActorAssertion.recentMfa(actor,clock);}
            if(route.scope().endsWith("_WITH_SIGNED_STAFF")){if(!actor.roles().contains("OPERATIONS"))throw ActorAssertion.denied();ActorAssertion.recentMfa(actor,clock);}
        } else if(actorToken!=null)throw ActorAssertion.denied();
        return new Proof(route.client(),jwt.getSubject(),route,actor);
    }
}
