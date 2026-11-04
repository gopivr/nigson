package com.nigson.commerce.fulfillment;

import java.util.*;import java.time.Instant;
import com.nigson.commerce.security.SessionCookies;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryQuoteControllerTest {
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
 @SuppressWarnings("unchecked") private ObjectProvider<DeliveryQuoteService> provider(){return mock(ObjectProvider.class);}
 private ActorAssertion.Actor login(){var actor=new ActorAssertion.Actor(UUID.randomUUID(),"https://identity.example.invalid","subject",null,Set.of("CUSTOMER"),Set.of(),null);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,List.of()));return actor;}
 @Test void disabledCartAndDistributorQuoteRoutesReportFeatureUnavailable(){
  login();var cookies=mock(SessionCookies.class);var controller=new DeliveryQuoteController(provider(),cookies);var request=new MockHttpServletRequest("POST","/api/v1/carts/"+UUID.randomUUID()+"/delivery-quotes");var input=EventCodec.JSON.valueToTree(Map.of("addressId",UUID.randomUUID()));
  assertThatThrownBy(()->controller.create(request,UUID.randomUUID().toString(),input)).isInstanceOfSatisfying(Problem.Failure.class,e->assertThat(e.code).isEqualTo(ProblemCode.FEATURE_DISABLED));
  assertThatThrownBy(()->controller.quote(request,UUID.randomUUID().toString(),input)).isInstanceOfSatisfying(Problem.Failure.class,e->assertThat(e.code).isEqualTo(ProblemCode.FEATURE_DISABLED));verifyNoInteractions(cookies);
 }
 @Test void disabledServiceStillRequiresAuthentication(){
  var controller=new DeliveryQuoteController(provider(),mock(SessionCookies.class));assertThatThrownBy(()->controller.create(new MockHttpServletRequest(),UUID.randomUUID().toString(),EventCodec.JSON.readTree("{}"))).isInstanceOfSatisfying(Problem.Failure.class,e->assertThat(e.code).isEqualTo(ProblemCode.AUTH_REQUIRED));
 }
 @Test void enabledRouteRetainsVersionSessionAndOwnerServiceDelegation(){
  var actor=login();var service=mock(DeliveryQuoteService.class);var provider=provider();when(provider.getIfAvailable()).thenReturn(service);var cookies=mock(SessionCookies.class);var controller=new DeliveryQuoteController(provider,cookies);UUID cart=UUID.randomUUID(),address=UUID.randomUUID();var request=new MockHttpServletRequest("POST","/api/v1/carts/"+cart+"/delivery-quotes");request.addHeader("If-Match","\"3\"");request.addHeader("Idempotency-Key","delivery-controller-test");when(cookies.read(request)).thenReturn("session");var expected=new DeliveryQuoteValues.Quote(UUID.randomUUID(),"FEDEX","MOCK_SERVICE","NGN","1500.00",Instant.now().plusSeconds(60),3,"MOCK");when(service.create(eq(actor),eq("session"),eq(cart),eq(3L),eq(new DeliveryQuoteValues.Input(address,null)),eq("delivery-controller-test"),any())).thenReturn(expected);
  var response=controller.create(request,cart.toString(),EventCodec.JSON.valueToTree(Map.of("addressId",address)));assertThat(response.getStatusCode().value()).isEqualTo(201);assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");assertThat(response.getBody()).isEqualTo(expected);
 }
}
