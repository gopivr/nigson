package com.nigson.acceptance.catalogue;

import com.nigson.commerce.catalogue.*;
import com.nigson.commerce.files.JdbcCatalogueAssets;
import com.nigson.shared.events.EventCodec;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.zaxxer.hikari.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

/** Local representative source-query workload. The separate staging30m gate is not implied. */
class CatalogueReadLoadTest {
    @Test void tenThousandProductsTwentyThousandSkusServeTwentyReadsPerSecond()throws Exception{
        String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"catalogue-check supplies isolated PostgreSQL");
        if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");
        var settings=new HikariConfig();settings.setJdbcUrl(url+"?currentSchema=commerce");settings.setUsername("nigson_commerce_app");settings.setPassword("mock-commerce-runtime");settings.setMaximumPoolSize(10);settings.setConnectionTimeout(2000);
        try(var pool=new HikariDataSource(settings)){
            var jdbc=new JdbcTemplate(pool);String batch=UUID.randomUUID().toString(),prefix="synthetic-load-"+batch+"-",marketCode="LOAD"+batch.substring(0,8).toUpperCase();UUID market=UUID.randomUUID(),location=UUID.randomUUID();
            jdbc.update("INSERT INTO commerce.currency(id,created_by,updated_by,code,minor_unit) VALUES (?,'test','test','NGN',2) ON CONFLICT(code) DO NOTHING",UUID.randomUUID());
            jdbc.update("INSERT INTO commerce.market(id,created_by,updated_by,code,name,currency_code,enabled) VALUES (?,'test','test',?,'Synthetic load market','NGN',true)",market,marketCode);
            jdbc.update("INSERT INTO commerce.stock_location(id,created_by,updated_by,code,name) VALUES (?,'test','test',?,'Synthetic load stock location')",location,"LOAD-"+batch.substring(0,8));
            jdbc.update("INSERT INTO commerce.product(id,created_by,updated_by,name,slug,description,status,published_at) SELECT md5(?||g)::uuid,'test','test','Synthetic fixture product '||g,?||g,'Synthetic fixture description','ACTIVE',now() FROM generate_series(1,10000) g",prefix,prefix);
            jdbc.update("INSERT INTO commerce.sku(id,created_by,updated_by,product_id,code,variant_name,unit,quantity_scale,pack_size,weight_kg,carrier_packaging) SELECT md5(?||'sku-'||g)::uuid,'test','test',md5(?||((g-1)/2+1))::uuid,?||g,'Synthetic load SKU','SYNTHETIC',0,1,1,?::jsonb FROM generate_series(1,20000) g",prefix,prefix,prefix,ProductPersistenceTest.PACKAGING);
            jdbc.update("INSERT INTO commerce.inventory_balance(id,created_by,updated_by,sku_id,location_id,on_hand) SELECT md5(?||'balance-'||g)::uuid,'test','test',md5(?||'sku-'||g)::uuid,?,10 FROM generate_series(1,20000) g",prefix,prefix,location);
            jdbc.update("INSERT INTO commerce.product_market_eligibility(id,created_by,updated_by,product_id,market_id,eligible) SELECT md5(?||'eligible-'||g)::uuid,'test','test',md5(?||g)::uuid,?,true FROM generate_series(1,10000) g",prefix,prefix,market);
            jdbc.update("INSERT INTO commerce.asset_reference(id,created_by,updated_by,product_id,object_key,purpose,mime_type,byte_size,checksum_sha256,status) SELECT md5(?||'image-'||g)::uuid,'test','test',md5(?||g)::uuid,'synthetic/'||?||'/'||g||'/image.png','IMAGE','image/png',1,repeat('1',64),'PUBLISHED' FROM generate_series(1,10000) g",prefix,prefix,batch);
            var assets=new JdbcCatalogueAssets(jdbc,"test","https://cdn.synthetic.test/assets/");var manager=new DataSourceTransactionManager(pool);var tx=new TransactionTemplate(manager);tx.setReadOnly(true);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);tx.setTimeout(3);
            var reads=new CatalogueReads(new JdbcCatalogueQueries(jdbc,new JdbcProductRepository(jdbc,Clock.systemUTC(),assets),assets),new CatalogueAuthority(jdbc),jdbc,tx,Clock.systemUTC(),1000);
            var search=new CatalogueQueryRepository.Search(marketCode,"fixture",null,null,null,0,20,"relevance,desc");
            assertThat(reads.search(search).totalElements()).isEqualTo(10000);
            var times=new ArrayList<Long>();long schedule=System.nanoTime();
            try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
                var futures=new ArrayList<Future<Long>>();for(int n=0;n<100;n++){final long due=schedule+TimeUnit.MILLISECONDS.toNanos(n*50L);futures.add(executor.submit(()->{
                    long wait=due-System.nanoTime();if(wait>0)TimeUnit.NANOSECONDS.sleep(wait);
                    long start=System.nanoTime();var result=reads.search(search);assertThat(result.totalElements()).isEqualTo(10000);assertThat(result.items()).hasSize(20);assertThat(result.items()).allMatch(v->v.available()&&v.imageUrl()!=null);return System.nanoTime()-start;
                }));}for(var f:futures)times.add(f.get(15,TimeUnit.SECONDS));
            }
            Collections.sort(times);long p50=times.get(49),p95=times.get(94),p99=times.get(98);assertThat(Duration.ofNanos(p95)).isLessThanOrEqualTo(Duration.ofMillis(500));assertThat(Duration.ofNanos(p99)).isLessThanOrEqualTo(Duration.ofSeconds(1));
            var evidence=Path.of(System.getenv("NIGSON_EVENT_FIXTURES")).getParent().getParent().getParent().resolve(".build/catalogue001/read-load.json");Files.createDirectories(evidence.getParent());Files.writeString(evidence,EventCodec.JSON.writeValueAsString(Map.of("scope","local native source query; not staging30m checkout/provider qualification","products",10000,"skus",20000,"readers",100,"requests",100,"scheduled_rps",20,"p50_ms",p50/1000000.0,"p95_ms",p95/1000000.0,"p99_ms",p99/1000000.0,"errors",0))+"\n");
        }
    }
}
