package com.nigson.acceptance.reporting;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.acceptance.order.OrderFixture;
import com.nigson.commerce.security.JdbcSessionTrust;
import com.nigson.shared.events.EventCodec;
import static org.assertj.core.api.Assertions.*;

/** Actual BFF HTTP acceptance against the same local native Commerce fixture used by checkout. */
public final class ReportHttpAcceptance {
    private ReportHttpAcceptance(){}
    public static void run(ConfigurableApplicationContext app,HttpClient http,String base,OrderFixture f,UUID orderId,String customerCookie)throws Exception{
        if(f==null)return;
        JdbcTemplate jdbc=f.jdbc;UUID actor=f.operations.party(),org=f.prices.organization(),refund=UUID.randomUUID();Instant at=Instant.parse("2026-10-18T12:00:00Z");String issuer=f.operations.identityIssuer(),subject=f.operations.identitySubject();var sessions=app.getBean(JdbcSessionTrust.class);String analystCookie=null;int auditsBefore=jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE action='COMMERCE_REPORT_READ'",Integer.class);
        jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,'ANALYST',?)",UUID.randomUUID(),actor,actor);
        try{
            jdbc.update("UPDATE commerce.sales_order SET organization_id=?,created_at=?,refunded_total=10.00,payment_status='PARTIALLY_REFUNDED' WHERE id=?",org,java.sql.Timestamp.from(at),orderId);UUID attempt=jdbc.queryForObject("SELECT id FROM commerce.payment_attempt WHERE order_id=?",UUID.class,orderId);UUID line=jdbc.queryForObject("SELECT id FROM commerce.order_line WHERE order_id=?",UUID.class,orderId);jdbc.update("INSERT INTO commerce.refund(id,created_by,updated_by,order_id,attempt_id,request_key,amount,currency_code,state,reason,requested_by,provider_reference) VALUES (?,'test','test',?,?,?, '10.00','NGN','SUCCEEDED','Synthetic HTTP refund fixture',?,?)",refund,orderId,attempt,"report-http-"+refund,actor,"report-provider-"+refund);jdbc.update("INSERT INTO commerce.refund_line(id,created_by,updated_by,refund_id,order_line_id,quantity,amount) VALUES (?,'test','test',?,?,1,10.00)",UUID.randomUUID(),refund,line);
            String sid=JdbcSessionTrust.opaque();String csrf=JdbcSessionTrust.opaque();var flow=sessions.begin("/account");sessions.consume(flow.sessionId(),flow.flow().state());sid=sessions.authenticate(flow.sessionId(),new com.nigson.commerce.security.SessionRecord(1,actor,issuer,subject,Instant.now(),Instant.now(),"synthetic-access","synthetic-refresh",Instant.now().plusSeconds(300),csrf,com.nigson.commerce.security.SessionRecord.TokenState.READY));analystCookie="__Host-nigson-session="+sid;
            String path=base+"/api/v1/staff/reports/commerce";
            var response=send(http,HttpRequest.newBuilder(URI.create(path+query(at,at.plusSeconds(1),null,org))).header("Cookie",analystCookie).GET());assertThat(response.statusCode()).as(response.body()).isEqualTo(200);assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");var json=EventCodec.JSON.readTree(response.body());assertThat(json.path("sales").path("amount").asString()).isEqualTo("18.13");assertThat(json.path("orders").asLong()).isEqualTo(1);assertThat(json.path("products").get(0).path("revenue").path("amount").asString()).isEqualTo("14.68");
            var beforeTo=send(http,HttpRequest.newBuilder(URI.create(path+query(at.minusSeconds(1),at, null,org))).header("Cookie",analystCookie).GET());assertThat(beforeTo.statusCode()).as(beforeTo.body()).isEqualTo(200);assertThat(EventCodec.JSON.readTree(beforeTo.body()).path("orders").asLong()).isZero();
            assertThat(send(http,HttpRequest.newBuilder(URI.create(path+"?from="+at+"&to="+at.plusSeconds(1)+"&currency=NGN&organizationId="+UUID.randomUUID())).header("Cookie",analystCookie).GET()).statusCode()).isEqualTo(404);assertThat(send(http,HttpRequest.newBuilder(URI.create(path+"?from="+at+"&to="+at.plusSeconds(1)+"&currency=NGN&unknown=x")).header("Cookie",analystCookie).GET()).statusCode()).isEqualTo(400);assertThat(send(http,HttpRequest.newBuilder(URI.create(path+query(at,at.plusSeconds(1),null,org))).header("Cookie",customerCookie).GET()).statusCode()).isEqualTo(403);assertThat(send(http,HttpRequest.newBuilder(URI.create(path+query(at,at.plusSeconds(1),null,org))).GET()).statusCode()).isEqualTo(401);assertThat(response.body()).doesNotContain(issuer,subject,"synthetic-access","synthetic-refresh");assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE action='COMMERCE_REPORT_READ'",Integer.class)-auditsBefore).isEqualTo(2);
        }finally{if(analystCookie!=null)sessions.logout(analystCookie.substring(analystCookie.indexOf('=')+1));jdbc.update("DELETE FROM commerce.refund_line WHERE refund_id=?",refund);jdbc.update("DELETE FROM commerce.refund WHERE id=?",refund);jdbc.update("UPDATE commerce.sales_order SET organization_id=NULL WHERE id=?",orderId);jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id=? AND role='ANALYST'",actor);}
    }
    private static String query(Instant from,Instant to,UUID scope,UUID org){return "?from="+URLEncoder.encode(from.toString(),StandardCharsets.UTF_8)+"&to="+URLEncoder.encode(to.toString(),StandardCharsets.UTF_8)+"&currency=NGN&organizationId="+org+(scope==null?"":"&scope="+scope);}
    private static HttpResponse<String> send(HttpClient http,HttpRequest.Builder request)throws Exception{return http.send(request.timeout(java.time.Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.ofString());}
}
