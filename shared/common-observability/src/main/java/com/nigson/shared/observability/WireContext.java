package com.nigson.shared.observability;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.*;
import java.util.*;
/** Scoped protocol context only. SDK and persistence belong to the owning service. */
public final class WireContext implements AutoCloseable {
 public static final String TRACE_PATTERN="00-(?!0{32}-)[a-f0-9]{32}-(?!0{16}-)[a-f0-9]{16}-[a-f0-9]{2}";
 private static final ThreadLocal<WireContext> CURRENT=new ThreadLocal<>();
 private final WireContext previous; private final Scope scope;
 public final UUID correlationId; public final Tracer tracer;
 public WireContext(UUID correlationId,Tracer tracer,Context context){this.correlationId=Objects.requireNonNull(correlationId);this.tracer=Objects.requireNonNull(tracer);previous=CURRENT.get();scope=context.makeCurrent();CURRENT.set(this);}
 public static WireContext current(){return CURRENT.get();}
 public static String traceparent(){var s=Span.current().getSpanContext();return s.isValid()?"00-"+s.getTraceId()+"-"+s.getSpanId()+"-"+s.getTraceFlags().asHex():null;}
 public static Context parent(String input){if(input==null||!input.matches(TRACE_PATTERN))return Context.root();return Context.root().with(Span.wrap(SpanContext.createFromRemoteParent(input.substring(3,35),input.substring(36,52),TraceFlags.fromHex(input,53),TraceState.getDefault())));}
 public static UUID correlation(String input){try{if(input==null||!input.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))return UUID.randomUUID();var id=UUID.fromString(input);return id.equals(new UUID(0,0))?UUID.randomUUID():id;}catch(IllegalArgumentException bad){return UUID.randomUUID();}}
 @Override public void close(){try{scope.close();}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}}
}
