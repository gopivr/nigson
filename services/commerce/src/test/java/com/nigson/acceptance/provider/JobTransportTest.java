package com.nigson.acceptance.provider;
import com.nigson.commerce.provider.*;
import com.nigson.shared.security.ActorAssertion;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
class JobTransportTest {
    @Test void acceptedMutationWithMissingVersionHeaderRemainsUnknownAndIsNeverAutomaticallyResent()throws Exception{
        UUID job=UUID.randomUUID();var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",x->{calls.incrementAndGet();assertThat(x.getRequestHeaders().getFirst("X-Nigson-Actor")).isEqualTo("synthetic-assertion");assertThat(x.getRequestHeaders().getFirst("If-Match")).isEqualTo("\"0\"");byte[] body=("{\"id\":\""+job+"\",\"state\":\"PENDING\",\"attemptCount\":1,\"nextAttemptAt\":\"2026-10-10T00:00:00Z\",\"lastErrorCode\":null}").getBytes(java.nio.charset.StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(202,body.length);x.getResponseBody().write(body);x.close();});server.start();
        try{var adapter=new HttpIntegrationJobs("test","http://127.0.0.1:"+server.getAddress().getPort(),r->"synthetic-workload");assertThatThrownBy(()->adapter.retry(job,0,new JobValues.Retry("Synthetic retry"),UUID.randomUUID().toString(),"synthetic-assertion",UUID.randomUUID())).hasMessage("PROVIDER_OUTCOME_UNKNOWN");assertThat(calls.get()).isEqualTo(1);}finally{server.stop(0);}
    }
    @Test void environmentTargetsAreClosedAndProductionNeverFallsBack(){
        assertThat(HttpIntegrationJobs.target("test","http://127.0.0.1:1234").getHost()).isEqualTo("127.0.0.1");
        assertThat(HttpIntegrationJobs.target("prod","https://approved-service.synthetic.internal").getScheme()).isEqualTo("https");
        for(String target:List.of("http://127.0.0.1:1234","https://example.com","https://approved.internal/path","https://user@approved.internal","https://approved.internal?url=x","https://approved.internal#x"))assertThatThrownBy(()->HttpIntegrationJobs.target("prod",target)).hasMessage("INTEGRATION_TARGET_REQUIRED");
        assertThatThrownBy(()->HttpIntegrationJobs.target("test","http://provider.example.com")).hasMessage("INTEGRATION_TARGET_REQUIRED");
    }
    @Test void bodyLimitsMalformedDtosRedirectsAndRemoteTextCannotEscape()throws Exception{
        var mode=new AtomicInteger();var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",x->{calls.incrementAndGet();assertThat(x.getRequestHeaders().getFirst("X-Nigson-Actor")).isNull();assertThat(x.getRequestHeaders().getFirst("Cookie")).isNull();assertThat(x.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer synthetic-workload");String text=switch(mode.get()){case 0->"x".repeat(65537);case 1->"{\"items\":[],\"page\":0,\"size\":20,\"totalElements\":0,\"totalPages\":0,\"private\":\"protected\"}";case 2->"protected remote text";default->"{\"code\":\"VERSION_MISMATCH\",\"detail\":\"protected remote text\"}";};x.getResponseHeaders().set("Content-Type",mode.get()==3?"application/problem+json":"application/json");x.getResponseHeaders().set("Location","https://external.invalid");byte[] body=text.getBytes(java.nio.charset.StandardCharsets.UTF_8);x.sendResponseHeaders(mode.get()==2?302:mode.get()==3?412:200,body.length);x.getResponseBody().write(body);x.close();});server.start();
        try{var adapter=new HttpIntegrationJobs("test","http://127.0.0.1:"+server.getAddress().getPort(),r->{assertThat(r.scope()).isEqualTo("jobs.read");return "synthetic-workload";});for(int i=0;i<3;i++){mode.set(i);assertThatThrownBy(()->adapter.list(new JobValues.Query(null,0,20,"id,asc"),UUID.randomUUID())).hasMessage("PROVIDER_UNAVAILABLE");}assertThat(calls.get()).isEqualTo(3);mode.set(3);assertThatThrownBy(()->adapter.list(new JobValues.Query(null,0,20,"id,asc"),UUID.randomUUID())).hasMessage("VERSION_MISMATCH");}finally{server.stop(0);}
    }
    @Test void timeoutAfterResponseHeadersIsBoundedAndMutationRemainsUnknown()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);try(var executor=Executors.newVirtualThreadPerTaskExecutor()){server.setExecutor(executor);server.createContext("/",x->{try{x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(202,100);Thread.sleep(7500);}catch(InterruptedException stopped){Thread.currentThread().interrupt();}finally{x.close();}});server.start();try{var adapter=new HttpIntegrationJobs("test","http://127.0.0.1:"+server.getAddress().getPort(),r->"synthetic-workload");long start=System.nanoTime();assertThatThrownBy(()->adapter.retry(UUID.randomUUID(),0,new JobValues.Retry("Synthetic retry"),UUID.randomUUID().toString(),"synthetic-assertion",UUID.randomUUID())).hasMessage("PROVIDER_OUTCOME_UNKNOWN");assertThat(java.time.Duration.ofNanos(System.nanoTime()-start).toMillis()).isLessThan(7000);}finally{server.stop(0);}}
    }
    @Test void noHttpOrCredentialAcquisitionInsideDatabaseTransaction(){var calls=new AtomicInteger();var adapter=new HttpIntegrationJobs("test","http://127.0.0.1:9",r->{calls.incrementAndGet();return "synthetic-workload";});org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);try{assertThatThrownBy(()->adapter.list(new JobValues.Query(null,0,20,"id,asc"),UUID.randomUUID())).hasMessage("IDENTITY_HTTP_OUTSIDE_TRANSACTION_REQUIRED");assertThat(calls.get()).isZero();}finally{org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}}
}
