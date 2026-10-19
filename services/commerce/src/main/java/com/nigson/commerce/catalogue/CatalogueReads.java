package com.nigson.commerce.catalogue;

import java.util.*;
import java.time.Clock;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;

public final class CatalogueReads {
    private final CatalogueQueryRepository repository;private final CatalogueAuthority authority;private final JdbcTemplate jdbc;private final TransactionTemplate queries;private final Clock clock;private final int budget;
    private long second=-1;private int calls;
    public CatalogueReads(CatalogueQueryRepository repository,CatalogueAuthority authority,JdbcTemplate jdbc,TransactionTemplate queries,Clock clock,int budget){if(budget<1||budget>10000)throw new IllegalArgumentException("CATALOGUE_READ_BUDGET_INVALID");this.repository=repository;this.authority=authority;this.jdbc=jdbc;this.queries=queries;this.clock=clock;this.budget=budget;}
    private synchronized void admit(){long now=clock.instant().getEpochSecond();if(now!=second){second=now;calls=0;}if(++calls>budget)throw new Problem.Failure(ProblemCode.RATE_LIMITED);}
    public static String q(String q){String value=CatalogueValues.text(q,200,true);var words=java.util.regex.Pattern.compile("[\\p{L}\\p{N}_]+").matcher(value);int count=0;while(words.find())if(++count>20)throw ApiValues.invalid();return value;}
    public static String market(String market){if(market==null||!market.matches("[A-Z][A-Z0-9_-]{1,31}"))throw ApiValues.invalid();return market;}
    private <T>T query(java.util.function.Supplier<T> operation){
        try{return queries.execute(s->{jdbc.execute("SET LOCAL statement_timeout='1s'");return operation.get();});}
        catch(org.springframework.dao.DataAccessException failure){if(failure.getMostSpecificCause() instanceof java.sql.SQLException sql&&"57014".equals(sql.getSQLState()))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);throw failure;}
    }
    public CatalogueValues.Page<CatalogueValues.Summary> search(CatalogueQueryRepository.Search input){
        admit();market(input.market());q(input.q());ApiValues.page(input.page(),input.size());if(input.productType()!=null)CatalogueValues.text(input.productType(),100,false);return query(()->repository.search(input));
    }
    public CatalogueValues.Product product(UUID id,String market){admit();market(market);return query(()->repository.product(id,market));}
    public List<CatalogueValues.PublicCategory> categories(String market){admit();market(market);return query(()->repository.categories(market));}
    public List<CatalogueValues.PublicBrand> brands(){admit();return query(repository::brands);}
    public <T>T boundedQuery(java.util.function.Supplier<T> operation){admit();return query(operation);}
    public CatalogueValues.Page<CatalogueValues.ProductAdmin> staff(ActorAssertion.Actor actor,String q,String status,int page,int size,String sort){authority.require(actor,"CONTENT_EDITOR");String text=q(q);ApiValues.page(page,size);if(status!=null&&!Set.of("DRAFT","ACTIVE","ARCHIVED").contains(status))throw ApiValues.invalid();return query(()->{authority.require(actor,"CONTENT_EDITOR");return repository.staff(text,status,page,size,sort);});}
}
