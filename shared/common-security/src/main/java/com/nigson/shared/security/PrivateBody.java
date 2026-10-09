package com.nigson.shared.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** A bounded repeatable private body binds actor proof to exact bytes, then owner DTO validation runs. */
public final class PrivateBody extends HttpServletRequestWrapper {
    private final byte[] bytes;
    public PrivateBody(HttpServletRequest request)throws IOException{super(request);if(request.getContentLengthLong()>1048576)throw ActorAssertion.denied();bytes=request.getInputStream().readNBytes(1048577);if(bytes.length>1048576)throw ActorAssertion.denied();}
    public String hash(){return ActorAssertion.Binding.bodyHash(bytes);}
    @Override public ServletInputStream getInputStream(){var in=new ByteArrayInputStream(bytes);return new ServletInputStream(){public int read(){return in.read();}public boolean isFinished(){return in.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("ASYNC_BODY_UNSUPPORTED");}};}
    @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
}
