package com.nigson.commerce.pricing;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import com.nigson.commerce.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
public final class CheckoutQuoteService {
 private final CheckoutQuoteRepository repository;private final PricingAuthority authority;private final PlatformStore commands;private final AuditWriter audit;private final JdbcTemplate jdbc;private final io.micrometer.core.instrument.MeterRegistry metrics;
 public CheckoutQuoteService(CheckoutQuoteRepository repository,PricingAuthority authority,PlatformStore commands,AuditWriter audit,JdbcTemplate jdbc,io.micrometer.core.instrument.MeterRegistry metrics){this.repository=repository;this.authority=authority;this.commands=commands;this.audit=audit;this.jdbc=jdbc;this.metrics=metrics;}
 public PricingValues.Quote create(ActorAssertion.Actor actor,PricingValues.QuoteInput input,String key,UUID correlation){authority.require(actor,"CUSTOMER");repository.requireOwner(actor,input);ApiValues.idempotencyKey(key);if(correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();try{var response=commands.executeLocal(actor.party().toString(),"API-036",key,EventCodec.JSON.valueToTree(input),db->{jdbc.execute("SET LOCAL lock_timeout='500ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");authority.stabilize(actor,"CUSTOMER");var after=repository.create(actor,input);audit.append(actor.party().toString(),AuditValues.ActorKind.USER,AuditValues.Action.CHECKOUT_QUOTE,AuditValues.Resource.QUOTE,after.id(),correlation,AuditValues.Reason.OPERATOR_REQUEST,null,Map.of("version",after.cartVersion(),"amount",after.totals().grandTotal(),"currency",after.currency()),null);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){metrics.counter("nigson.pricing.commands","operation","QUOTE").increment();}});return new PlatformStore.Response(201,EventCodec.JSON.valueToTree(after),after.id());});var quote=EventCodec.JSON.treeToValue(response.body(),PricingValues.Quote.class);repository.requireReplay(actor,quote.id());return quote;}catch(org.springframework.dao.DataAccessException failure){for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof java.sql.SQLException sql&&Set.of("55P03","57014").contains(sql.getSQLState()))throw new Problem.Failure(ProblemCode.IDEMPOTENCY_IN_PROGRESS);throw failure;}}
}
