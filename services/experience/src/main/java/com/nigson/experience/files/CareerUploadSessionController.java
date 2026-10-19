package com.nigson.experience.files;

import com.nigson.shared.problem.ApiValues;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.observability.WireContext;

@RestController
public final class CareerUploadSessionController {
 private final CareerUploadSessionService service;
 public CareerUploadSessionController(CareerUploadSessionService service){this.service=service;}
 @PostMapping(value="/api/v1/career-upload-sessions",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
 public ResponseEntity<CareerUploadSessionService.UploadSession> create(HttpServletRequest request,@RequestBody JsonNode body){query(request);if(body==null||!body.isObject()||!body.propertyNames().equals(Set.of("antiSpamToken"))||!body.path("antiSpamToken").isString())throw ApiValues.invalid();String key=header(request);String antiSpamToken=body.path("antiSpamToken").asString();var wire=WireContext.current();UUID correlation=wire==null?WireContext.correlation(null):wire.correlationId;return ResponseEntity.status(201).header("Cache-Control","no-store").body(service.create(antiSpamToken,key,correlation));}
 private static String header(HttpServletRequest request){var values=Collections.list(request.getHeaders("Idempotency-Key"));if(values.size()!=1||values.getFirst().isBlank()||values.getFirst().length()>100)throw ApiValues.invalid();return ApiValues.idempotencyKey(values.getFirst());}
 private static void query(HttpServletRequest request){if(!request.getParameterMap().isEmpty())throw ApiValues.invalid();}
}
