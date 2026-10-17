package com.nigson.commerce.notification;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.nigson.commerce.security.SessionCookies;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.events.EventCodec;
@RestController
@ConditionalOnProperty(name="NIGSON_MESSAGING_STAFF_REPLY_ENABLED",havingValue="true")
public final class StaffReplyController {
 private final StaffReplyRelay relay;private final SessionCookies cookies;
 public StaffReplyController(StaffReplyRelay relay,SessionCookies cookies){this.relay=relay;this.cookies=cookies;}
 @PostMapping(value="/api/v1/staff/leads/{id}/messages",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
 public ResponseEntity<StaffReplyValues.Acknowledgement> send(HttpServletRequest request,@PathVariable String id,@RequestBody byte[] bytes){if(!request.getParameterMap().isEmpty()||bytes==null||bytes.length>8192)throw ApiValues.invalid();var idem=Collections.list(request.getHeaders("Idempotency-Key"));if(idem.size()!=1)throw ApiValues.invalid();tools.jackson.databind.JsonNode body;try{body=EventCodec.JSON.readTree(bytes);}catch(RuntimeException invalid){throw ApiValues.invalid();}UUID lead=ApiValues.uuid(id),command=ApiValues.uuid(idem.getFirst());var values=StaffReplyValues.parse(body,lead,command);var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof ActorAssertion.Actor actor))throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);UUID correlation=WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;var result=relay.send(actor,cookies.read(request),values,correlation);return ResponseEntity.accepted().header("Cache-Control","no-store").body(result);}
}
