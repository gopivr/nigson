package com.nigson.shared.observability;
import java.util.*;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;
public final class SafeRoutes {
 private record Route(String template,Pattern pattern){}
 private static final List<Route> ROUTES;
 static {try(var source=SafeRoutes.class.getResourceAsStream("/observability/routes.json")){
  var routes=new ArrayList<Route>();for(var item:new JsonMapper().readTree(source)){String route=item.asString();StringBuilder pattern=new StringBuilder("^");for(String part:route.split("/")){if(part.isEmpty())continue;pattern.append('/').append(part.startsWith("{")?"[^/]{1,200}":Pattern.quote(part));}pattern.append('$');routes.add(new Route(route,Pattern.compile(pattern.toString())));}ROUTES=List.copyOf(routes);
 }catch(Exception missing){throw new ExceptionInInitializerError("ROUTE_CONTRACT_UNAVAILABLE");}}
 private SafeRoutes(){}
 public static String route(String path){if(path==null||path.length()>2048)return "UNKNOWN";return ROUTES.stream().filter(r->r.pattern().matcher(path).matches()).map(Route::template).findFirst().orElse("UNKNOWN");}
 public static String instance(String path){String route=route(path);return route.equals("UNKNOWN")?"/":route.replaceAll("\\{[^}]+\\}","_id");}
 public static String method(String method){return Set.of("GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS").contains(method)?method:"OTHER";}
}
