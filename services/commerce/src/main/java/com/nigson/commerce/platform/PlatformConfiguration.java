package com.nigson.commerce.platform;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import io.micrometer.core.instrument.MeterRegistry;
@Configuration(proxyBeanMethods=false)
public class PlatformConfiguration {
 @Bean OwnerConfiguration ownerConfiguration(JdbcTemplate jdbc,PlatformStore commands,AuditWriter audit,java.util.List<ConfigurationPolicy> policies) {
  return new OwnerConfiguration(new JdbcConfigurationRepository(jdbc,java.time.Clock.systemUTC()),commands,audit,java.time.Clock.systemUTC(),policies);
 }
 @Bean PlatformStore platformStore(JdbcTemplate jdbc,PlatformTransactionManager manager,MeterRegistry metrics) {
  return new PlatformStore(jdbc,manager,java.time.Clock.systemUTC(),metrics);
 }
}
