package com.nigson.shared.events;

import java.time.Instant;
import java.util.*;
import java.security.SecureRandom;
import java.security.MessageDigest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Wire validation only; no repositories or domain entities are shared. */
public final class EventCodec {
    public static final JsonMapper JSON = JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final JsonNode REGISTRY;
    static {
        try (var stream = EventCodec.class.getResourceAsStream("/events/registry.json")) {
            REGISTRY = JSON.readTree(Objects.requireNonNull(stream));
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    private EventCodec() {}
    public static UUID uuid7(Instant now) {
        return new UUID((now.toEpochMilli() << 16) | 0x7000 | RANDOM.nextInt(4096),
                (RANDOM.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L);
    }
    public static JsonNode decode(String body) {
        if (body == null || body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536)
            throw new IllegalArgumentException("EVENT_SIZE_INVALID");
        JsonNode node;
        try {node=JSON.readTree(body);} catch(RuntimeException invalid) {throw new IllegalArgumentException("EVENT_JSON_INVALID");}
        if (node == null || !node.isObject() || !node.path("event_type").isString()) throw new IllegalArgumentException("EVENT_INVALID");
        JsonNode contract = REGISTRY.get(node.path("event_type").asString());
        if (contract == null) throw new IllegalArgumentException("EVENT_TYPE_UNKNOWN");
        validate(node, contract.get("schema"));
        if (node.path("event_type").asString().equals("file.scan.requested.v1") && !node.path("producer").equals(node.path("payload").path("owner_service")))
            throw new IllegalArgumentException("EVENT_OWNER_MISMATCH");
        return node;
    }
    public static boolean subscribed(String owner, JsonNode event, int release) {
        JsonNode contract = REGISTRY.get(event.path("event_type").asString());
        return contract != null && contract.path("phase").asInt() <= release && (!owner.equals("ai") || release == 5)
                && contract.path("consumers").valueStream().anyMatch(n -> n.asString().equals(owner));
    }
    private static void validate(JsonNode value, JsonNode schema) {
        if (schema.has("anyOf")) {
            for (JsonNode branch : schema.get("anyOf")) { try { validate(value, branch); return; } catch (IllegalArgumentException ignored) {} }
            throw new IllegalArgumentException("EVENT_FIELD_INVALID");
        }
        if (schema.has("const") && !schema.get("const").equals(value)) throw new IllegalArgumentException("EVENT_CONSTANT_INVALID");
        if (schema.has("enum") && schema.get("enum").valueStream().noneMatch(value::equals)) throw new IllegalArgumentException("EVENT_ENUM_INVALID");
        String type = schema.path("type").asString("");
        boolean matches = switch(type) { case "object" -> value.isObject(); case "array" -> value.isArray(); case "string" -> value.isString(); case "integer" -> value.isIntegralNumber() && value.canConvertToLong(); case "boolean" -> value.isBoolean(); case "null" -> value.isNull(); default -> true; };
        if (!matches) throw new IllegalArgumentException("EVENT_FIELD_TYPE_INVALID");
        if (value.isObject() && schema.has("properties")) {
            for (JsonNode required : schema.path("required")) if (!value.has(required.asString())) throw new IllegalArgumentException("EVENT_FIELD_MISSING");
            value.properties().forEach(entry -> { JsonNode child = schema.path("properties").get(entry.getKey()); if(child == null) throw new IllegalArgumentException("EVENT_FIELD_UNKNOWN"); validate(entry.getValue(),child); });
        }
        if (value.isArray()) { if(value.size()>schema.path("maxItems").asInt(100)) throw new IllegalArgumentException("EVENT_ARRAY_TOO_LARGE"); for(JsonNode child:value) validate(child,schema.path("items")); }
        if (value.isString()) {
            String text=value.asString();
            if(text.length()>schema.path("maxLength").asInt(65536) || text.length()<schema.path("minLength").asInt(0) || (schema.has("pattern") && !text.matches(schema.get("pattern").asString()))) throw new IllegalArgumentException("EVENT_STRING_INVALID");
            if(schema.path("format").asString("").equals("date-time")) { try { Instant.parse(text); } catch(Exception failure) { throw new IllegalArgumentException("EVENT_TIME_INVALID"); } }
        }
        if(value.isIntegralNumber() && (value.asLong()<schema.path("minimum").asLong(Long.MIN_VALUE) || value.asLong()>schema.path("maximum").asLong(Long.MAX_VALUE))) throw new IllegalArgumentException("EVENT_INTEGER_INVALID");
    }
    public static String digest(String body) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA256_UNAVAILABLE");}
    }
    public static String hash(JsonNode input) {
        if(input==null || JSON.writeValueAsBytes(input).length>65536) throw new IllegalArgumentException("REQUEST_SIZE_INVALID");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(canonical(input)))); }
        catch(Exception failure) { throw new IllegalArgumentException("REQUEST_HASH_INVALID", failure); }
    }
    private static Object canonical(JsonNode input) {
        if(input.isObject()) { var sorted=new TreeMap<String,Object>(); input.properties().forEach(e -> sorted.put(e.getKey(),canonical(e.getValue()))); return sorted; }
        if(input.isArray()) { var list=new ArrayList<Object>(); input.forEach(n -> list.add(canonical(n))); return list; }
        if(input.isNumber()) return input.decimalValue().stripTrailingZeros();
        if(input.isBoolean()) return input.asBoolean(); if(input.isNull()) return null; return input.asString();
    }
}
