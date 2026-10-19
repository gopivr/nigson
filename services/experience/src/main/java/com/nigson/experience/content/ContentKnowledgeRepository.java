package com.nigson.experience.content;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.net.URI;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.shared.events.EventCodec;
import tools.jackson.databind.JsonNode;

/** Reads only the Experience-owned CMS aggregate. */
public final class ContentKnowledgeRepository {
    private final JdbcTemplate jdbc;

    public ContentKnowledgeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public JdbcTemplate jdbc() { return jdbc; }

    public ContentKnowledgeValues.ValidatedRevision validate(UUID itemId, int revision) {
        var rows = jdbc.query("""
                SELECT r.state, r.approved_by,
                       (i.status='ACTIVE' AND i.deleted_at IS NULL AND r.approved_by IS NOT NULL
                        AND r.state IN ('APPROVED','PUBLISHED')) AS approved,
                       (i.status='ACTIVE' AND i.deleted_at IS NULL AND i.current_published_revision_id=r.id
                        AND r.state='PUBLISHED' AND r.approved_by IS NOT NULL AND r.published_at IS NOT NULL
                        AND r.published_at<=CURRENT_TIMESTAMP
                        AND (r.scheduled_at IS NULL OR r.scheduled_at<=CURRENT_TIMESTAMP)) AS current
                  FROM experience.content_item i
                  JOIN experience.content_revision r ON r.content_item_id=i.id
                 WHERE i.id=? AND r.revision=?
                """, (row, index) -> new ContentKnowledgeValues.ValidatedRevision(itemId, revision,
                row.getBoolean("approved"), row.getBoolean("current")), itemId, revision);
        return rows.isEmpty() ? new ContentKnowledgeValues.ValidatedRevision(itemId, revision, false, false) : rows.getFirst();
    }

    public ContentKnowledgeValues.ContentKnowledge current(UUID itemId) {
        var rows = jdbc.query("""
                SELECT i.id, i.content_type, i.slug, i.locale, r.revision, r.title, r.body::text AS body,
                       r.metadata::text AS metadata, r.published_at, r.checksum_sha256
                  FROM experience.content_item i
                  JOIN experience.content_revision r ON r.id=i.current_published_revision_id AND r.content_item_id=i.id
                 WHERE i.id=? AND i.status='ACTIVE' AND i.deleted_at IS NULL
                   AND r.state='PUBLISHED' AND r.approved_by IS NOT NULL AND r.published_at IS NOT NULL
                   AND r.published_at<=CURRENT_TIMESTAMP
                   AND (r.scheduled_at IS NULL OR r.scheduled_at<=CURRENT_TIMESTAMP)
                 LIMIT 1
                """, this::content, itemId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private ContentKnowledgeValues.ContentKnowledge content(ResultSet row, int index) throws SQLException {
        JsonNode body = EventCodec.JSON.readTree(row.getString("body"));
        JsonNode metadata = EventCodec.JSON.readTree(row.getString("metadata"));
        String checksum = row.getString("checksum_sha256").trim();
        if (body == null || !body.isObject() || metadata == null || !metadata.isObject()
                || !checksum.matches("[a-f0-9]{64}") || !validBody(body) || !validMetadata(metadata))
            throw new IllegalStateException("CONTENT_KNOWLEDGE_INVALID");
        var assets=new LinkedHashSet<UUID>();
        for (var block:body.path("blocks")) for (String key:List.of("assetId")) {
            if (block.has(key) && !block.path(key).isNull()) assets.add(UUID.fromString(block.path(key).asString()));
        }
        for (UUID asset:assets) if (!Boolean.TRUE.equals(jdbcExistsAsset(asset))) throw new IllegalStateException("CONTENT_KNOWLEDGE_ASSET_INVALID");
        return new ContentKnowledgeValues.ContentKnowledge(row.getObject("id", UUID.class),
                row.getString("content_type"), row.getString("slug"), row.getString("locale"),
                row.getInt("revision"), row.getString("title"), body, metadata,
                row.getTimestamp("published_at").toInstant(), true, checksum, "PUBLIC");
    }

    private Boolean jdbcExistsAsset(UUID id) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM experience.file_asset WHERE id=? AND purpose='IMAGE' AND classification='PUBLIC' AND scan_status='CLEAN' AND owner_party_id IS NULL AND organization_id IS NULL)", Boolean.class, id);
    }

