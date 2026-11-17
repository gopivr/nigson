package com.nigson.commerce.wishlist;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.commerce.party.PartyRepository;
import com.nigson.commerce.platform.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public class WishlistConfiguration {
 @Bean WishlistRepository wishlistRepository(JdbcTemplate jdbc){return new JdbcWishlistRepository(jdbc);}
 @Bean WishlistService wishlistService(WishlistRepository repository,PartyRepository parties,PlatformStore commands,AuditWriter audit,PlatformTransactionManager manager){var tx=new TransactionTemplate(manager);tx.setTimeout(3);return new WishlistService(repository,parties,commands,audit,tx);}
}
