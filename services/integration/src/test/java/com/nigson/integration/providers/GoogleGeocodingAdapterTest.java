package com.nigson.integration.providers;

import com.sun.net.httpserver.HttpServer;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ProviderHttp;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class GoogleGeocodingAdapterTest {
 private HttpServer server;private String body;private final List<String> paths=new ArrayList<>();private GoogleGeocodingAdapter adapter;
 private static final GeocodingProvider.Address ADDRESS=new GeocodingProvider.Address("12 Test street",null,"Lagos","Lagos",null,"NG");
 @BeforeEach void start()throws Exception {
  body=response("ROOFTOP",false,"NG");server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{paths.add(e.getRequestURI().getRawPath()+"?"+e.getRequestURI().getRawQuery());byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}});server.start();
  adapter=new GoogleGeocodingAdapter("test",ProviderHttp.Mode.MOCK,"http://127.0.0.1:"+server.getAddress().getPort(),"mock-google-geocoding-test-key");
 }
 @AfterEach void stop(){server.stop(0);}
 private static String response(String precision,boolean partial,String country){return EventCodec.JSON.writeValueAsString(Map.of("status","OK","results",List.of(Map.of("partial_match",partial,"geometry",Map.of("location_type",precision,"location",Map.of("lat",6.1234567,"lng",3.1234567)),"address_components",List.of(Map.of("short_name",country,"types",List.of("country")))))));}
 private void error(ProblemCode expected){assertThatThrownBy(()->adapter.resolve(ADDRESS)).isInstanceOfSatisfying(Problem.Failure.class,e->assertThat(e.code).isEqualTo(expected));}
 @Test void resolvesStreetAddressRestrictsNigeriaAndRoundsCoordinates(){var point=adapter.resolve(ADDRESS);assertThat(point.latitude()).isEqualByComparingTo(new BigDecimal("6.123457"));assertThat(point.longitude()).isEqualByComparingTo(new BigDecimal("3.123457"));assertThat(paths).hasSize(1);assertThat(URLDecoder.decode(paths.getFirst(),StandardCharsets.UTF_8)).contains("address=12 Test street, Lagos, Lagos, Nigeria","components=country:NG");assertThat(ADDRESS.toString()).doesNotContain("Test street");}
 @Test void rejectsPartialApproximateForeignAndAmbiguousMatches(){for(var value:List.of(response("ROOFTOP",true,"NG"),response("APPROXIMATE",false,"NG"),response("ROOFTOP",false,"US"))){body=value;error(ProblemCode.ADDRESS_NOT_LOCATED);}var value=(tools.jackson.databind.node.ObjectNode)EventCodec.JSON.readTree(response("ROOFTOP",false,"NG"));var array=(tools.jackson.databind.node.ArrayNode)value.path("results");array.add(array.path(0).deepCopy());body=EventCodec.JSON.writeValueAsString(value);error(ProblemCode.ADDRESS_NOT_LOCATED);}
 @Test void reportsNoMatchDeniedKeyQuotaAndInvalidResponseWithoutProviderText(){
  for(var item:Map.of("ZERO_RESULTS",ProblemCode.ADDRESS_NOT_LOCATED,"REQUEST_DENIED",ProblemCode.GEOCODING_ACCESS_DENIED,"OVER_DAILY_LIMIT",ProblemCode.GEOCODING_ACCESS_DENIED,"OVER_QUERY_LIMIT",ProblemCode.GEOCODING_RATE_LIMITED,"UNKNOWN_ERROR",ProblemCode.GEOCODING_UNAVAILABLE).entrySet()){body=EventCodec.JSON.writeValueAsString(Map.of("status",item.getKey(),"error_message","sensitive provider response"));error(item.getValue());}
  body="<html>private failure</html>";error(ProblemCode.GEOCODING_UNAVAILABLE);
 }
 @Test void rejectsNonNigeriaAndArbitraryExternalTargets(){assertThatThrownBy(()->adapter.resolve(new GeocodingProvider.Address("Test",null,"Test",null,null,"US"))).isInstanceOf(Problem.Failure.class);assertThat(paths).isEmpty();assertThatThrownBy(()->new GoogleGeocodingAdapter("local",ProviderHttp.Mode.LIVE,"https://untrusted.example","mock-google-geocoding-test-key")).isInstanceOf(IllegalArgumentException.class);}
}