    private static boolean validBody(JsonNode body) {
        if (!fields(body, Set.of("schemaVersion","blocks")) || !body.path("schemaVersion").isIntegralNumber()
                || body.path("schemaVersion").intValue()!=1 || !body.path("blocks").isArray() || body.path("blocks").size()>200) return false;
        for (JsonNode block:body.path("blocks")) {
            if (!block.isObject() || !block.path("type").isString()) return false;
            Set<String> expected=switch(block.path("type").asString()) {
                case "TEXT" -> Set.of("type","text"); case "IMAGE" -> Set.of("type","assetId","alt");
                case "VIDEO" -> Set.of("type","provider","videoId","caption");
                case "PERSON" -> Set.of("type","name","title","bio","assetId");
                case "LINK" -> Set.of("type","label","url"); case "MAP" -> Set.of("type","channelKey");
                case "PRODUCT_LIST" -> Set.of("type","productIds"); case "FAQ" -> Set.of("type","question","answer");
                default -> Set.of();
            };
            if (expected.isEmpty() || !fields(block,expected)) return false;
            switch(block.path("type").asString()) {
                case "TEXT" -> { if(!text(block.path("text"),20000)||block.path("text").asString().matches("(?is).*(<\\s*(script|iframe|object|embed|style)|javascript:|\\bon[a-z]+\\s*=).*"))return false; }
                case "IMAGE" -> { if(!uuid(block.path("assetId"))||!text(block.path("alt"),300))return false; }
                case "VIDEO" -> { if(!oneOf(block.path("provider"),"YOUTUBE","VIMEO")||!pattern(block.path("videoId"),"[A-Za-z0-9_-]{1,100}")||!text(block.path("caption"),500))return false; }
                case "PERSON" -> { if(!text(block.path("name"),160)||!text(block.path("title"),160)||!text(block.path("bio"),10000)||!(block.path("assetId").isNull()||uuid(block.path("assetId"))))return false; }
                case "LINK" -> { if(!text(block.path("label"),160)||!https(block.path("url")))return false; }
                case "MAP" -> { if(!pattern(block.path("channelKey"),"[A-Za-z0-9][A-Za-z0-9._:-]{0,79}"))return false; }
                case "PRODUCT_LIST" -> { if(!block.path("productIds").isArray()||block.path("productIds").size()>30||block.path("productIds").valueStream().anyMatch(v->!uuid(v)))return false; }
                case "FAQ" -> { if(!text(block.path("question"),500)||!text(block.path("answer"),5000))return false; }
                default -> { return false; }
            }
        }
        return true;
    }

    private static boolean validMetadata(JsonNode value) {
        if(!fields(value,Set.of("title","description","canonicalUrl","robots","alternates","structuredData"))
                ||!text(value.path("title"),70)||!text(value.path("description"),180)
                ||!(value.path("canonicalUrl").isNull()||https(value.path("canonicalUrl")))
                ||!oneOf(value.path("robots"),"INDEX","NOINDEX")||!value.path("alternates").isArray()
                ||value.path("alternates").size()>30||!value.path("structuredData").isObject()
                ||value.path("structuredData").size()>50)return false;
        for(JsonNode alternate:value.path("alternates"))if(!fields(alternate,Set.of("locale","url"))
                ||!pattern(alternate.path("locale"),"[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")||!https(alternate.path("url")))return false;
        return true;
    }
    private static boolean fields(JsonNode value,Set<String> expected) { if(!value.isObject()||value.size()!=expected.size())return false;for(String field:value.propertyNames())if(!expected.contains(field))return false;return true; }
    private static boolean text(JsonNode value,int max) { return value.isString()&&value.asString().length()<=max&&value.asString().chars().noneMatch(c->c<32&&c!='\n'&&c!='\r'&&c!='\t'); }
    private static boolean pattern(JsonNode value,String pattern) { return value.isString()&&value.asString().matches(pattern); }
    private static boolean oneOf(JsonNode value,String... allowed) { return value.isString()&&Set.of(allowed).contains(value.asString()); }
    private static boolean uuid(JsonNode value) { try { return value.isString()&&!UUID.fromString(value.asString()).equals(new UUID(0,0)); } catch(IllegalArgumentException bad) { return false; } }
    private static boolean https(JsonNode value) { if(!value.isString()||value.asString().length()>2048)return false;try{URI uri=URI.create(value.asString());return "https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getFragment()==null;}catch(IllegalArgumentException invalid){return false;} }
}
