package com.nigson.acceptance.pricing;

import com.nigson.commerce.pricing.PricingAuthority;
import com.nigson.commerce.market.MarketAuthority;
import com.nigson.shared.security.ActorAssertion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class NormalAdminPricingTest {
 private final Clock clock=Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"),ZoneOffset.UTC);
 private ActorAssertion.Actor actor(Set<String> roles,Instant mfa,UUID organization){return new ActorAssertion.Actor(UUID.randomUUID(),"https://identity.test","subject",organization,roles,Set.of(),mfa);}
 private JdbcTemplate persisted(boolean active){var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(Boolean.class),any(Object[].class))).thenReturn(active);return jdbc;}
 @Test void adminWithoutMfaCanManagePriceBooksAndBindRetailPrices(){var jdbc=persisted(true);var admin=actor(Set.of("ADMIN"),null,null);var pricing=new PricingAuthority(jdbc,clock,3,"");for(String role:List.of("SALES","FINANCE"))assertThatCode(()->pricing.requirePriceBook(admin,role)).doesNotThrowAnyException();assertThatCode(()->new MarketAuthority(jdbc,clock).requireRetailBook(admin)).doesNotThrowAnyException();}
 @Test void staleMfaDoesNotBlockTheExplicitAdminPricingException(){var jdbc=persisted(true);var admin=actor(Set.of("ADMIN"),clock.instant().minusSeconds(3600),null);assertThatCode(()->new PricingAuthority(jdbc,clock,3,"").requirePriceBook(admin,"FINANCE")).doesNotThrowAnyException();assertThatCode(()->new MarketAuthority(jdbc,clock).requireRetailBook(admin)).doesNotThrowAnyException();}
 @Test void revokedOrUnmappedAdminCannotUseTheException(){var jdbc=persisted(false);var admin=actor(Set.of("ADMIN"),null,null);assertThatThrownBy(()->new PricingAuthority(jdbc,clock,3,"").requirePriceBook(admin,"FINANCE")).hasMessage("ACCESS_DENIED");assertThatThrownBy(()->new MarketAuthority(jdbc,clock).requireRetailBook(admin)).hasMessage("ACCESS_DENIED");}
 @Test void customerAndOrganizationActorsCannotUseNormalAdminPricing(){var jdbc=persisted(true);for(var denied:List.of(actor(Set.of("CUSTOMER"),null,null),actor(Set.of("ADMIN"),null,UUID.randomUUID()))){assertThatThrownBy(()->new PricingAuthority(jdbc,clock,3,"").requirePriceBook(denied,"FINANCE")).hasMessage("ACCESS_DENIED");assertThatThrownBy(()->new MarketAuthority(jdbc,clock).requireRetailBook(denied)).hasMessage("ACCESS_DENIED");}}
 @Test void anonymousActorsRemainUnauthorized(){var jdbc=persisted(true);assertThatThrownBy(()->new PricingAuthority(jdbc,clock,3,"").requirePriceBook(null,"FINANCE")).hasMessage("AUTH_REQUIRED");assertThatThrownBy(()->new MarketAuthority(jdbc,clock).requireRetailBook(null)).hasMessage("AUTH_REQUIRED");verifyNoInteractions(jdbc);}
 @Test void financeAndOtherMarketOperationsStillRequireFreshMfa(){var jdbc=persisted(true);var staff=actor(Set.of("ADMIN","FINANCE"),null,null);var pricing=new PricingAuthority(jdbc,clock,3,"");assertThatThrownBy(()->pricing.require(staff,"FINANCE")).hasMessage("ACTOR_TRUST_REJECTED");var market=new MarketAuthority(jdbc,clock);assertThatThrownBy(()->market.require(staff,"ADMIN")).hasMessage("ACTOR_TRUST_REJECTED");assertThatThrownBy(()->market.require(staff,"FINANCE")).hasMessage("ACTOR_TRUST_REJECTED");var finance=actor(Set.of("FINANCE"),null,null);assertThatThrownBy(()->pricing.requirePriceBook(finance,"FINANCE")).hasMessage("ACTOR_TRUST_REJECTED");assertThatThrownBy(()->market.requireRetailBook(finance)).hasMessage("ACTOR_TRUST_REJECTED");}
}
