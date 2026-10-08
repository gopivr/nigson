package com.nigson.commerce.platform;
import com.nigson.shared.observability.*;
import com.nigson.shared.problem.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import io.micrometer.core.instrument.Timer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Before security. Preserves exact webhook bytes and refuses oversized mutation bodies. */
public final class RequestBoundary extends OncePerRequestFilter {
 private final Telemetry telemetry;private final ProblemWriter problems;
 public RequestBoundary(Telemetry telemetry,ProblemWriter problems){this.telemetry=telemetry;this.problems=problems;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  UUID correlation=WireContext.correlation(single(request,"X-Correlation-ID"));String route=SafeRoutes.route(request.getRequestURI());String method=SafeRoutes.method(request.getMethod());long began=System.nanoTime();
  try(var observation=telemetry.http(correlation,single(request,"traceparent"),route,method)){
   response.setHeader("X-Correlation-ID",correlation.toString());response.setHeader("Cache-Control","no-store");
   try{int limit=request.getRequestURI().startsWith("/api/v1/webhooks/")?262144:1048576;if(request.getContentLengthLong()>limit)throw new Problem.Failure(ProblemCode.BODY_TOO_LARGE);
    byte[] bytes=request.getInputStream().readNBytes(limit+1);if(bytes.length>limit)throw new Problem.Failure(ProblemCode.BODY_TOO_LARGE);if(bytes.length>0&&request.getContentType()!=null&&request.getContentType().split(";",2)[0].equalsIgnoreCase("application/json")&&!request.getRequestURI().startsWith("/api/v1/webhooks/"))ApiValues.jsonShape(bytes,100);chain.doFilter(new BytesRequest(request,bytes),response);
   }catch(Exception failure){problems.write(request,response,problems.classify(failure),List.of());}
   if(request.getRequestURI().startsWith("/api/")||request.getRequestURI().startsWith("/internal/")||request.getRequestURI().startsWith("/bff/")){telemetry.metrics.counter("nigson.http.requests","owner","commerce").increment();if(response.getStatus()>=500)telemetry.metrics.counter("nigson.http.errors","owner","commerce").increment();}
   observation.span.setAttribute("http.response.status_code",response.getStatus());if(response.getStatus()>=400)observation.failed("REQUEST_REJECTED");
   Timer.builder("nigson.http.server.requests").tags("owner","commerce","route",route,"method",method,"status",Integer.toString(response.getStatus())).register(telemetry.metrics).record(System.nanoTime()-began,java.util.concurrent.TimeUnit.NANOSECONDS);
   org.slf4j.LoggerFactory.getLogger(RequestBoundary.class).info("REQUEST_COMPLETE");
  }
 }
 private static String single(HttpServletRequest request,String key){var values=Collections.list(request.getHeaders(key));return values.size()==1?values.getFirst():null;}
 private static final class BytesRequest extends HttpServletRequestWrapper {
  private final byte[] bytes;BytesRequest(HttpServletRequest original,byte[] bytes){super(original);this.bytes=bytes;}
  public ServletInputStream getInputStream(){var input=new ByteArrayInputStream(bytes);return new ServletInputStream(){public int read(){return input.read();}public int read(byte[] b,int offset,int len){return input.read(b,offset,len);}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new IllegalStateException("ASYNC_BODY_READER_UNAVAILABLE");}};}
  public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
 }
}
