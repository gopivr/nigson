package com.nigson.commerce.catalogue;

import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import com.nigson.commerce.platform.*;

@Configuration(proxyBeanMethods=false)
public class CatalogueConfiguration {
    @Bean CatalogueAuthority catalogueAuthority(JdbcTemplate jdbc){return new CatalogueAuthority(jdbc);}
    @Bean TaxonomyService taxonomyService(JdbcTemplate jdbc,CatalogueAuthority authority,PlatformStore commands,AuditWriter audit){var clock=Clock.systemUTC();return new TaxonomyService(new JdbcTaxonomyRepository(jdbc,clock),authority,commands,audit,clock);}
    @Bean ProductService productService(JdbcTemplate jdbc,CatalogueAssets assets,CatalogueAuthority authority,PlatformStore commands,AuditWriter audit){var clock=Clock.systemUTC();return new ProductService(new JdbcProductRepository(jdbc,clock,assets),new JdbcTaxonomyRepository(jdbc,clock),authority,commands,audit,clock);}
    @Bean CatalogueReads catalogueReads(JdbcTemplate jdbc,CatalogueAssets assets,CatalogueAuthority authority,org.springframework.transaction.PlatformTransactionManager manager,@org.springframework.beans.factory.annotation.Value("${NIGSON_CATALOGUE_READ_BUDGET_PER_SECOND:1000}")int budget){var clock=Clock.systemUTC();var tx=new org.springframework.transaction.support.TransactionTemplate(manager);tx.setReadOnly(true);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);tx.setTimeout(3);return new CatalogueReads(new JdbcCatalogueQueries(jdbc,new JdbcProductRepository(jdbc,clock,assets),assets),authority,jdbc,tx,clock,budget);}
}
