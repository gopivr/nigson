package com.nigson.commerce.security;

import java.util.*;
import jakarta.servlet.http.Cookie;
import com.nigson.commerce.platform.ProblemWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicRetailPriceTest {
 private static final String PRICE="/api/v1/catalogue/skus/00000000-0000-0000-0000-000000000001/price";
 private boolean passes(String method,String path,String cookie,boolean organization)throws Exception {
  SecurityContextHolder.clearContext();
  var request=new MockHttpServletRequest(method,path);
  if(cookie!=null)request.setCookies(new Cookie("nigson-local-session",cookie));
  if(organization)request.addParameter("organizationId",UUID.randomUUID().toString());
  var response=new MockHttpServletResponse();
  var sessions=mock(JdbcSessionTrust.class);
  when(sessions.read(anyString(),eq(true))).thenThrow(com.nigson.shared.security.ActorAssertion.denied());
  var filter=new TrustFilter(null,sessions,new SessionCookies("local",true),null,new ProblemWriter("test",""),new BrowserBoundary("test",Set.of("https://bff.synthetic.test")));
  boolean[] reached={false};
  filter.doFilter(request,response,(req,res)->{reached[0]=true;assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();});
  if(!reached[0])assertThat(response.getStatus()).isEqualTo(401);
  return reached[0];
 }
 @Test void retailPriceRemainsPublicWithMissingMalformedOrExpiredSession()throws Exception {
  for(String cookie:Arrays.asList(null,"expired-session",UUID.randomUUID().toString()))assertThat(passes("GET",PRICE,cookie,false)).isTrue();
 }
 @Test void organizationPriceDoesNotBypassSessionValidation()throws Exception {
  assertThat(passes("GET",PRICE,"expired-session",true)).isFalse();
  assertThat(passes("GET",PRICE,UUID.randomUUID().toString(),true)).isFalse();
 }
 @Test void privateAndMutationRoutesKeepSessionValidation()throws Exception {
  assertThat(passes("POST",PRICE,"expired-session",false)).isFalse();
  assertThat(passes("GET","/api/v1/carts","expired-session",false)).isFalse();
 }
}
