package com.nigson.providers;

import com.nigson.shared.security.CallbackSignatures;
import com.nigson.shared.events.EventCodec;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Test-only HTTP/1.1 signature receiver. Records evidence, never applies domain state. */
public final class CallbackReceiver {
 public static void main(String[] args)throws Exception{
  var journal=new CopyOnWriteArrayList<Map<String,Object>>();var server=new ServerSocket(8080,20);
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
   while(true){Socket socket=server.accept();executor.submit(()->{try(socket){socket.setSoTimeout(4000);var in=socket.getInputStream();String first=line(in);String path=first.split(" ")[1];var headers=new TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER);for(int n=0;n<100;n++){String header=line(in);if(header.isEmpty())break;int colon=header.indexOf(':');if(colon<1)throw new IOException();headers.put(header.substring(0,colon),header.substring(colon+1).trim());}int size=Integer.parseInt(headers.getOrDefault("Content-Length","0"));if(size<0||size>65536)throw new IOException();byte[] bytes=in.readNBytes(size);if(bytes.length!=size)throw new IOException();int code=200;String response="{}";
    if(path.equals("/state"))response=EventCodec.JSON.writeValueAsString(journal);
    else{
     boolean valid=false;String state="";
     if(path.equals("/paystack")){valid=CallbackSignatures.paystack(bytes,headers.get("x-paystack-signature"),"sk_mock_nigson");if(valid)state=EventCodec.JSON.readTree(bytes).path("event").asString();}
     if(path.equals("/twilio")){
      var params=new TreeMap<String,String>();for(String p:new String(bytes,StandardCharsets.UTF_8).split("&")){var parts=p.split("=",2);if(parts.length!=2||params.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts[1],StandardCharsets.UTF_8))!=null)throw new IllegalArgumentException();}
      valid=CallbackSignatures.twilio("http://callback-receiver:8080/twilio",params,headers.get("X-Twilio-Signature"),"mock-twilio-token");if(valid)state=params.getOrDefault("MessageStatus",params.containsKey("Body")?"INBOUND":"");
     }
     code=valid?204:403;response=code==204?"":"{}";journal.add(Map.of("valid",valid,"state",state));if(journal.size()>128)journal.removeFirst();
    }
    byte[] body=response.getBytes(StandardCharsets.UTF_8);var out=socket.getOutputStream();out.write(("HTTP/1.1 "+code+" Result\r\nContent-Type: application/json\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();
   }catch(Exception invalid){System.getLogger("mock-callback").log(System.Logger.Level.WARNING,"CALLBACK_TEST_REJECTED");}});}
  }
 }
 private static String line(InputStream in)throws IOException {var out=new ByteArrayOutputStream();for(int n=0;n<8192;n++){int b=in.read();if(b<0)throw new EOFException();if(b==10)return out.toString(StandardCharsets.US_ASCII).replace("\r","");out.write(b);}throw new IOException("HEADER_LIMIT");}
}
