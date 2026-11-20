package com.nigson.commerce.affiliate;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.payment.PaymentPayloadCipher;
import com.nigson.commerce.order.OrderPolicy;
@Configuration(proxyBeanMethods=false) @ConditionalOnExpression("${NIGSON_PRODUCT_PHASE:1} >= 3")
public class AffiliatePayoutConfiguration {
 @Bean AffiliatePayouts affiliatePayouts(JdbcTemplate jdbc,AffiliateLinks links,AffiliateCommissions commissions,ObjectProvider<PaymentPayloadCipher> cipher,OrderPolicy policy,PlatformStore commands,AuditWriter audit,PlatformTransactionManager manager){return new AffiliatePayouts(jdbc,links,commissions,new AffiliateBankSecrets(cipher::getIfAvailable,policy.mode()),commands,audit,manager,Clock.systemUTC());}
}
