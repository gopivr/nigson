package com.nigson.experience.files;

import com.nigson.experience.careers.CareerSecrets;
import com.nigson.experience.careers.CareerService;
import com.nigson.experience.platform.AuditWriter;
import com.nigson.experience.platform.PlatformStore;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.AuditValues;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** Mints public CAREER_CV capabilities; transfer, scan and later claim remain separate owned steps. */
public final class CareerUploadSessionService {
 public record UploadSession(String uploadToken,Instant expiresAt,long maximumBytes){@Override public String toString(){return "UploadSession[protected]";}}
 private record SessionReference(UUID id,String protectedToken,Instant expiresAt,long maximumBytes){@Override public String toString(){return "SessionReference[protected]";}}
 private static final long MAXIMUM_BYTES=5L*1024*1024;
 private static final long TTL_SECONDS=30*60;
 private final JdbcTemplate jdbc;private final PlatformStore store;private final AuditWriter audit;private final CareerSecrets secrets;private final CareerService.AntiSpamVerifier antiSpam;private final Clock clock;private final int retentionDays;private final String policyVersion,recipientGroup;
 public CareerUploadSessionService(JdbcTemplate jdbc,PlatformStore store,AuditWriter audit,CareerSecrets secrets,CareerService.AntiSpamVerifier antiSpam,Clock clock,int retentionDays,String policyVersion,String recipientGroup){this.jdbc=jdbc;this.store=store;this.audit=audit;this.secrets=secrets;this.antiSpam=antiSpam;this.clock=clock;this.retentionDays=retentionDays;this.policyVersion=policyVersion;this.recipientGroup=recipientGroup;}
 public UploadSession create(String antiSpamToken,String key,UUID correlation){if(!enabled())throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);if(antiSpamToken==null||antiSpamToken.isBlank()||antiSpamToken.length()>4096||antiSpamToken.chars().anyMatch(c->c<32||c==127))throw ApiValues.invalid();key=ApiValues.idempotencyKey(key);JsonNode request=EventCodec.JSON.valueToTree(Map.of("antiSpamToken",antiSpamToken));var existing=store.replayLocal("public:career","API-157",key,request);if(existing.isPresent())return from(existing.get().body());boolean accepted;try{accepted=antiSpam.verify(antiSpamToken,key);}catch(RuntimeException unavailable){throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}if(!accepted)throw new Problem.Failure(ProblemCode.FILES_RULE_VIOLATION);UUID id=EventCodec.uuid7(clock.instant());String token=secrets.uploadToken(),tokenHash=sha256(token);Instant expiresAt=clock.instant().plusSeconds(TTL_SECONDS);var saved=store.executeLocal("public:career","API-157",key,request,db->{db.update("INSERT INTO experience.upload_session(id,created_by,updated_by,owner_party_id,token_hash,purpose,expires_at,maximum_bytes) VALUES (?,?,?,NULL,?,'CAREER_CV',?,?)",id,"public:career","public:career",tokenHash,java.sql.Timestamp.from(expiresAt),MAXIMUM_BYTES);audit.append("public:career",AuditValues.ActorKind.SYSTEM,AuditValues.Action.FILE_UPLOAD,AuditValues.Resource.FILE,id,correlation(correlation),AuditValues.Reason.SECURITY_POLICY,null,Map.of("purpose","CAREER_CV","expiresAt",expiresAt.toString(),"maximumBytes",MAXIMUM_BYTES),null);var ref=new SessionReference(id,Base64.getEncoder().encodeToString(secrets.encryptUploadToken(id,token)),expiresAt,MAXIMUM_BYTES);return new PlatformStore.Response(201,EventCodec.JSON.valueToTree(ref),id);});return from(saved.body());}
 private boolean enabled(){return secrets!=null&&antiSpam!=null&&retentionDays>=1&&retentionDays<=3650&&policyVersion!=null&&!policyVersion.isBlank()&&policyVersion.length()<=80&&recipientGroup!=null&&!recipientGroup.isBlank()&&recipientGroup.length()<=1000&&recipientGroup.chars().noneMatch(c->c<32||c==127);}
 private UploadSession from(JsonNode body){try{if(body==null||!body.isObject()||!body.propertyNames().equals(Set.of("id","protectedToken","expiresAt","maximumBytes")))throw new IllegalArgumentException();var ref=EventCodec.JSON.treeToValue(body,SessionReference.class);if(ref.id()==null||ref.protectedToken()==null||ref.expiresAt()==null||ref.maximumBytes()!=MAXIMUM_BYTES)throw new IllegalArgumentException();String token=secrets.decryptUploadToken(ref.id(),Base64.getDecoder().decode(ref.protectedToken()));String stored=jdbc.queryForObject("SELECT token_hash FROM experience.upload_session WHERE id=? AND purpose='CAREER_CV' AND owner_party_id IS NULL",String.class,ref.id()).trim();if(!MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII),sha256(token).getBytes(StandardCharsets.US_ASCII)))throw new IllegalArgumentException();return new UploadSession(token,ref.expiresAt(),ref.maximumBytes());}catch(Exception invalid){throw new IllegalStateException("CAREER_UPLOAD_SESSION_RESPONSE_INVALID");}}
 private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));}catch(Exception failure){throw new IllegalStateException("CAREER_TOKEN_HASH_FAILED");}}
 private UUID correlation(UUID value){if(value!=null)return value;var wire=com.nigson.shared.observability.WireContext.current();return wire==null?com.nigson.shared.observability.WireContext.correlation(null):wire.correlationId;}
}
