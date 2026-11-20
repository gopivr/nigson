package com.nigson.experience.distributor;

import com.nigson.experience.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Experience-owned distributor state; commerce is reached only through the
 * post-commit entitlement port.
 */
public final class DistributorService {
  public interface EntitlementPort {
    EntitlementAck execute(EntitlementCommand command) throws Exception;
  }

  public record EntitlementCommand(UUID commandId, UUID applicationId, UUID partyId, String organizationName,
      String countryCode, String businessReference, String decision, UUID marketId, UUID priceBookId,
      long expectedVersion, String idempotencyKey, UUID correlationId, String partnerType) {
    public EntitlementCommand(UUID commandId, UUID applicationId, UUID partyId, String name, String country, String ref,
        String decision, UUID market, UUID book, long expected, String key, UUID correlation) {
      this(commandId, applicationId, partyId, name, country, ref, decision, market, book, expected, key, correlation,
          "DISTRIBUTOR");
    }
  }

  public record EntitlementAck(UUID commandId, UUID organizationId, String state, long revision) {
  }

  private final JdbcTemplate jdbc;
  private final PlatformStore store;
  private final AuditWriter audit;
  private final Clock clock;
  private final Set<String> requiredTypes;
  private final String recipientGroup;
  private final EntitlementPort commerce;

  public DistributorService(JdbcTemplate jdbc, PlatformStore store, AuditWriter audit, Clock clock,
      Set<String> requiredTypes, String recipientGroup, EntitlementPort commerce) {
    this.jdbc = jdbc;
    this.store = store;
    this.audit = audit;
    this.clock = clock;
    this.requiredTypes = Set.copyOf(requiredTypes);
    this.recipientGroup = recipientGroup;
    this.commerce = commerce;
  }

  private static PrivateWorkload.Route route(String api) {
    return PrivateWorkload.ROUTES.stream().filter(r -> r.api().equals(api) && r.owner().equals("experience"))
        .findFirst().orElseThrow(ActorAssertion::denied);
  }

  private ActorAssertion.Actor customer(PrivateWorkload.Proof p, String api) {
    var r = route(api);
    if (p == null || !r.equals(p.route()) || !"nigson-commerce".equals(p.client()) || p.actor() == null
        || p.actor().organization() != null || !p.actor().roles().contains("CUSTOMER")
        || !p.actor().scopes().contains(r.scope()))
      throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    return p.actor();
  }

  private ActorAssertion.Actor sales(PrivateWorkload.Proof p, String api) {
    var r = route(api);
    if (p == null || !r.equals(p.route()) || !"nigson-commerce".equals(p.client()) || p.actor() == null
        || p.actor().organization() != null || !p.actor().roles().contains("SALES")
        || !p.actor().scopes().contains(r.scope()))
      throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    return p.actor();
  }

  private static String who(UUID id) {
    return id.toString();
  }

  private static java.sql.Timestamp ts(Instant i) {
    return java.sql.Timestamp.from(i);
  }

  private static UUID correlation(UUID c) {
    return c == null ? WireCorrelation.current() : c;
  }

  private static final class WireCorrelation {
    static UUID current() {
      var w = com.nigson.shared.observability.WireContext.current();
      return w == null ? com.nigson.shared.observability.WireContext.correlation(null) : w.correlationId;
    }
  }

