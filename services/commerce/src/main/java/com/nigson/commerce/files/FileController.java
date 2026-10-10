package com.nigson.commerce.files;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;
import com.nigson.shared.observability.WireContext;

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class FileController {
    private final FileService service;
    public FileController(FileService service){this.service=service;}
    private Object proof(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);return auth.getPrincipal();}
    private ActorAssertion.Actor actor(){if(!(proof() instanceof ActorAssertion.Actor a))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return a;}
    private String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
    private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
    private void query(HttpServletRequest r){if(!r.getParameterMap().isEmpty())throw ApiValues.invalid();}
    @PostMapping(value="/api/v1/commerce/files/uploads",consumes="application/json",produces="application/json")
    public ResponseEntity<FileValues.UploadResult> upload(HttpServletRequest r,@RequestBody JsonNode input){query(r);var result=service.upload(actor(),input,header(r,"Idempotency-Key"),correlation());return ResponseEntity.created(java.net.URI.create("/api/v1/commerce/files/"+result.assetId()+"/completion")).eTag("0").header("Cache-Control","no-store").body(result);}
    @PostMapping(value="/api/v1/commerce/files/{id}/completion",consumes="application/json",produces="application/json")
    public ResponseEntity<FileValues.Asset> complete(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input){query(r);var result=service.complete(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),input,header(r,"Idempotency-Key"),correlation());return ResponseEntity.accepted().eTag(Long.toString(result.version())).header("Cache-Control","no-store").header("Location","/api/v1/commerce/files/"+result.id()+"/download").body(result);}
    @GetMapping(value="/api/v1/commerce/files/{id}/download",produces="application/json")
    public ResponseEntity<FileValues.Download> download(HttpServletRequest r,@PathVariable String id)throws java.io.IOException{query(r);if(r.getInputStream().read()!=-1)throw ApiValues.invalid();return ResponseEntity.ok().header("Cache-Control","no-store").body(service.download(actor(),ApiValues.uuid(id),correlation()));}
    @PostMapping(value="/internal/v1/files/{id}/scan-result",consumes="application/json",produces="application/json")
    public ResponseEntity<FileValues.Asset> scan(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input){query(r);if(!(proof() instanceof PrivateWorkload.Proof workload))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var result=service.scan(workload,ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),input,header(r,"Idempotency-Key"),correlation());return ResponseEntity.ok().eTag(Long.toString(result.version())).header("Cache-Control","no-store").body(result);}
}
