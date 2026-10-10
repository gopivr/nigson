package com.nigson.acceptance.files;

import com.nigson.commerce.files.*;
import com.nigson.commerce.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.*;
import com.nigson.shared.problem.*;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

class FilePersistenceTest {
    JdbcTemplate jdbc;FileService service;FileRepository repo;StorageFixture fixture;UUID editor,other;String issuer="https://identity.synthetic.invalid/realms/test";AtomicLong offset=new AtomicLong();
    byte[] bytes=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJ1kAAAAASUVORK5CYII=");
    String checksum;
    @BeforeEach void setup()throws Exception{
        String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null&&System.getenv("NIGSON_TEST_STORAGE_FIXTURE")!=null,"files-check supplies isolated PostgreSQL/TLS storage");if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");
        checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));fixture=new StorageFixture();var ds=new DriverManagerDataSource(url+"?currentSchema=commerce","nigson_commerce_app","mock-commerce-runtime");jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);var metrics=new SimpleMeterRegistry();
        Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return Instant.now().plusSeconds(offset.get());}};
        editor=party();other=party();repo=new JdbcFileRepository(jdbc,clock);service=new FileService(repo,fixture.storage,new PlatformStore(jdbc,manager,clock,metrics),new AuditWriter(jdbc,clock,metrics),manager,clock,"test",metrics);
    }
    UUID party(){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic editor')",id,issuer,id.toString(),id+"@example.invalid");jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,'CONTENT_EDITOR',?)",UUID.randomUUID(),id,id);return id;}
    @AfterEach void cleanup(){if(fixture!=null)fixture.close();if(jdbc!=null)for(UUID id:List.of(editor,other)){jdbc.update("DELETE FROM commerce.file_transfer WHERE owner_party_id=?",id);jdbc.update("DELETE FROM commerce.asset_reference WHERE created_by=?",id.toString());jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id=?",id);jdbc.update("DELETE FROM commerce.party WHERE id=?",id);}}
    ActorAssertion.Actor actor(UUID id){return new ActorAssertion.Actor(id,issuer,id.toString(),null,Set.of("CONTENT_EDITOR"),Set.of(),null);}
    JsonNode uploadInput(){return EventCodec.JSON.valueToTree(Map.of("purpose","IMAGE","filename","Synthetic image.png","mimeType","image/png","size",bytes.length,"checksumSha256",checksum));}
    JsonNode completion(){return EventCodec.JSON.valueToTree(Map.of("checksumSha256",checksum));}
    FileValues.UploadResult upload(String key){return service.upload(actor(editor),uploadInput(),key,UUID.randomUUID());}
    FileValues.Asset complete(UUID id,String key){return service.complete(actor(editor),id,0,completion(),key,UUID.randomUUID());}
    PrivateWorkload.Proof proof(){return new PrivateWorkload.Proof("nigson-integration","synthetic-integration",PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-132")).findFirst().orElseThrow(),null);}
    JsonNode scan(UUID id,String result){return EventCodec.JSON.valueToTree(Map.of("commandId",repo.find(id,false).scanCommand(),"checksumSha256",checksum,"result",result,"scannerVersion","synthetic-known-fixture-v1"));}
    FileValues.Asset apply(UUID id,JsonNode body,String key){return service.scan(proof(),id,1,body,id+":"+key,UUID.randomUUID());}
    @Test void realTlsSignedUploadHeadPinnedCopyAndDownloadWithDurableDuplicates()throws Exception{
        String key=UUID.randomUUID().toString();var upload=upload(key);assertThat(upload.uploadUrl()).startsWith("https://localhost:");assertThat(upload.requiredHeaders()).isNotEmpty();assertThat(fixture.put(upload,bytes).statusCode()).isEqualTo(200);assertThat(fixture.put(upload,bytes).statusCode()).isEqualTo(412);
        assertThat(fixture.get(upload.uploadUrl().replaceFirst("X-Amz-Signature=[a-f0-9]+","X-Amz-Signature="+"0".repeat(64))).statusCode()).isEqualTo(403);
        assertThat(upload(key)).isEqualTo(upload);var queued=complete(upload.assetId(),"completion");assertThat(queued.state()).isEqualTo("QUARANTINED");assertThat(queued.version()).isEqualTo(1);assertThat(complete(upload.assetId(),"completion")).isEqualTo(queued);
        assertThatThrownBy(()->service.download(actor(editor),upload.assetId(),UUID.randomUUID())).hasMessage("INVALID_TRANSITION");
        var stored=repo.find(upload.assetId(),false);assertThat(stored.sourceVersion()).isNotBlank();assertThat(stored.sourceEtag()).isNotBlank();
        String envelope=jdbc.queryForObject("SELECT payload::text FROM commerce.outbox_event WHERE event_id=?",String.class,stored.scanCommand());assertThat(EventCodec.decode(envelope).path("payload").path("asset_id").asString()).isEqualTo(upload.assetId().toString());
        int copies=fixture.control(null).path("copies").asInt();var input=scan(upload.assetId(),"CLEAN");var clean=apply(upload.assetId(),input,"scan");assertThat(clean.state()).isEqualTo("CLEAN");assertThat(clean.url()).isNull();assertThat(clean.version()).isEqualTo(2);
        for(int i=0;i<20;i++)assertThat(apply(upload.assetId(),input,"duplicate-"+i)).isEqualTo(clean);assertThat(fixture.control(null).path("copies").asInt()).isEqualTo(copies+1);
        var download=service.download(actor(editor),upload.assetId(),UUID.randomUUID());assertThat(Duration.between(Instant.now(),download.expiresAt()).toSeconds()).isBetween(58L,60L);assertThat(fixture.get(download.url()).body()).isEqualTo(bytes);
        assertThatThrownBy(()->apply(upload.assetId(),scan(upload.assetId(),"REJECTED"),"late-reject")).hasMessage("IDEMPOTENCY_HASH_MISMATCH");
    }
    @Test void ownerCurrentRoleChecksumAndConfiguredScanCommandAreRequired()throws Exception{
        var v=upload("ownership");assertThat(fixture.put(v,bytes).statusCode()).isEqualTo(200);
        assertThatThrownBy(()->service.complete(actor(other),v.assetId(),0,completion(),"foreign",UUID.randomUUID())).hasMessage("ACCESS_DENIED");
        assertThatThrownBy(()->service.complete(actor(editor),v.assetId(),0,EventCodec.JSON.valueToTree(Map.of("checksumSha256","0".repeat(64))),"wrong-checksum",UUID.randomUUID())).hasMessage("FILES_RULE_VIOLATION");
        complete(v.assetId(),"valid");var bad=(tools.jackson.databind.node.ObjectNode)scan(v.assetId(),"CLEAN");bad.put("commandId",UUID.randomUUID().toString());assertThatThrownBy(()->apply(v.assetId(),bad,"wrong-job")).hasMessage("ACCESS_DENIED");
        assertThatThrownBy(()->service.scan(null,v.assetId(),1,scan(v.assetId(),"CLEAN"),"browser",UUID.randomUUID())).hasMessage("ACCESS_DENIED");
        jdbc.update("UPDATE commerce.staff_role_assignment SET revoked_at=now() WHERE party_id=?",editor);assertThatThrownBy(()->upload("ownership")).hasMessage("ACCESS_DENIED");assertThatThrownBy(()->complete(v.assetId(),"valid")).hasMessage("ACCESS_DENIED");
        assertThat(repo.find(v.assetId(),false).state()).isEqualTo("QUARANTINED");
    }
    @Test void copyTimeoutAfterAcceptanceRetainsUnknownAndReconcilesWithoutSecondCopy()throws Exception{
        var v=upload("after-accept");assertThat(fixture.put(v,bytes).statusCode()).isEqualTo(200);complete(v.assetId(),"complete");var input=scan(v.assetId(),"CLEAN");int copies=fixture.control(null).path("copies").asInt();fixture.control("AFTER_ACCEPT");
        assertThatThrownBy(()->apply(v.assetId(),input,"scan")).hasMessage("PROVIDER_OUTCOME_UNKNOWN");assertThat(repo.find(v.assetId(),false).transferState()).isEqualTo("UNKNOWN");assertThat(repo.find(v.assetId(),false).state()).isEqualTo("QUARANTINED");
        assertThat(apply(v.assetId(),input,"scan").state()).isEqualTo("CLEAN");assertThat(fixture.control(null).path("copies").asInt()).isEqualTo(copies+1);
    }
    @Test void copyTimeoutBeforeAcceptanceNeverBlindlyRetriesOrResurrectsRejectedFile()throws Exception{
        var v=upload("before-accept");assertThat(fixture.put(v,bytes).statusCode()).isEqualTo(200);complete(v.assetId(),"complete");var input=scan(v.assetId(),"CLEAN");int copies=fixture.control(null).path("copies").asInt();fixture.control("BEFORE_ACCEPT");
        assertThatThrownBy(()->apply(v.assetId(),input,"scan")).hasMessage("PROVIDER_OUTCOME_UNKNOWN");assertThatThrownBy(()->apply(v.assetId(),input,"scan")).hasMessage("PROVIDER_OUTCOME_UNKNOWN");assertThat(fixture.control(null).path("copies").asInt()).isEqualTo(copies+1);
        var rejected=upload("reject");assertThat(fixture.put(rejected,bytes).statusCode()).isEqualTo(200);complete(rejected.assetId(),"complete-reject");assertThat(apply(rejected.assetId(),scan(rejected.assetId(),"REJECTED"),"reject").state()).isEqualTo("REJECTED");
        assertThatThrownBy(()->apply(rejected.assetId(),scan(rejected.assetId(),"CLEAN"),"late-clean")).hasMessage("IDEMPOTENCY_HASH_MISMATCH");assertThatThrownBy(()->service.download(actor(editor),rejected.assetId(),UUID.randomUUID())).hasMessage("INVALID_TRANSITION");
    }
    @Test void deniedCompletionAuditRollsBackQuarantineAndOutboxThenSameKeyRecovers()throws Exception{
        var v=upload("audit");assertThat(fixture.put(v,bytes).statusCode()).isEqualTo(200);var admin=new JdbcTemplate(new DriverManagerDataSource(System.getenv("NIGSON_TEST_JDBC_URL"),"nigson_bootstrap","mock-bootstrap"));admin.execute("REVOKE INSERT ON commerce.audit_record FROM nigson_commerce_app");
        try{assertThatThrownBy(()->complete(v.assetId(),"complete-audit")).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(repo.find(v.assetId(),false).state()).isEqualTo("UPLOADING");assertThat(repo.find(v.assetId(),false).scanCommand()).isNull();assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.outbox_event WHERE aggregate_id=?",Integer.class,v.assetId())).isZero();}finally{admin.execute("GRANT INSERT ON commerce.audit_record TO nigson_commerce_app");}
        assertThat(complete(v.assetId(),"complete-audit").state()).isEqualTo("QUARANTINED");
    }
    @Test void finalAuditFailureAfterCopyRecoversThroughExpiredLeaseWithoutAnotherCopy()throws Exception{
        var v=upload("final-audit");assertThat(fixture.put(v,bytes).statusCode()).isEqualTo(200);complete(v.assetId(),"complete");var input=scan(v.assetId(),"CLEAN");int copies=fixture.control(null).path("copies").asInt();
        var admin=new JdbcTemplate(new DriverManagerDataSource(System.getenv("NIGSON_TEST_JDBC_URL"),"nigson_bootstrap","mock-bootstrap"));
        admin.execute("CREATE FUNCTION commerce.synthetic_deny_file_scan_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='FILE_SCAN_RESULT' THEN RAISE EXCEPTION 'Synthetic audit denial' USING ERRCODE='42501'; END IF; RETURN NEW; END $$");
        admin.execute("CREATE TRIGGER synthetic_file_scan_audit BEFORE INSERT ON commerce.audit_record FOR EACH ROW EXECUTE FUNCTION commerce.synthetic_deny_file_scan_audit()");
        try{assertThatThrownBy(()->apply(v.assetId(),input,"scan")).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(repo.find(v.assetId(),false).state()).isEqualTo("QUARANTINED");assertThat(repo.find(v.assetId(),false).transferState()).isEqualTo("COPYING");}
        finally{admin.execute("DROP TRIGGER synthetic_file_scan_audit ON commerce.audit_record");admin.execute("DROP FUNCTION commerce.synthetic_deny_file_scan_audit()");}
        offset.set(16);assertThat(apply(v.assetId(),input,"scan").state()).isEqualTo("CLEAN");assertThat(fixture.control(null).path("copies").asInt()).isEqualTo(copies+1);
    }
    @Test void disabledStorageMakesNoIntentAndProviderCallsCannotRunInTransactions()throws Exception{
        var ds=new DriverManagerDataSource(System.getenv("NIGSON_TEST_JDBC_URL")+"?currentSchema=commerce","nigson_commerce_app","mock-commerce-runtime");var manager=new DataSourceTransactionManager(ds);var clock=Clock.systemUTC();var metrics=new SimpleMeterRegistry();
        try(var disabled=new FileConfiguration().fileStorage(new org.springframework.mock.env.MockEnvironment())){
            var inactive=new FileService(repo,disabled,new PlatformStore(jdbc,manager,clock,metrics),new AuditWriter(jdbc,clock,metrics),manager,clock,"test",metrics);
            assertThatThrownBy(()->inactive.upload(actor(editor),uploadInput(),"disabled",UUID.randomUUID())).hasMessage("FEATURE_DISABLED");assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.file_transfer WHERE owner_party_id=?",Integer.class,editor)).isZero();
        }
        int heads=fixture.control(null).path("heads").asInt();var tx=new org.springframework.transaction.support.TransactionTemplate(manager);
        assertThatThrownBy(()->tx.execute(status->fixture.storage.quarantine(FileValues.key("test","IMAGE",UUID.randomUUID(),0,"png")))).hasMessage("PROVIDER_HTTP_IN_TRANSACTION");assertThat(fixture.control(null).path("heads").asInt()).isEqualTo(heads);
    }
    @Test void twelveCompetingUploadCommandsPersistOneOwnedIntent()throws Exception{
        String key=UUID.randomUUID().toString();var start=new CyclicBarrier(12);try(var pool=Executors.newVirtualThreadPerTaskExecutor()){var futures=new ArrayList<Future<FileValues.UploadResult>>();for(int i=0;i<12;i++)futures.add(pool.submit(()->{start.await(5,TimeUnit.SECONDS);for(int attempt=0;attempt<20;attempt++)try{return upload(key);}catch(PlatformStore.Conflict busy){if(!busy.getMessage().equals("IDEMPOTENCY_IN_PROGRESS"))throw busy;Thread.sleep(60);}throw new IllegalStateException("BOUNDED_REPLAY_EXHAUSTED");}));var first=futures.getFirst().get(15,TimeUnit.SECONDS);for(var f:futures)assertThat(f.get(15,TimeUnit.SECONDS)).isEqualTo(first);}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.file_transfer WHERE owner_party_id=?",Integer.class,editor)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE created_by=? AND action='FILE_UPLOAD'",Integer.class,editor.toString())).isEqualTo(1);
    }
}
