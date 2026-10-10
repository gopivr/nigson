package com.nigson.acceptance.catalogue;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.commerce.security.*;
import com.nigson.shared.events.EventCodec;
import static org.assertj.core.api.Assertions.*;

public final class CatalogueHttpAcceptance {
    private CatalogueHttpAcceptance(){}
    public static void run(ConfigurableApplicationContext app,HttpClient http,String base)throws Exception{
        var jdbc=app.getBean(JdbcTemplate.class);var sessions=app.getBean(JdbcSessionTrust.class);
        String issuer="http://127.0.0.1:9/realms/offline",market="HTTP"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
        UUID editor=UUID.randomUUID(),publisher=UUID.randomUUID(),retail=UUID.randomUUID(),marketId=UUID.randomUUID();var ids=List.of(editor,publisher,retail);var cookie=new HashMap<UUID,String>();var csrf=new HashMap<UUID,String>();
        try{
            for(var id:ids){jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic HTTP catalogue actor')",id,issuer,id.toString(),id+"@example.invalid");
                if(!id.equals(retail))jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,?,?)",UUID.randomUUID(),id,id.equals(editor)?"CONTENT_EDITOR":"CONTENT_PUBLISHER",id);
                var pending=sessions.begin("/account");sessions.consume(pending.sessionId(),pending.flow().state());String token=JdbcSessionTrust.opaque();csrf.put(id,token);
                cookie.put(id,"__Host-nigson-session="+sessions.authenticate(pending.sessionId(),new SessionRecord(1,id,issuer,id.toString(),Instant.now(),Instant.now(),"synthetic-access","synthetic-refresh",Instant.now().plusSeconds(300),token,SessionRecord.TokenState.READY)));
            }
            jdbc.update("INSERT INTO commerce.currency(id,created_by,updated_by,code,minor_unit) VALUES (?,'test','test','NGN',2) ON CONFLICT(code) DO NOTHING",UUID.randomUUID());
            jdbc.update("INSERT INTO commerce.market(id,created_by,updated_by,code,name,currency_code,enabled) VALUES (?,'test','test',?,'Synthetic HTTP market','NGN',true)",marketId,market);
            jdbc.update("INSERT INTO commerce.feature_configuration(id,created_by,updated_by,key,revision,value,active) VALUES (?,'http-catalogue','http-catalogue','catalogue.metadata',1,'{\"schemaVersion\":1,\"units\":[{\"code\":\"SYNTHETIC\",\"scales\":[0]}],\"divisions\":[]}',true)",UUID.randomUUID());
            String staff=base+"/api/v1/staff/products",brands=base+"/api/v1/staff/brands",categories=base+"/api/v1/staff/categories";
            assertThat(send(http,HttpRequest.newBuilder(URI.create(staff)).GET()).statusCode()).isEqualTo(401);
            assertThat(send(http,HttpRequest.newBuilder(URI.create(staff)).header("Cookie",cookie.get(retail)).GET()).statusCode()).isEqualTo(403);
            String brandInput="{\"name\":\"Synthetic HTTP brand\",\"slug\":\"synthetic-"+UUID.randomUUID()+"\"}";
            var brand=send(http,mutation(brands,"POST",cookie.get(editor),csrf.get(editor),null,"brand",brandInput));assertThat(brand.statusCode()).isEqualTo(201);UUID brandId=UUID.fromString(EventCodec.JSON.readTree(brand.body()).path("id").asString());
            var category=send(http,mutation(categories,"POST",cookie.get(editor),csrf.get(editor),null,"category",brandInput.replace("brand","category")));assertThat(category.statusCode()).isEqualTo(201);UUID categoryId=UUID.fromString(EventCodec.JSON.readTree(category.body()).path("id").asString());
            assertThat(send(http,mutation(categories+"/"+categoryId,"PATCH",cookie.get(editor),csrf.get(editor),"0","cycle","{\"parentId\":\""+categoryId+"\"}")).statusCode()).isEqualTo(422);
            String body=EventCodec.JSON.writeValueAsString(Map.of("name","Synthetic HTTP product","slug","synthetic-"+UUID.randomUUID(),"description","Synthetic HTTP description","brandId",brandId,"specifications",Map.of(),"categoryIds",List.of(categoryId)));
            assertThat(send(http,mutation(staff,"POST",cookie.get(editor),"wrong",null,"csrf",body)).statusCode()).isEqualTo(401);
            for(String bad:List.of(body.replace("\"specifications\":{}","\"specifications\":{\"unregistered\":true}"),body.replace("\"name\":\"Synthetic HTTP product\"","\"name\":true"),body.replace("\"description\":","\"status\":\"ACTIVE\",\"description\":"))){var response=send(http,mutation(staff,"POST",cookie.get(editor),csrf.get(editor),null,UUID.randomUUID().toString(),bad));assertThat(response.statusCode()).isIn(400,422);assertThat(response.body()).doesNotContain("SELECT","synthetic-refresh");}
            String key=UUID.randomUUID().toString();var created=send(http,mutation(staff,"POST",cookie.get(editor),csrf.get(editor),null,key,body));assertThat(created.statusCode()).isEqualTo(201);assertThat(created.headers().firstValue("ETag")).hasValue("\"0\"");assertThat(send(http,mutation(staff,"POST",cookie.get(editor),csrf.get(editor),null,key,body)).body()).isEqualTo(created.body());
            var dto=EventCodec.JSON.readTree(created.body());UUID product=UUID.fromString(dto.path("id").asString());String publicProduct=base+"/api/v1/catalogue/products/"+product+"?market="+market;
            assertThat(send(http,HttpRequest.newBuilder(URI.create(publicProduct)).GET()).statusCode()).isEqualTo(404);
            String skuInput="{\"code\":\"HTTP-"+product+"\",\"variantName\":\"Synthetic SKU\",\"unit\":\"SYNTHETIC\",\"quantityScale\":0,\"packSize\":\"1\",\"carrierPackaging\":"+ProductPersistenceTest.PACKAGING+"}";
            var sku=send(http,mutation(staff+"/"+product+"/skus","POST",cookie.get(editor),csrf.get(editor),"0","sku",skuInput));assertThat(sku.statusCode()).isEqualTo(201);UUID skuId=UUID.fromString(EventCodec.JSON.readTree(sku.body()).path("id").asString());
            String publication=staff+"/"+product+"/publication",publish="{\"action\":\"PUBLISH\",\"reason\":\"Synthetic publication review\"}";
            assertThat(send(http,mutation(publication,"POST",cookie.get(editor),csrf.get(editor),"1","role-denial",publish)).statusCode()).isEqualTo(403);
            assertThat(send(http,mutation(publication,"POST",cookie.get(publisher),csrf.get(publisher),"1","not-ready",publish)).statusCode()).isEqualTo(422);
            jdbc.update("INSERT INTO commerce.product_market_eligibility(id,created_by,updated_by,product_id,market_id,eligible) VALUES (?,'test','test',?,?,true)",UUID.randomUUID(),product,marketId);
            UUID asset=UUID.randomUUID();jdbc.update("INSERT INTO commerce.asset_reference(id,created_by,updated_by,product_id,object_key,purpose,mime_type,byte_size,checksum_sha256,status) VALUES (?,'test','test',?,?,'IMAGE','image/png',1,?,'CLEAN')",asset,product,"synthetic/"+asset+"/image.png","1".repeat(64));
            var published=send(http,mutation(publication,"POST",cookie.get(publisher),csrf.get(publisher),"1","publish",publish));assertThat(published.statusCode()).isEqualTo(201);assertThat(published.headers().firstValue("ETag")).hasValue("\"2\"");
            var visible=send(http,HttpRequest.newBuilder(URI.create(publicProduct)).GET());assertThat(visible.statusCode()).isEqualTo(200);assertThat(visible.headers().firstValue("Cache-Control").orElseThrow()).contains("public","must-revalidate");assertThat(visible.headers().allValues("Set-Cookie")).isEmpty();
            var view=EventCodec.JSON.readTree(visible.body());assertThat(view.properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrder("id","name","slug","description","specifications","brand","categories","skus","assets","revision");assertThat(visible.body()).doesNotContain("status","version","price","checksum","objectKey","SHIPPING_LABEL");
            String etag=visible.headers().firstValue("ETag").orElseThrow();var cached=send(http,HttpRequest.newBuilder(URI.create(publicProduct)).header("If-None-Match",etag).GET());assertThat(cached.statusCode()).isEqualTo(304);assertThat(cached.body()).isEmpty();
            assertThat(send(http,HttpRequest.newBuilder(URI.create(publicProduct)).header("Cookie","__Host-nigson-session=not-a-session").GET()).body()).isEqualTo(visible.body());
            assertThat(send(http,HttpRequest.newBuilder(URI.create(publicProduct)).header("Cookie",cookie.get(editor)).GET()).body()).isEqualTo(visible.body());
            for(String suffix:List.of("&unknown=1","&market="+market))assertThat(send(http,HttpRequest.newBuilder(URI.create(publicProduct+suffix)).GET()).statusCode()).isEqualTo(400);
            assertThat(send(http,HttpRequest.newBuilder(URI.create(base+"/api/v1/catalogue/products?market="+market+"&size=101")).GET()).statusCode()).isEqualTo(400);
            assertThat(send(http,HttpRequest.newBuilder(URI.create(base+"/api/v1/catalogue/products?market="+market+"&sort=private,asc")).GET()).statusCode()).isEqualTo(400);
            assertThat(send(http,mutation(staff+"/"+product,"PATCH",cookie.get(editor),csrf.get(editor),"0","stale-product","{\"name\":\"Changed\"}")).statusCode()).isEqualTo(412);
            String disabled="{\"active\":false,\"carrierPackaging\":"+ProductPersistenceTest.PACKAGING+"}";
            assertThat(send(http,mutation(base+"/api/v1/staff/skus/"+skuId,"PATCH",cookie.get(editor),csrf.get(editor),"0","sku-disable",disabled)).statusCode()).isEqualTo(200);
            var fresh=send(http,HttpRequest.newBuilder(URI.create(publicProduct)).header("If-None-Match",etag).GET());assertThat(fresh.statusCode()).isEqualTo(200);assertThat(EventCodec.JSON.readTree(fresh.body()).path("skus").size()).isZero();
            assertThat(send(http,mutation(brands+"/"+brandId,"PATCH",cookie.get(editor),csrf.get(editor),"0","brand-disable","{\"active\":false}")).statusCode()).isEqualTo(200);
            assertThat(send(http,mutation(categories+"/"+categoryId,"PATCH",cookie.get(editor),csrf.get(editor),"0","category-rename","{\"name\":\"Synthetic changed category\"}")).statusCode()).isEqualTo(200);
            var staffPage=send(http,HttpRequest.newBuilder(URI.create(staff+"?q=Synthetic%20HTTP%20product")).header("Cookie",cookie.get(editor)).GET());assertThat(staffPage.statusCode()).isEqualTo(200);assertThat(staffPage.headers().firstValue("Cache-Control")).hasValue("no-store");assertThat(EventCodec.JSON.readTree(staffPage.body()).path("items").size()).isEqualTo(1);
            assertThat(send(http,mutation(publication,"POST",cookie.get(publisher),csrf.get(publisher),"3","archive",publish.replace("PUBLISH","ARCHIVE"))).statusCode()).isEqualTo(201);
            assertThat(send(http,HttpRequest.newBuilder(URI.create(publicProduct)).header("If-None-Match",etag).GET()).statusCode()).isEqualTo(404);
            // Representative staff page: query count remains bounded as page size grows.
            String batch=UUID.randomUUID().toString(),prefix="synthetic-batch-"+batch+"-";
            jdbc.update("INSERT INTO commerce.product(id,created_by,updated_by,name,slug,description) SELECT md5(?||g)::uuid,'test','test','Synthetic batch '||?||' '||g,?||g,'Synthetic batch description' FROM generate_series(1,100) g",prefix,batch,prefix);
            jdbc.update("INSERT INTO commerce.sku(id,created_by,updated_by,product_id,code,variant_name,unit,pack_size,weight_kg,carrier_packaging) SELECT md5(?||'sku-'||g)::uuid,'test','test',md5(?||((g-1)/2+1))::uuid,?||g,'Synthetic batch SKU','SYNTHETIC',1,1,?::jsonb FROM generate_series(1,200) g",prefix,prefix,prefix,ProductPersistenceTest.PACKAGING);
            jdbc.update("INSERT INTO commerce.asset_reference(id,created_by,updated_by,product_id,object_key,purpose,mime_type,byte_size,checksum_sha256,status) SELECT md5(?||'asset-'||g)::uuid,'test','test',md5(?||g)::uuid,'synthetic/'||?||'/'||g||'/image.png','IMAGE','image/png',1,repeat('1',64),'QUARANTINED' FROM generate_series(1,100) g",prefix,prefix,batch);
            var meters=app.getBean(io.micrometer.core.instrument.MeterRegistry.class);long before=meters.find("nigson.db.query").tag("operation","SELECT").timers().stream().mapToLong(io.micrometer.core.instrument.Timer::count).sum();
            var batchPage=send(http,HttpRequest.newBuilder(URI.create(staff+"?q="+URLEncoder.encode("Synthetic batch "+batch,java.nio.charset.StandardCharsets.UTF_8)+"&size=100")).header("Cookie",cookie.get(editor)).GET());
            assertThat(batchPage.statusCode()).isEqualTo(200);var items=EventCodec.JSON.readTree(batchPage.body()).path("items");assertThat(items.size()).isEqualTo(100);for(var item:items){assertThat(item.path("skus").size()).isEqualTo(2);assertThat(item.path("assets").size()).isEqualTo(1);assertThat(item.path("assets").get(0).path("url").isNull()).isTrue();}
            long after=meters.find("nigson.db.query").tag("operation","SELECT").timers().stream().mapToLong(io.micrometer.core.instrument.Timer::count).sum();assertThat(after-before).as("100-product staff page cannot issue per-product queries").isBetween(1L,20L);
        }finally{
            jdbc.update("DELETE FROM commerce.feature_configuration WHERE key='catalogue.metadata' AND created_by='http-catalogue'");
            for(var id:ids){sessions.revokeSubject(id);jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id=?",id);jdbc.update("DELETE FROM commerce.party WHERE id=?",id);}
        }
    }
    private static HttpRequest.Builder mutation(String url,String method,String cookie,String csrf,String version,String key,String body){var r=HttpRequest.newBuilder(URI.create(url)).header("Cookie",cookie).header("X-CSRF-TOKEN",csrf).header("Content-Type","application/json").header("Idempotency-Key",key);if(version!=null)r.header("If-Match","\""+version+"\"");return r.method(method,HttpRequest.BodyPublishers.ofString(body));}
    private static HttpResponse<String> send(HttpClient http,HttpRequest.Builder request)throws Exception{return http.send(request.timeout(Duration.ofSeconds(5)).build(),HttpResponse.BodyHandlers.ofString());}
}
