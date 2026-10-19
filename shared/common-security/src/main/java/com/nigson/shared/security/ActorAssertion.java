package com.nigson.shared.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.*;
import java.security.interfaces.ECPublicKey;
import java.time.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;

/** Bounded private wire proof. Domain ownership and current policy are checked by the owner. */
public final class ActorAssertion {
    public static final String ISSUER="nigson-commerce-bff";
    private ActorAssertion() {}
    public record Actor(UUID party,String identityIssuer,String identitySubject,UUID organization,
                        Set<String> roles,Set<String> scopes,Instant mfaAt) {
        public Actor {
            nonnil(party); if(organization!=null)nonnil(organization);
            if(identityIssuer==null||identityIssuer.length()>500||identitySubject==null||identitySubject.isBlank()||identitySubject.length()>200)throw denied();
            roles=Set.copyOf(roles);scopes=Set.copyOf(scopes);
            if(roles.size()>10||!Set.of("CUSTOMER","ADMIN","CONTENT_EDITOR","CONTENT_PUBLISHER","SALES","OPERATIONS","FINANCE","ANALYST").containsAll(roles)||scopes.size()>64||scopes.stream().anyMatch(s->!s.matches("[a-zA-Z0-9:._-]{1,100}")))throw denied();
        }
        @Override public String toString(){return "Actor[protected]";}
    }
    public record Binding(String audience,String operation,String method,String resource,UUID correlation,String bodyHash) {
        public Binding(String audience,String operation,String method,String resource,UUID correlation){this(audience,operation,method,resource,correlation,bodyHash(new byte[0]));}
        public static String bodyHash(byte[] body){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(body));}catch(Exception e){throw denied();}}
        public Binding {
            boolean facade=resource!=null&&(resource.startsWith("/api/v1/staff/")||resource.startsWith("/api/v1/experience/staff/")||resource.startsWith("/api/v1/me/")||resource.equals("/api/v1/distributor-applications")||resource.equals("/api/v1/quote-requests"));
            if(!Set.of("commerce","experience","integration","ai").contains(audience)||operation==null||!operation.matches("[A-Za-z0-9:._-]{1,100}")||!Set.of("GET","POST","PUT","PATCH","DELETE").contains(method)||resource==null||resource.length()>512||(!resource.startsWith("/internal/v1/")&&!facade)||resource.contains("%")||resource.contains("\\")||resource.contains(";")||resource.contains("?")||resource.contains("#")||resource.contains("//")||resource.contains(".."))throw denied();
            nonnil(correlation);if(bodyHash==null||!bodyHash.matches("[a-f0-9]{64}"))throw denied();
        }
    }
    public static JWTClaimsSet claims(Actor actor,Binding binding,Instant now) {
        var b=new JWTClaimsSet.Builder().issuer(ISSUER).audience(binding.audience()).subject(actor.party().toString())
            .claim("identity_issuer",actor.identityIssuer()).claim("identity_subject",actor.identitySubject())
            .claim("roles",actor.roles().stream().sorted().toList()).claim("scopes",actor.scopes().stream().sorted().toList())
            .claim("operation",binding.operation()).claim("http_method",binding.method()).claim("resource",binding.resource())
            .claim("body_sha256",binding.bodyHash()).claim("correlation_id",binding.correlation().toString()).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(60))).jwtID(UUID.randomUUID().toString());
        if(actor.organization()!=null)b.claim("organization_id",actor.organization().toString());
        if(actor.mfaAt()!=null)b.claim("mfa_at",actor.mfaAt().getEpochSecond());return b.build();
    }
    public static Actor verify(String token,Binding binding,Map<String,ECPublicKey> pinnedKeys,Set<String> identityIssuers,Clock clock) {
        try {
            if(token==null||token.length()>8192||!token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")||pinnedKeys.isEmpty()||pinnedKeys.size()>4)throw denied();
            var json=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(8192).maxNumberLength(20).build()).build()).build();
            var parts=token.split("\\.");var h=json.readTree(Base64.getUrlDecoder().decode(parts[0]));var c=json.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if(!h.isObject()||h.size()!=3||!h.has("alg")||!h.has("kid")||!h.has("typ")||!c.isObject()||c.size()>18)throw denied();
            for(String field:List.of("iss","sub","identity_issuer","identity_subject","operation","http_method","resource","body_sha256","correlation_id","jti"))if(!c.has(field)||!c.get(field).isString())throw denied();
            for(String field:List.of("iat","exp"))if(!c.has(field)||(!c.get(field).isIntegralNumber()||!c.get(field).canConvertToLong()))throw denied();
            if(c.has("mfa_at")&&!c.get("mfa_at").isIntegralNumber()||c.has("organization_id")&&!c.get("organization_id").isString())throw denied();
            for(String field:List.of("roles","scopes")){if(!c.has(field)||!c.get(field).isArray())throw denied();for(var item:c.get(field))if(!item.isString())throw denied();}
            var jwt=SignedJWT.parse(token);var header=jwt.getHeader();var key=pinnedKeys.get(header.getKeyID());
            if(!JWSAlgorithm.ES256.equals(header.getAlgorithm())||!new JOSEObjectType("nigson-actor+jwt").equals(header.getType())||header.getCriticalParams()!=null||key==null||!ActorPublicKeys.p256(key)||!jwt.verify(new ECDSAVerifier(key)))throw denied();
            var claims=jwt.getJWTClaimsSet();Instant now=clock.instant(),issued=claims.getIssueTime().toInstant(),expiry=claims.getExpirationTime().toInstant();
            // No expiration grace on the 60-second delegation. Future issuance permits <=5s clock skew.
            if(!ISSUER.equals(claims.getIssuer())||!claims.getAudience().equals(List.of(binding.audience()))||!issued.isBefore(expiry)||expiry.isAfter(issued.plusSeconds(60))||!now.isBefore(expiry)||issued.isAfter(now.plusSeconds(5))||!binding.operation().equals(claims.getStringClaim("operation"))||!binding.method().equals(claims.getStringClaim("http_method"))||!binding.resource().equals(claims.getStringClaim("resource"))||!binding.bodyHash().equals(claims.getStringClaim("body_sha256"))||!binding.correlation().toString().equals(claims.getStringClaim("correlation_id")))throw denied();
            nonnil(UUID.fromString(claims.getJWTID()));String issuer=claims.getStringClaim("identity_issuer");if(!identityIssuers.contains(issuer))throw denied();
            Instant mfa=c.has("mfa_at")?Instant.ofEpochSecond(c.get("mfa_at").asLong()):null;if(mfa!=null&&mfa.isAfter(now.plusSeconds(5)))throw denied();
            return new Actor(UUID.fromString(claims.getSubject()),issuer,claims.getStringClaim("identity_subject"),claims.getStringClaim("organization_id")==null?null:UUID.fromString(claims.getStringClaim("organization_id")),new HashSet<>(claims.getStringListClaim("roles")),new HashSet<>(claims.getStringListClaim("scopes")),mfa);
        } catch(Exception rejected){throw denied();}
    }
    public static void recentMfa(Actor actor,Clock clock){if(actor.mfaAt()==null||actor.mfaAt().isAfter(clock.instant().plusSeconds(5))||!actor.mfaAt().isAfter(clock.instant().minusSeconds(900)))throw denied();}
    public static AccessDeniedException denied(){return new AccessDeniedException("ACTOR_TRUST_REJECTED");}
    private static void nonnil(UUID id){if(id==null||id.equals(new UUID(0,0)))throw denied();}
}
