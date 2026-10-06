package com.nigson.mocks;

import java.net.*;
import java.net.http.*;
import java.time.Duration;

/** Internal-container admin command; no provider URLs or credentials are accepted. */
public final class AdminClient {
 public static void main(String[] args)throws Exception{
  if(args.length!=2||!java.util.Set.of("reset","configure","state").contains(args[0]))throw new IllegalArgumentException("ADMIN_COMMAND_INVALID");
  var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/__scenario/"+args[0])).timeout(Duration.ofSeconds(3));
  var response=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build().send(args[0].equals("state")?request.GET().build():request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(args[1])).build(),HttpResponse.BodyHandlers.ofString());
  if(response.statusCode()!=200)throw new IllegalStateException("MOCK_ADMIN_FAILED");System.out.println(response.body());
 }
}
