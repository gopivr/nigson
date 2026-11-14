package com.nigson.acceptance.pricing;

import com.nigson.commerce.pricing.*;
import com.nigson.commerce.platform.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.problem.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PriceBookReadTest {
 @Test @SuppressWarnings("unchecked") void bindingAndPageShareAnAuthorizedReadTransaction() {
  var jdbc=mock(JdbcTemplate.class);var repository=mock(PriceBookRepository.class);var authority=mock(PricingAuthority.class);
  var commands=mock(PlatformStore.class);var audit=mock(AuditWriter.class);var manager=mock(PlatformTransactionManager.class);
  when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
  var query=new PricingValues.Query("RETAIL","NGN",0,100,"code,asc");
  when(repository.list(query)).thenReturn(new PricingValues.Page(List.of(),0,100,0,0));
  var binding=new PricingValues.RetailBinding(UUID.randomUUID(),"NG",UUID.randomUUID(),7);
  when(jdbc.query(eq("SELECT id,code,retail_price_book_id,version FROM commerce.market WHERE code='NG'"),any(RowMapper.class))).thenReturn(List.of(binding));
  var service=new PriceBookService(repository,authority,commands,audit,Clock.systemUTC(),jdbc,manager,new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
  var actor=mock(ActorAssertion.Actor.class);
  var page=service.list(actor,query);
  assertThat(page.retailBindings()).containsExactly(binding);
  verify(authority,times(2)).requirePriceBook(actor,"SALES");
  verify(manager).getTransaction(argThat(def->def.isReadOnly()&&def.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));
  verify(manager).commit(any());verifyNoInteractions(commands,audit);
 }
 @Test void unauthorizedReadDoesNotReachTheDatabase() {
  var jdbc=mock(JdbcTemplate.class);var repository=mock(PriceBookRepository.class);var authority=mock(PricingAuthority.class);
  var manager=mock(PlatformTransactionManager.class);var actor=mock(ActorAssertion.Actor.class);
  doThrow(new Problem.Failure(ProblemCode.ACCESS_DENIED)).when(authority).requirePriceBook(actor,"SALES");
  var service=new PriceBookService(repository,authority,mock(PlatformStore.class),mock(AuditWriter.class),Clock.systemUTC(),jdbc,manager,new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
  assertThatThrownBy(()->service.list(actor,new PricingValues.Query("RETAIL","NGN",0,100,"code,asc"))).hasMessage("ACCESS_DENIED");
  verifyNoInteractions(jdbc,repository,manager);
 }
}
