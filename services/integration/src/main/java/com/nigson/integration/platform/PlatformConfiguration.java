package com.nigson.integration.platform;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import io.micrometer.core.instrument.MeterRegistry;
@Configuration(proxyBeanMethods=false)
public class PlatformConfiguration {
 @Bean PlatformStore platformStore(JdbcTemplate jdbc,PlatformTransactionManager manager,MeterRegistry metrics) {
  return new PlatformStore(jdbc,manager,java.time.Clock.systemUTC(),metrics);
 }
}
