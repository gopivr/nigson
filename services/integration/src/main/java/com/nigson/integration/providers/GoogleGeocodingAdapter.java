package com.nigson.integration.providers;

import java.math.*;
import java.util.*;
import com.nigson.shared.security.ProviderHttp;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
import tools.jackson.databind.JsonNode;

/** Explicit Google geocoding; no city-centre fallback, personal-address cache or fake coordinates. */
public final class GoogleGeocodingAdapter implements GeocodingProvider {
 private final ProviderHttp http;private final String key;
 public GoogleGeocodingAdapter(String environment,ProviderHttp.Mode mode,String endpoint,String key){
  if(key==null||!key.matches("[A-Za-z0-9_-]{20,200}"))throw new IllegalArgumentException("GEOCODING_KEY_REQUIRED");
  if(mode!=ProviderHttp.Mode.MOCK&&!"https://maps.googleapis.com".equals(endpoint))throw new IllegalArgumentException("GEOCODING_TARGET_INVALID");
  this.key=key;http=new ProviderHttp(environment,mode,endpoint,"geocoding-mock",Set.of("maps.googleapis.com"),key);
 }
 private static Problem.Failure fail(ProblemCode code){return new Problem.Failure(code);}
 public Point resolve(Address address){
  if(address==null||!"NG".equals(address.countryCode())||address.line1()==null||address.line1().isBlank()||address.city()==null||address.city().isBlank())throw ApiValues.invalid();
  var parts=new ArrayList<String>();for(String value:Arrays.asList(address.line1(),address.line2(),address.city(),address.region(),address.postalCode(),"Nigeria"))if(value!=null&&!value.isBlank())parts.add(value);
  String query=ProviderHttp.form(Map.of("address",String.join(", ",parts),"components","country:NG","key",key));
  ProviderHttp.Result response;
  try{response=http.call("GET","/maps/api/geocode/json?"+query,"application/json","",null,false,262144);}
  catch(ProviderHttp.Failure failure){throw fail(failure.status==401||failure.status==403?ProblemCode.GEOCODING_ACCESS_DENIED:ProblemCode.GEOCODING_UNAVAILABLE);}
  JsonNode body;try{body=EventCodec.JSON.readTree(response.body());}catch(RuntimeException invalid){throw fail(ProblemCode.GEOCODING_UNAVAILABLE);}
  String status=body.path("status").asString();
  if("ZERO_RESULTS".equals(status))throw fail(ProblemCode.ADDRESS_NOT_LOCATED);
  if(Set.of("REQUEST_DENIED","OVER_DAILY_LIMIT").contains(status))throw fail(ProblemCode.GEOCODING_ACCESS_DENIED);
  if("OVER_QUERY_LIMIT".equals(status))throw fail(ProblemCode.GEOCODING_RATE_LIMITED);
  if(!"OK".equals(status)||!body.path("results").isArray())throw fail(ProblemCode.GEOCODING_UNAVAILABLE);
  var results=body.path("results");if(results.size()!=1)throw fail(ProblemCode.ADDRESS_NOT_LOCATED);
  var result=results.path(0);String precision=result.path("geometry").path("location_type").asString();
  if(result.path("partial_match").asBoolean(false)||!Set.of("ROOFTOP","RANGE_INTERPOLATED").contains(precision))throw fail(ProblemCode.ADDRESS_NOT_LOCATED);
  boolean nigeria=false;for(var component:result.path("address_components"))for(var type:component.path("types"))if("country".equals(type.asString())&&"NG".equals(component.path("short_name").asString()))nigeria=true;
  var location=result.path("geometry").path("location");if(!nigeria||!location.path("lat").isNumber()||!location.path("lng").isNumber())throw fail(ProblemCode.ADDRESS_NOT_LOCATED);
  var lat=location.path("lat").decimalValue();var lng=location.path("lng").decimalValue();if(lat.abs().compareTo(BigDecimal.valueOf(90))>0||lng.abs().compareTo(BigDecimal.valueOf(180))>0)throw fail(ProblemCode.GEOCODING_UNAVAILABLE);
  return new Point(lat.setScale(6,RoundingMode.HALF_UP),lng.setScale(6,RoundingMode.HALF_UP));
 }
}