  public DistributorValues.Application submit(PrivateWorkload.Proof proof, DistributorValues.Submission input,
      String key, UUID corr) {
    var actor = customer(proof, "API-092");
    if (input == null || !input.countryCode().equals("NG")
        || (!input.partnerType().equals("AFFILIATE") && requiredTypes.isEmpty()) || recipientGroup == null
        || recipientGroup.isBlank())
      throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);
    ApiValues.idempotencyKey(key);
    if (input.documents().size() != (input.partnerType().equals("AFFILIATE") ? 0 : requiredTypes.size()))
      throw rule();
    var orderedTypes = requiredTypes.stream().sorted().toList();
    var typedDocuments = new ArrayList<DistributorValues.Document>();
    for (int i = 0; i < input.documents().size(); i++)
      typedDocuments.add(new DistributorValues.Document(input.documents().get(i).fileId(), orderedTypes.get(i)));
    UUID id = EventCodec.uuid7(clock.instant());
    var req = EventCodec.JSON.valueToTree(input);
    var saved = store.executeLocal(who(actor.party()), "API-092", key, req, db -> {
      var files = new ArrayList<Map<String, Object>>();
      for (var doc : typedDocuments) {
        var rows = db.queryForList(
            "SELECT id,owner_party_id,classification,scan_status,linked_resource_id FROM experience.file_asset WHERE id=? FOR UPDATE",
            doc.fileId());
        if (rows.size() != 1)
          throw new Problem.Failure(ProblemCode.NOT_FOUND);
        var f = rows.getFirst();
        if (!actor.party().equals(f.get("owner_party_id")) || !"PRIVATE".equals(f.get("classification"))
            || !"CLEAN".equals(f.get("scan_status")) || f.get("linked_resource_id") != null)
          throw rule();
        files.add(f);
      }
      Instant now = clock.instant();
      db.update(
          "INSERT INTO experience.distributor_application(id,created_by,updated_by,party_id,organization_name,country_code,business_reference,status,submitted_at,partner_type,business_details) VALUES (?,?,?,?,?,?,?,'SUBMITTED',?,?,?)",
          id, who(actor.party()), who(actor.party()), actor.party(), input.organizationName(), input.countryCode(),
          input.businessReference(), ts(now), input.partnerType(), input.businessDetails());
      for (int i = 0; i < typedDocuments.size(); i++) {
        var d = typedDocuments.get(i);
        db.update(
            "INSERT INTO experience.application_document(id,created_by,updated_by,application_id,file_asset_id,document_type) VALUES (?,?,?,?,?,?)",
            EventCodec.uuid7(now), who(actor.party()), who(actor.party()), id, d.fileId(), d.type());
        db.update(
            "UPDATE experience.file_asset SET linked_resource_type='DISTRIBUTOR_APPLICATION',linked_resource_id=?,version=version+1,updated_at=?,updated_by=? WHERE id=? AND linked_resource_id IS NULL",
            id, ts(now), who(actor.party()), d.fileId());
      }
      appendEvent(db, "distributor.application.created.v1", id, 0,
          Map.of("application_id", id, "recipient_group", recipientGroup), corr);
      audit.append(who(actor.party()), AuditValues.ActorKind.USER, AuditValues.Action.MODERATION,
          AuditValues.Resource.ENTITLEMENT, id, correlation(corr), AuditValues.Reason.OPERATOR_REQUEST, null,
          Map.of("status", "SUBMITTED", "documentCount", input.documents().size()), null);
      return new PlatformStore.Response(201, EventCodec.JSON.valueToTree(response(201, id, "SUBMITTED", null, now, 0)),
          id);
    });
    return EventCodec.JSON.treeToValue(saved.body(), DistributorValues.Application.class);
  }

  public List<DistributorValues.Application> mine(PrivateWorkload.Proof proof) {
    var actor = customer(proof, "API-093");
    return jdbc.query(
        "SELECT id,status,commerce_organization_id,submitted_at,version,partner_type FROM experience.distributor_application WHERE party_id=? ORDER BY submitted_at DESC,id DESC LIMIT 100",
        (r, n) -> row(r), actor.party());
  }

  public DistributorValues.Page queue(PrivateWorkload.Proof proof, String state, int page, int size, String sort) {
    sales(proof, "API-094");
    if (page < 0 || size < 1 || size > 100 || !(sort.equals("submittedAt,asc") || sort.equals("submittedAt,desc")))
      throw ApiValues.invalid();
    if (state != null && !Set.of("SUBMITTED", "UNDER_REVIEW", "ACTIVATION_PENDING", "APPROVED", "REJECTED",
        "SUSPENSION_PENDING", "SUSPENDED").contains(state))
      throw ApiValues.invalid();
    String direction = sort.endsWith("asc") ? "ASC" : "DESC";
    Long total = jdbc.queryForObject(
        "SELECT count(*) FROM experience.distributor_application" + (state == null ? "" : " WHERE status=?"),
        Long.class, state == null ? new Object[] {} : new Object[] { state });
    var rows = jdbc.query(
        "SELECT id,status,commerce_organization_id,submitted_at,version,partner_type FROM experience.distributor_application"
            + (state == null ? "" : " WHERE status=?") + " ORDER BY submitted_at " + direction + ",id LIMIT ? OFFSET ?",
        (r, n) -> row(r),
        state == null ? new Object[] { size, page * size } : new Object[] { state, size, page * size });
    return new DistributorValues.Page(rows, page, size, total, (int) Math.ceil((double) total / size), sort);
  }

  public DistributorValues.Detail detail(PrivateWorkload.Proof proof, UUID id) {
    sales(proof, "API-177");
    var rows = jdbc.queryForList(
        "SELECT id,partner_type,business_details,organization_name,country_code,business_reference,status,commerce_organization_id,version,reviewer_party_id,review_reason,decision_payload->>'reason' AS decision_reason FROM experience.distributor_application WHERE id=?",
        id);
    if (rows.size() != 1)
      throw new Problem.Failure(ProblemCode.NOT_FOUND);
    var a = rows.getFirst();
    var docs = jdbc.query(
        "SELECT d.file_asset_id,d.document_type,f.scan_status FROM experience.application_document d JOIN experience.file_asset f ON f.id=d.file_asset_id WHERE d.application_id=? ORDER BY d.id",
        (r, n) -> new DistributorValues.DocumentDetail(r.getObject(1, UUID.class), r.getString(2), r.getString(3)), id);
    return new DistributorValues.Detail(id, (String) a.get("organization_name"), (String) a.get("country_code"),
        (String) a.get("business_reference"), (String) a.get("status"), docs, (UUID) a.get("reviewer_party_id"),
        (String) a.get("review_reason"), (String) a.get("decision_reason"), (UUID) a.get("commerce_organization_id"),
        ((Number) a.get("version")).longValue(), (String) a.get("partner_type"), (String) a.get("business_details"));
  }

  public DistributorValues.Application review(PrivateWorkload.Proof proof, UUID id, String reason, boolean verified,
      long expected, String key, UUID corr) {
    var actor = sales(proof, "API-172");
    if (reason == null || reason.isBlank() || reason.length() > 500)
      throw ApiValues.invalid();
    var req = EventCodec.JSON
        .valueToTree(Map.of("id", id, "reason", reason, "documentsVerified", verified, "version", expected));
    var result = store.executeLocal(who(actor.party()), "API-172:" + id, key, req, db -> {
      var rows = db.queryForList("SELECT status,version FROM experience.distributor_application WHERE id=? FOR UPDATE",
          id);
      if (rows.size() != 1)
        throw new Problem.Failure(ProblemCode.NOT_FOUND);
      String state = (String) rows.getFirst().get("status");
      long v = ((Number) rows.getFirst().get("version")).longValue();
      if (v != expected)
        throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
      if (!state.equals("SUBMITTED") || !verified)
        throw rule();
      Long dirty = db.queryForObject(
          "SELECT count(*) FROM experience.application_document d JOIN experience.file_asset f ON f.id=d.file_asset_id JOIN experience.distributor_application a ON a.id=d.application_id WHERE a.id=? AND (f.owner_party_id IS DISTINCT FROM a.party_id OR f.classification IS DISTINCT FROM 'PRIVATE' OR f.scan_status IS DISTINCT FROM 'CLEAN' OR f.linked_resource_id IS DISTINCT FROM a.id)",
          Long.class, id);
      if (dirty != 0
          || jdbc.queryForObject("SELECT count(*) FROM experience.application_document WHERE application_id=?",
              Long.class, id) < requiredTypes.size())
        throw rule();
      long next = v + 1;
      Instant now = clock.instant();
      db.update(
          "UPDATE experience.distributor_application SET status='UNDER_REVIEW',reviewer_party_id=?,review_reason=?,version=?,updated_at=?,updated_by=? WHERE id=?",
          actor.party(), reason, next, ts(now), who(actor.party()), id);
      audit.append(who(actor.party()), AuditValues.ActorKind.USER, AuditValues.Action.MODERATION,
          AuditValues.Resource.ENTITLEMENT, id, correlation(corr), AuditValues.Reason.PUBLICATION_REVIEW,
          Map.of("status", state, "version", v),
          Map.of("status", "UNDER_REVIEW", "version", next, "documentsVerified", true), null);
      return new PlatformStore.Response(200,
          EventCodec.JSON.valueToTree(application(id, "UNDER_REVIEW", null, now, next)), id);
    });
    return EventCodec.JSON.treeToValue(result.body(), DistributorValues.Application.class);
  }

  public DistributorValues.Application decide(PrivateWorkload.Proof proof, UUID id, DistributorValues.Decision in,
      String key, long expected, UUID corr) {
    var actor = sales(proof, "API-095");
    ApiValues.idempotencyKey(key);
    if (in.decision().equals("RETRY"))
      return retryActivation(proof, actor, id, expected, corr);
    var req = EventCodec.JSON.valueToTree(Map.of("id", id, "decision", in.decision(), "reason", in.reason(), "marketId",
        in.marketId() == null ? "" : in.marketId().toString(), "priceBookId",
        in.priceBookId() == null ? "" : in.priceBookId().toString(), "version", expected));
    var oldRows = jdbc.queryForList(
        "SELECT party_id,partner_type,organization_name,country_code,business_reference,status,version,reviewer_party_id,decision_command_id,decision_payload,commerce_organization_id,submitted_at,acknowledgment_revision FROM experience.distributor_application WHERE id=?",
        id);
    if (oldRows.size() != 1)
      throw new Problem.Failure(ProblemCode.NOT_FOUND);
    var old = oldRows.getFirst();
    boolean affiliate = "AFFILIATE".equals(old.get("partner_type"));
    if (affiliate && in.priceBookId() != null)
      throw ApiValues.invalid();
    String prior = (String) old.get("status");
    long version = ((Number) old.get("version")).longValue();
    var replay = store.replayLocal(who(actor.party()), "API-095:" + id, key, req);
    boolean replaying = replay.isPresent();
    if (!replaying && version != expected)
      throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
    if (!replaying && in.decision().equals("APPROVE")
        && (affiliate ? !Set.of("SUBMITTED", "UNDER_REVIEW").contains(prior)
            : (!prior.equals("UNDER_REVIEW") || actor.party().equals(old.get("reviewer_party_id")))))
      throw rule();
    if (!replaying && in.decision().equals("REJECT") && !Set.of("SUBMITTED", "UNDER_REVIEW").contains(prior))
      throw rule();
    if (!replaying && in.decision().equals("SUSPEND") && !Set.of("APPROVED", "SUSPENSION_PENDING").contains(prior))
      throw rule();
    JsonNode priorPayload = EventCodec.JSON.readTree(old.get("decision_payload").toString());
    UUID market = in.marketId(), book = in.priceBookId();
    if (in.decision().equals("SUSPEND")) {
      if (market == null && priorPayload.path("marketId").isString())
        market = UUID.fromString(priorPayload.path("marketId").asString());
      if (book == null && priorPayload.path("priceBookId").isString())
        book = UUID.fromString(priorPayload.path("priceBookId").asString());
    }
    if (!in.decision().equals("REJECT") && market == null)
      throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);
    UUID priorCommand = (UUID) old.get("decision_command_id");
    boolean retryPending = (prior.equals("ACTIVATION_PENDING") && in.decision().equals("APPROVE"))
        || (prior.equals("SUSPENSION_PENDING") && in.decision().equals("SUSPEND"));
    if (replaying && !in.decision().equals(priorPayload.path("decision").asString()))
      throw new Problem.Failure(ProblemCode.IDEMPOTENCY_HASH_MISMATCH);
    UUID command = (retryPending || replaying) && priorCommand != null ? priorCommand
        : EventCodec.uuid7(clock.instant());
    String target = in.decision().equals("APPROVE") ? "ACTIVATION_PENDING"
        : in.decision().equals("SUSPEND") ? "SUSPENSION_PENDING" : "REJECTED";
    var payload = EventCodec.JSON.createObjectNode();
    payload.put("decision", in.decision());
    payload.put("reason", in.reason());
    payload.put("originalMarketId", in.marketId() == null ? "" : in.marketId().toString());
    payload.put("originalPriceBookId", in.priceBookId() == null ? "" : in.priceBookId().toString());
    if (market != null)
      payload.put("marketId", market.toString());
    if (book != null)
      payload.put("priceBookId", book.toString());
    UUID finalCommand = command;
    String finalTarget = target;
    UUID finalMarket = market, finalBook = book;
    var saved = store.executeLocal(who(actor.party()), "API-095:" + id, key, req, db -> {
      var locked = db
          .queryForList("SELECT status,version FROM experience.distributor_application WHERE id=? FOR UPDATE", id);
      if (locked.size() != 1)
        throw new Problem.Failure(ProblemCode.NOT_FOUND);
      long current = ((Number) locked.getFirst().get("version")).longValue();
      String state = (String) locked.getFirst().get("status");
      if (current != expected || !state.equals(prior))
        throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
      if (in.decision().equals("APPROVE")) {
        Long dirty = db.queryForObject(
            "SELECT count(*) FROM experience.application_document d JOIN experience.file_asset f ON f.id=d.file_asset_id JOIN experience.distributor_application a ON a.id=d.application_id WHERE a.id=? AND (f.owner_party_id IS DISTINCT FROM a.party_id OR f.classification IS DISTINCT FROM 'PRIVATE' OR f.scan_status IS DISTINCT FROM 'CLEAN' OR f.linked_resource_id IS DISTINCT FROM a.id)",
            Long.class, id);
        Long count = db.queryForObject("SELECT count(*) FROM experience.application_document WHERE application_id=?",
            Long.class, id);
        if (dirty != 0 || count < (affiliate ? 0 : requiredTypes.size()))
          throw rule();
      }
      int changed = db.update(
          "UPDATE experience.distributor_application SET status=?,decision_command_id=?,decision_payload=?::jsonb,version=version+1,updated_at=?,updated_by=? WHERE id=? AND version=?",
          finalTarget, finalCommand, EventCodec.JSON.writeValueAsString(payload), ts(clock.instant()),
          who(actor.party()), id, version);
      if (changed != 1)
        throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
      long next = version + 1;
      appendEvent(db, "distributor.decision.requested.v1", id, next,
          Map.of("application_id", id, "command_id", finalCommand, "decision", in.decision()), corr);
      audit.append(who(actor.party()), AuditValues.ActorKind.USER, AuditValues.Action.MODERATION,
          AuditValues.Resource.ENTITLEMENT, id, correlation(corr), AuditValues.Reason.OPERATOR_REQUEST,
          Map.of("status", prior), Map.of("status", finalTarget, "decision", in.decision()), null);
      return new PlatformStore.Response(202,
          EventCodec.JSON.valueToTree(application(id, finalTarget, (UUID) old.get("commerce_organization_id"),
              ((java.sql.Timestamp) old.get("submitted_at")).toInstant(), next)),
          id);
    });
    var pending = EventCodec.JSON.treeToValue(saved.body(), DistributorValues.Application.class);
    if (in.decision().equals("REJECT"))
      return pending;
    if (commerce == null)
      throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);
    try {
      long commerceVersion = old.get("acknowledgment_revision") == null ? 0
          : ((Number) old.get("acknowledgment_revision")).longValue();
      var ack = commerce.execute(new EntitlementCommand(finalCommand, id, (UUID) old.get("party_id"),
          (String) old.get("organization_name"), (String) old.get("country_code"),
          (String) old.get("business_reference"), in.decision().equals("APPROVE") ? "ACTIVATE" : "SUSPEND", finalMarket,
          finalBook, commerceVersion, key, correlation(corr), (String) old.get("partner_type")));
      String expectedState = in.decision().equals("APPROVE") ? "ACTIVE" : "SUSPENDED";
      if (!ack.commandId().equals(finalCommand) || ack.organizationId() == null || !ack.state().equals(expectedState))
        throw new IllegalStateException("ACK_MISMATCH");
      String acknowledged = in.decision().equals("APPROVE") ? "APPROVED" : "SUSPENDED";
      var fin = store.executeLocal("commerce-entitlement-ack", "API-117-ACK:" + id, finalCommand.toString(),
          EventCodec.JSON.valueToTree(ack), db -> {
            var current = db.queryForList(
                "SELECT status,commerce_organization_id,acknowledgment_revision,version,submitted_at FROM experience.distributor_application WHERE id=?",
                id);
            if (current.size() != 1)
              throw new Problem.Failure(ProblemCode.NOT_FOUND);
            var row = current.getFirst();
            if (acknowledged.equals(row.get("status"))
                && ack.organizationId().equals(row.get("commerce_organization_id"))
                && ack.revision() == ((Number) row.get("acknowledgment_revision")).longValue())
              return new PlatformStore.Response(202,
                  EventCodec.JSON.valueToTree(application(id, acknowledged, ack.organizationId(),
                      ((java.sql.Timestamp) row.get("submitted_at")).toInstant(),
                      ((Number) row.get("version")).longValue())),
                  id);
            int n = db.update(
                "UPDATE experience.distributor_application SET status=?,commerce_organization_id=?,acknowledgment_revision=?,version=version+1,updated_at=?,updated_by='commerce-entitlement-ack' WHERE id=? AND decision_command_id=? AND status=?",
                acknowledged, ack.organizationId(), ack.revision(), ts(clock.instant()), id, finalCommand, finalTarget);
            if (n != 1)
              throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
            return new PlatformStore.Response(202,
                EventCodec.JSON.valueToTree(
                    application(id, acknowledged, ack.organizationId(), pending.submittedAt(), pending.version() + 1)),
                id);
          });
      return EventCodec.JSON.treeToValue(fin.body(), DistributorValues.Application.class);
    } catch (Problem.Failure e) {
      if (in.decision().equals("APPROVE") && e.code == ProblemCode.AUTHORIZATION_RULE_VIOLATION) {
        store.executeLocal("commerce-entitlement-denied", "API-117-DENIED:" + id, finalCommand.toString(),
            EventCodec.JSON.valueToTree(Map.of("commandId", finalCommand, "code", e.code.name())), db -> {
              int changed = db.update(
                  "UPDATE experience.distributor_application SET status='UNDER_REVIEW',decision_payload=decision_payload || ?::jsonb,version=version+1,updated_at=?,updated_by='commerce-entitlement-denied' WHERE id=? AND decision_command_id=? AND status='ACTIVATION_PENDING'",
                  EventCodec.JSON.writeValueAsString(Map.of("activationFailureCode", e.code.name())),
                  ts(clock.instant()), id, finalCommand);
              if (changed == 1)
                audit.append("commerce-entitlement-denied", AuditValues.ActorKind.SERVICE,
                    AuditValues.Action.MODERATION, AuditValues.Resource.ENTITLEMENT, id, correlation(corr),
                    AuditValues.Reason.PUBLICATION_REVIEW, Map.of("status", "ACTIVATION_PENDING"),
                    Map.of("status", "UNDER_REVIEW", "code", e.code.name()), null);
              return new PlatformStore.Response(200, EventCodec.JSON.valueToTree(Map.of("state", "UNDER_REVIEW")), id);
            });
      }
      throw e;
    } catch (Exception e) {
      throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);
    }
  }

  private DistributorValues.Application retryActivation(PrivateWorkload.Proof proof, ActorAssertion.Actor actor,
      UUID id, long expected, UUID corr) {
    var rows = jdbc.queryForList(
        "SELECT status,version,decision_payload,reviewer_party_id,partner_type FROM experience.distributor_application WHERE id=?",
        id);
    if (rows.size() != 1)
      throw new Problem.Failure(ProblemCode.NOT_FOUND);
    var row = rows.getFirst();
    long current = ((Number) row.get("version")).longValue();
    if (current != expected)
      throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
    boolean suspension = "SUSPENSION_PENDING".equals(row.get("status")) && "AFFILIATE".equals(row.get("partner_type"));
    if ((!"ACTIVATION_PENDING".equals(row.get("status")) && !suspension) || current < 1
        || (!suspension && actor.party().equals(row.get("reviewer_party_id"))))
      throw rule();
    var payload = EventCodec.JSON.readTree(row.get("decision_payload").toString());
    var restored = com.nigson.shared.events.EventCodec.JSON.createObjectNode()
        .put("decision", payload.path("decision").asString()).put("reason", payload.path("reason").asString());
    for (String field : List.of("marketId", "priceBookId")) {
      String originalField = field.equals("marketId") ? "originalMarketId" : "originalPriceBookId";
      String value = payload.has(originalField) ? payload.path(originalField).asString()
          : payload.path(field).asString("");
      if (!value.isEmpty())
        restored.put(field, value);
    }
    var decision = DistributorValues.decision(restored);
    if (!decision.decision().equals(suspension ? "SUSPEND" : "APPROVE"))
      throw rule();
    long original = current - 1;
    var request = EventCodec.JSON.valueToTree(Map.of("id", id, "decision", decision.decision(), "reason",
        decision.reason(), "marketId", decision.marketId() == null ? "" : decision.marketId().toString(), "priceBookId",
        decision.priceBookId() == null ? "" : decision.priceBookId().toString(), "version", original));
    String hash = EventCodec.hash(request);
    var keys = jdbc.query(
        "SELECT request_key FROM experience.idempotency_record WHERE principal_key=? AND operation=? AND request_hash=? AND state='SUCCEEDED'",
        (r, n) -> r.getString(1), who(actor.party()), "API-095:" + id, hash);
    if (keys.size() != 1)
      throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    return decide(proof, id, decision, keys.getFirst(), original, corr);
  }

  private static Problem.Failure rule() {
    return new Problem.Failure(ProblemCode.BUSINESS_RULE_VIOLATION);
  }

  private DistributorValues.Application response(int status, UUID id, String state, UUID org, Instant submitted,
      long version) {
    return application(id, state, org, submitted, version);
  }

  private static DistributorValues.Application row(java.sql.ResultSet r) throws java.sql.SQLException {
    return new DistributorValues.Application(r.getObject("id", UUID.class), r.getString("status"),
        r.getObject("commerce_organization_id", UUID.class), r.getTimestamp("submitted_at").toInstant(),
        r.getLong("version"), r.getString("partner_type"));
  }

  private DistributorValues.Application application(UUID id, String state, UUID org, Instant at, long v) {
    String type = jdbc.queryForObject("SELECT partner_type FROM experience.distributor_application WHERE id=?",
        String.class, id);
    return new DistributorValues.Application(id, state, org, at, v, type);
  }

  private void appendEvent(JdbcTemplate db, String type, UUID aggregate, long version, Map<String, Object> payload,
      UUID corr) {
    var env = new LinkedHashMap<String, Object>();
    env.put("event_id", EventCodec.uuid7(clock.instant()));
    env.put("event_type", type);
    env.put("schema_version", 1);
    env.put("producer", "experience");
    env.put("occurred_at", clock.instant());
    env.put("aggregate_id", aggregate);
    env.put("aggregate_version", version);
    env.put("organization_id", null);
    env.put("correlation_id", correlation(corr));
    env.put("traceparent", null);
    env.put("payload", payload);
    store.append(EventCodec.JSON.writeValueAsString(env), "distributor-service");
  }
}
