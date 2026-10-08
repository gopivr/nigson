package com.nigson.integration.platform;
import com.nigson.shared.problem.*;
import com.nigson.shared.observability.*;
import com.nigson.shared.security.ProviderHttp;
import com.nigson.shared.events.EventCodec;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.net.URI;
import java.util.*;
import java.io.IOException;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.web.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
/** Safe wire mapping. Never serializes exception messages, queries or protected IDs. */
public final class ProblemWriter {
 private static final Logger LOG=LoggerFactory.getLogger(ProblemWriter.class);private final String typeBase;
 public ProblemWriter(String env,String configured){
  if(configured==null||configured.isBlank()){if(!Set.of("local","test").contains(env))throw new IllegalArgumentException("PROBLEM_TYPE_BASE_REQUIRED");typeBase="urn:nigson:problem:";}
  else{URI uri=URI.create(configured);if(!uri.isAbsolute()||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||(!Set.of("local","test").contains(env)&&(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getHost().endsWith(".example"))))throw new IllegalArgumentException("PROBLEM_TYPE_BASE_INVALID");typeBase=configured.endsWith("/")||configured.endsWith(":")?configured:configured+"/";}
 }
 public ProblemCode classify(Throwable failure){
  if(failure instanceof Problem.Failure safe)return safe.code;
  if(failure instanceof org.springframework.security.access.AccessDeniedException)return ProblemCode.ACCESS_DENIED;
  if(failure instanceof org.springframework.security.core.AuthenticationException)return ProblemCode.AUTH_REQUIRED;
  if(failure instanceof PlatformStore.Conflict conflict)return switch(conflict.getMessage()){case "IDEMPOTENCY_HASH_MISMATCH"->ProblemCode.IDEMPOTENCY_HASH_MISMATCH;case "IDEMPOTENCY_IN_PROGRESS"->ProblemCode.IDEMPOTENCY_IN_PROGRESS;default->ProblemCode.CONFLICT;};
  if(failure instanceof ProviderHttp.Failure provider)return provider.uncertain?ProblemCode.PROVIDER_OUTCOME_UNKNOWN:ProblemCode.PROVIDER_UNAVAILABLE;
  if(failure instanceof org.springframework.orm.ObjectOptimisticLockingFailureException)return ProblemCode.VERSION_MISMATCH;
  if(failure instanceof DataIntegrityViolationException)return ProblemCode.CONFLICT;
  if(failure instanceof NoResourceFoundException)return ProblemCode.NOT_FOUND;
  if(failure instanceof HttpRequestMethodNotSupportedException)return ProblemCode.METHOD_NOT_ALLOWED;
  if(failure instanceof HttpMediaTypeNotSupportedException)return ProblemCode.UNSUPPORTED_MEDIA_TYPE;
  if(failure instanceof org.springframework.http.converter.HttpMessageNotReadableException||failure instanceof org.springframework.web.bind.MethodArgumentNotValidException||failure instanceof org.springframework.web.bind.ServletRequestBindingException||failure instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException||failure instanceof org.springframework.web.method.annotation.HandlerMethodValidationException||failure instanceof jakarta.validation.ConstraintViolationException)return ProblemCode.VALIDATION_ERROR;
  return ProblemCode.INTERNAL_ERROR;
 }
 public void write(HttpServletRequest request,HttpServletResponse response,ProblemCode code,List<Problem.Violation> violations)throws IOException {
  if(response.isCommitted())return;var context=WireContext.current();UUID correlation=context==null?WireContext.correlation(null):context.correlationId;var span=io.opentelemetry.api.trace.Span.current();span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);String trace=span.getSpanContext().isValid()?span.getSpanContext().getTraceId():null;
  var problem=new Problem(typeBase+code.name().toLowerCase(Locale.ROOT).replace('_','-'),code.title,code.status,code.detail,SafeRoutes.instance(request.getRequestURI()),code.name(),Instant.now(),correlation,trace,violations);
  response.resetBuffer();response.setStatus(code.status);response.setContentType("application/problem+json");response.setHeader("Cache-Control","no-store");response.setHeader("X-Correlation-ID",correlation.toString());if(code==ProblemCode.IDEMPOTENCY_IN_PROGRESS||code==ProblemCode.RATE_LIMITED)response.setHeader("Retry-After","1");if(code==ProblemCode.AUTH_REQUIRED)response.setHeader("WWW-Authenticate","Bearer");
  MDC.put("error_code",code.name());LOG.warn("REQUEST_FAILED");response.getOutputStream().write(EventCodec.JSON.writeValueAsBytes(problem));
 }
}
