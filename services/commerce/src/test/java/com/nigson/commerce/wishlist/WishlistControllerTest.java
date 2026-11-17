package com.nigson.commerce.wishlist;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.nigson.shared.security.ActorAssertion;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class WishlistControllerTest {
 WishlistService service=mock(WishlistService.class);WishlistController controller=new WishlistController(service);
 UUID party=UUID.randomUUID(),sku=UUID.randomUUID();
 @BeforeEach void login(){var actor=new ActorAssertion.Actor(party,"https://identity.example.invalid","test",null,Set.of("CUSTOMER"),Set.of(),null);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,List.of()));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void noStoreAndAccountComesOnlyFromTrustedActor()throws Exception{when(service.list(any())).thenReturn(List.of());assertThat(controller.list(new MockHttpServletRequest()).getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");verify(service).list(argThat(a->a.party().equals(party)));}
 @Test void anonymousRequestsNeverReachService(){SecurityContextHolder.clearContext();assertThatThrownBy(()->controller.list(new MockHttpServletRequest())).hasMessage("AUTH_REQUIRED");verifyNoInteractions(service);}
 @Test void ownerQueryAndRequestBodyAreRejected(){var query=new MockHttpServletRequest();query.addParameter("partyId",party.toString());assertThatThrownBy(()->controller.list(query)).hasMessage("VALIDATION_ERROR");var body=new MockHttpServletRequest();body.setContent("{}".getBytes());assertThatThrownBy(()->controller.save(body,sku.toString())).hasMessage("VALIDATION_ERROR");verifyNoInteractions(service);}
 @Test void invalidSkuAndMissingOrRepeatedIdempotencyHeadersAreRejected(){var request=new MockHttpServletRequest();request.addHeader("Idempotency-Key","key");assertThatThrownBy(()->controller.save(request,"DISPLAY-SKU")).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->controller.save(new MockHttpServletRequest(),sku.toString())).hasMessage("VALIDATION_ERROR");request.addHeader("Idempotency-Key","second");assertThatThrownBy(()->controller.save(request,sku.toString())).hasMessage("VALIDATION_ERROR");verifyNoInteractions(service);}
}
