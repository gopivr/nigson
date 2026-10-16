package com.nigson.acceptance.provider;

import com.nigson.commerce.security.*;
import com.nigson.commerce.provider.*;
import com.nigson.shared.security.*;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
import com.sun.net.httpserver.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.*;
import java.net.*;
import java.net.http.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Actual production workload decoder, KMS SDK signer and private guard over stateful local HTTP. */
public final class JobHttpAcceptance {
    private JobHttpAcceptance(){}
    public static final class Fixture implements AutoCloseable {
        public final AtomicInteger reconciliationEffects=new AtomicInteger(),reconciliationRequests=new AtomicInteger();public volatile boolean reconciliationAfterAccept;public volatile Runnable duringReconciliation=()->{};public final UUID reconciliationJob=UUID.randomUUID();final Map<String,com.nigson.commerce.fulfillment.CarrierReconciliationValues.Result> reconciliationCommands=new HashMap<>();final Map<String,String> reconciliationHashes=new HashMap<>();
        public volatile UUID lastCarrierOrganization;public final AtomicInteger carrierEffects=new AtomicInteger();final Map<UUID,com.nigson.commerce.fulfillment.CarrierRates.Rate> carrierQuotes=new HashMap<>();final Map<UUID,String> carrierHashes=new HashMap<>();
        public final AtomicInteger templateEffects=new AtomicInteger();public volatile boolean templateAfterAccept;final Map<String,com.nigson.commerce.notification.TemplatePort.Accepted> templateCommands=new HashMap<>();final Map<String,String> templateHashes=new HashMap<>();final Set<String> activeTemplates=new HashSet<>();
        final HttpServer server;final ExecutorService threads=Executors.newVirtualThreadPerTaskExecutor();final KeyPair signing,rsa;final PrivateWorkload guard;
        public final String issuer,endpoint;public final UUID job=UUID.randomUUID(),unknown=UUID.randomUUID();
        public final AtomicInteger effects=new AtomicInteger(),signs=new AtomicInteger(),reads=new AtomicInteger(),retries=new AtomicInteger();public volatile boolean afterAccept;long version;final Map<String,IntegrationJobs.Accepted> commands=new HashMap<>();final Map<String,String> hashes=new HashMap<>();
        public Fixture(KeyPair keys)throws Exception{
            signing=keys;var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);rsa=generator.generateKeyPair();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(threads);endpoint="http://127.0.0.1:"+server.getAddress().getPort();issuer=endpoint+"/realms/offline";
            guard=new PrivateWorkload("integration",1,new PrivateIngress(List.of("127.0.0.1/32")),r->KeycloakIdentity.decoder(new KeycloakIdentity.Policy("test",issuer,issuer+"/protocol/openid-connect/certs","nigson-integration",Set.of(r.client()),Set.of(r.scope()))),Map.of("active",(ECPublicKey)keys.getPublic()),Set.of(issuer),Clock.systemUTC());
            server.createContext("/",this::handle);server.start();
        }
        void respond(HttpExchange x,int status,Object body)throws java.io.IOException{byte[] bytes=EventCodec.JSON.writeValueAsBytes(body);x.getResponseHeaders().set("Content-Type",status>=400?"application/problem+json":"application/json");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);x.close();}
        String token()throws Exception{return workload("jobs.read jobs.retry_WITH_SIGNED_STAFF templates.write_WITH_SIGNED_ADMIN carrier.quote_WITH_SIGNED_ACTOR carrier.reconcile_WITH_SIGNED_STAFF","nigson-commerce","nigson-integration");}
        public String workload(String scope,String client,String audience)throws Exception{var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("jobs-http").build(),new JWTClaimsSet.Builder().issuer(issuer).subject("synthetic-"+client).audience(audience).issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(120))).claim("typ","Bearer").claim("azp",client).claim("client_id",client).claim("scope",scope).build());jwt.sign(new RSASSASigner(rsa.getPrivate()));return jwt.serialize();}
        void template(HttpExchange x,PrivateWorkload.Proof proof,byte[] body)throws Exception{
            assertThat(proof.actor().roles()).contains("ADMIN");ActorAssertion.recentMfa(proof.actor(),Clock.systemUTC());
            var value=com.nigson.commerce.notification.TemplateValues.template(EventCodec.JSON.readTree(body),1);String match=x.getRequestHeaders().getFirst("If-Match");Long expected=match==null?null:ApiValues.version(match);String key=ApiValues.idempotencyKey(x.getRequestHeaders().getFirst("Idempotency-Key"));ApiValues.uuid(key);String hash=EventCodec.digest(String.valueOf(expected)+":"+new String(body,java.nio.charset.StandardCharsets.UTF_8));
            com.nigson.commerce.notification.TemplatePort.Accepted saved;
            synchronized(this){
                saved=templateCommands.get(key);
                if(saved!=null){assertThat(templateHashes.get(key)).isEqualTo(hash);}
                else{
                    if(expected!=null&&expected!=0){respond(x,412,Map.of("code","VERSION_MISMATCH"));return;}
                    String revision=value.code()+":"+value.locale()+":"+value.channel()+":"+value.revision();
                    if(activeTemplates.contains(revision)){respond(x,422,Map.of("code","NOTIFICATION_RULE_VIOLATION","detail","protected template payload"));return;}
                    saved=new com.nigson.commerce.notification.TemplatePort.Accepted(UUID.randomUUID(),value.code(),value.revision(),value.active(),1L);templateCommands.put(key,saved);templateHashes.put(key,hash);if(value.active())activeTemplates.add(revision);templateEffects.incrementAndGet();
                }
            }
            if(templateAfterAccept){templateAfterAccept=false;x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(201,100);x.getResponseBody().write(123);x.getResponseBody().flush();Thread.sleep(7500);x.close();return;}
            x.getResponseHeaders().set("ETag","\""+saved.version()+"\"");respond(x,201,Map.of("id",saved.id(),"code",saved.code(),"revision",saved.revision(),"active",saved.active()));
        }
        void handle(HttpExchange x){try{
            String path=x.getRequestURI().getPath();byte[] body=x.getRequestBody().readNBytes(65537);if(body.length>65536)throw ApiValues.invalid();
            if(path.endsWith("/certs")){respond(x,200,Map.of("keys",List.of(new RSAKey.Builder((RSAPublicKey)rsa.getPublic()).keyID("jobs-http").algorithm(JWSAlgorithm.RS256).keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE).build().toJSONObject())));return;}
            if(path.endsWith("/token")){assertThat(new String(body,java.nio.charset.StandardCharsets.UTF_8)).contains("grant_type=client_credentials","client_id=nigson-commerce","synthetic-workload-credential-with-at-least-32-bytes");respond(x,200,Map.of("access_token",token(),"token_type","Bearer","expires_in",120));return;}
            if(path.equals("/")){assertThat(x.getRequestHeaders().getFirst("Authorization")).contains("Credential=mock-local-kms/");var request=EventCodec.JSON.readTree(body);assertThat(request.get("MessageType").asString()).isEqualTo("DIGEST");assertThat(request.get("SigningAlgorithm").asString()).isEqualTo("ECDSA_SHA_256");var signer=Signature.getInstance("NONEwithECDSA");signer.initSign(signing.getPrivate());signer.update(Base64.getDecoder().decode(request.get("Message").asString()));signs.incrementAndGet();respond(x,200,Map.of("KeyId",request.get("KeyId").asString(),"SigningAlgorithm","ECDSA_SHA_256","Signature",Base64.getEncoder().encodeToString(signer.sign())));return;}
            UUID correlation=ApiValues.uuid(x.getRequestHeaders().getFirst("X-Correlation-ID"));var proof=guard.verify("127.0.0.1",x.getRequestMethod(),path,x.getRequestHeaders().getFirst("Authorization"),x.getRequestHeaders().getFirst("X-Nigson-Actor"),correlation,ActorAssertion.Binding.bodyHash(body));
            if(path.equals("/internal/v1/carrier/quotes")){
                assertThat(proof.actor().roles()).contains("CUSTOMER");lastCarrierOrganization=proof.actor().organization();var request=EventCodec.JSON.readTree(body);assertThat(request.propertyNames()).containsExactlyInAnyOrder("commandId","cartId","market","currency","shipper","destination","packages");assertThat(request.path("market").asString()).isEqualTo("NG");assertThat(request.path("currency").asString()).isEqualTo("NGN");assertThat(request.path("packages").size()).isBetween(1,100);UUID command=ApiValues.uuid(request.path("commandId").asString());String hash=EventCodec.hash(request);com.nigson.commerce.fulfillment.CarrierRates.Rate rate;
                synchronized(this){rate=carrierQuotes.get(command);if(rate==null){rate=new com.nigson.commerce.fulfillment.CarrierRates.Rate("FEDEX","SYNTHETIC_SERVICE","NGN","1234.56",Instant.now().plusSeconds(250),null);carrierQuotes.put(command,rate);carrierHashes.put(command,hash);carrierEffects.incrementAndGet();}else assertThat(carrierHashes.get(command)).isEqualTo(hash);}
                x.getResponseHeaders().set("X-Nigson-Carrier-Mode","MOCK");respond(x,200,rate);return;
            }
            if(path.matches("/internal/v1/carrier/shipments/[a-f0-9-]{36}/reconciliation")){duringReconciliation.run();assertThat(proof.route().api()).isEqualTo("API-196");assertThat(proof.actor().roles()).contains("OPERATIONS");ActorAssertion.recentMfa(proof.actor(),Clock.systemUTC());var request=EventCodec.JSON.readTree(body);assertThat(request.propertyNames()).containsExactlyInAnyOrder("bookingRequestId","mode","reason");ApiValues.uuid(request.path("bookingRequestId").asString());assertThat(request.path("mode").asString()).isEqualTo("MOCK");UUID shipment=ApiValues.uuid(path.split("/")[5]);String key=ApiValues.uuid(x.getRequestHeaders().getFirst("Idempotency-Key")).toString(),hash=EventCodec.hash(request);com.nigson.commerce.fulfillment.CarrierReconciliationValues.Result result;reconciliationRequests.incrementAndGet();synchronized(this){result=reconciliationCommands.get(key);if(result==null){result=new com.nigson.commerce.fulfillment.CarrierReconciliationValues.Result(shipment,"UNKNOWN",reconciliationJob);reconciliationCommands.put(key,result);reconciliationHashes.put(key,hash);reconciliationEffects.incrementAndGet();}else assertThat(reconciliationHashes.get(key)).isEqualTo(hash);}if(reconciliationAfterAccept){reconciliationAfterAccept=false;x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(202,100);x.getResponseBody().write('{');x.close();return;}respond(x,202,result);return;}
            if(path.equals("/internal/v1/templates")){template(x,proof,body);return;}
            if(path.equals("/internal/v1/jobs")){assertThat(proof.actor()).isNull();reads.incrementAndGet();String query=x.getRequestURI().getRawQuery();assertThat(query).contains("page=0","size=20");respond(x,200,new JobValues.Page(List.of(dto(job,version==0?"FAILED":"PENDING")),0,20,1,1));return;}
            assertThat(proof.actor().roles()).contains("OPERATIONS");ActorAssertion.recentMfa(proof.actor(),Clock.systemUTC());JobValues.retry(EventCodec.JSON.readTree(body));long expected=ApiValues.version(x.getRequestHeaders().getFirst("If-Match"));String key=ApiValues.idempotencyKey(x.getRequestHeaders().getFirst("Idempotency-Key"));ApiValues.uuid(key);retries.incrementAndGet();UUID id=ApiValues.uuid(path.split("/")[4]);String hash=EventCodec.digest(id+":"+expected+":"+new String(body,java.nio.charset.StandardCharsets.UTF_8));
            synchronized(this){
                if(id.equals(unknown)){respond(x,503,Map.of("code","PROVIDER_OUTCOME_UNKNOWN","detail","synthetic protected provider payload"));return;}
                if(!id.equals(job)){respond(x,404,Map.of("code","NOT_FOUND"));return;}
                if(commands.containsKey(key)){assertThat(hashes.get(key)).isEqualTo(hash);var saved=commands.get(key);x.getResponseHeaders().set("ETag","\""+saved.version()+"\"");respond(x,202,saved.job());return;}
                if(expected!=version){respond(x,412,Map.of("code","VERSION_MISMATCH","detail","protected remote text"));return;}
                if(version>0){respond(x,409,Map.of("code","INVALID_TRANSITION"));return;}
                effects.incrementAndGet();version++;var accepted=new IntegrationJobs.Accepted(dto(job,"PENDING"),version);commands.put(key,accepted);hashes.put(key,hash);
                if(afterAccept){afterAccept=false;x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(202,100);x.getResponseBody().write(123);x.getResponseBody().flush();Thread.sleep(7500);x.close();return;}
                x.getResponseHeaders().set("ETag","\""+version+"\"");respond(x,202,accepted.job());
            }
        }catch(Problem.Failure denied){try{respond(x,denied.code.status,Map.of("code",denied.code.name()));}catch(Exception ignored){x.close();}}catch(Throwable failed){try{respond(x,500,Map.of("code","INTERNAL_ERROR"));}catch(Exception ignored){x.close();}}}
        JobValues.Job dto(UUID id,String state){return new JobValues.Job(id,state,1,Instant.parse("2026-10-10T00:00:00Z"),null);}
        public void configure(Map<String,Object> p){p.put("NIGSON_OIDC_ISSUER",issuer);p.put("NIGSON_OIDC_JWKS",issuer+"/protocol/openid-connect/certs");p.put("NIGSON_KMS_LOCAL_ENDPOINT",endpoint);p.put("NIGSON_INTEGRATION_JOBS_ENABLED","true");p.put("NIGSON_INTEGRATION_BASE_URL",endpoint);}
        public void close(){server.stop(0);threads.close();}
    }
    public static void run(ConfigurableApplicationContext app,HttpClient http,String base,Fixture fixture)throws Exception{
        if(fixture==null)return;var jdbc=app.getBean(JdbcTemplate.class);var sessions=app.getBean(JdbcSessionTrust.class);UUID operator=UUID.randomUUID(),customer=UUID.randomUUID(),stale=UUID.randomUUID();var ids=List.of(operator,customer,stale);var cookies=new HashMap<UUID,String>();var csrf=new HashMap<UUID,String>();String root=base+"/api/v1/staff/integration-jobs",body="{\"reason\":\"Synthetic operations retry\"}";
        try{
            for(var id:ids){jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic operations proxy')",id,fixture.issuer,id.toString(),id+"@example.invalid");if(!id.equals(customer))jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,'OPERATIONS',?)",UUID.randomUUID(),id,id);var flow=sessions.begin("/account");sessions.consume(flow.sessionId(),flow.flow().state());String token=JdbcSessionTrust.opaque();csrf.put(id,token);String session=sessions.authenticate(flow.sessionId(),new SessionRecord(1,id,fixture.issuer,id.toString(),Instant.now(),Instant.now().minusSeconds(id.equals(stale)?901:0),"synthetic-access","synthetic-refresh",Instant.now().plusSeconds(300),token,SessionRecord.TokenState.READY));cookies.put(id,"__Host-nigson-session="+session);}
            assertThat(send(http,HttpRequest.newBuilder(URI.create(root)).GET()).statusCode()).isEqualTo(401);
            assertThat(send(http,HttpRequest.newBuilder(URI.create(root)).header("Cookie",cookies.get(customer)).GET()).statusCode()).isEqualTo(403);
            var list=send(http,HttpRequest.newBuilder(URI.create(root)).header("Cookie",cookies.get(operator)).GET());assertThat(list.statusCode()).isEqualTo(200);assertThat(list.body()).doesNotContain("businessKey","providerKey","protected");assertThat(fixture.reads.get()).isEqualTo(1);assertThat(fixture.signs.get()).isZero();
            for(String query:List.of("?url=https://example.invalid","?page=0&page=1","?state=NEW","?size=101","?sort=payload,asc"))assertThat(send(http,HttpRequest.newBuilder(URI.create(root+query)).header("Cookie",cookies.get(operator)).GET()).statusCode()).isEqualTo(400);
            String retry=root+"/"+fixture.job+"/retry",key=UUID.randomUUID().toString();
            assertThat(send(http,mutation(retry,cookies.get(operator),"wrong","0","csrf",body)).statusCode()).isEqualTo(401);
            assertThat(send(http,mutation(retry,cookies.get(stale),csrf.get(stale),"0","stale-mfa",body)).statusCode()).isEqualTo(403);
            assertThat(send(http,mutation(retry,cookies.get(customer),csrf.get(customer),"0","role",body)).statusCode()).isEqualTo(403);
            assertThat(send(http,mutation(retry,cookies.get(operator),csrf.get(operator),"3","version",body)).statusCode()).isEqualTo(412);
            fixture.afterAccept=true;var unknown=send(http,mutation(retry,cookies.get(operator),csrf.get(operator),"0",key,body));assertThat(unknown.statusCode()).isEqualTo(503);assertThat(unknown.body()).contains("PROVIDER_OUTCOME_UNKNOWN").doesNotContain("protected acceptance");assertThat(fixture.effects.get()).isEqualTo(1);
            var acknowledged=send(http,mutation(retry,cookies.get(operator),csrf.get(operator),"0",key,body));assertThat(acknowledged.statusCode()).isEqualTo(202);assertThat(acknowledged.headers().firstValue("ETag")).hasValue("\"1\"");assertThat(acknowledged.headers().firstValue("Cache-Control")).hasValue("no-store");int calls=fixture.retries.get(),signs=fixture.signs.get();for(int i=0;i<5;i++)assertThat(send(http,mutation(retry,cookies.get(operator),csrf.get(operator),"0",key,body)).body()).isEqualTo(acknowledged.body());assertThat(fixture.retries.get()).isEqualTo(calls);assertThat(fixture.signs.get()).isEqualTo(signs);assertThat(fixture.effects.get()).isEqualTo(1);
            assertThat(send(http,mutation(retry,cookies.get(operator),csrf.get(operator),"0",key,body.replace("retry","changed retry"))).statusCode()).isEqualTo(409);
            String unresolved=root+"/"+fixture.unknown+"/retry",unknownKey=UUID.randomUUID().toString();for(int i=0;i<2;i++)assertThat(send(http,mutation(unresolved,cookies.get(operator),csrf.get(operator),"0",unknownKey,body)).statusCode()).isEqualTo(503);assertThat(fixture.effects.get()).isEqualTo(1);
            jdbc.update("UPDATE commerce.staff_role_assignment SET revoked_at=now() WHERE party_id=?",operator);assertThat(send(http,mutation(retry,cookies.get(operator),csrf.get(operator),"0",key,body)).statusCode()).isEqualTo(403);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.idempotency_record WHERE principal_key=? AND response_body->>'outcome'='UNKNOWN'",Integer.class,operator.toString())).isEqualTo(1);
        }finally{for(var id:ids){String cookie=cookies.get(id);if(cookie!=null)sessions.logout(cookie.substring(cookie.indexOf('=')+1));jdbc.update("DELETE FROM commerce.idempotency_record WHERE principal_key=?",id.toString());jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id=?",id);jdbc.update("DELETE FROM commerce.party WHERE id=?",id);}}
    }
    static HttpRequest.Builder mutation(String uri,String cookie,String csrf,String version,String key,String body){return HttpRequest.newBuilder(URI.create(uri)).header("Cookie",cookie).header("X-CSRF-TOKEN",csrf).header("Content-Type","application/json").header("If-Match","\""+version+"\"").header("Idempotency-Key",key).POST(HttpRequest.BodyPublishers.ofString(body));}
    static HttpResponse<String> send(HttpClient http,HttpRequest.Builder r)throws Exception{return http.send(r.timeout(Duration.ofSeconds(15)).build(),HttpResponse.BodyHandlers.ofString());}
}
