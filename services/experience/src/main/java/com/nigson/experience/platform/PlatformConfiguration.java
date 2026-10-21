package com.nigson.experience.platform;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import io.micrometer.core.instrument.MeterRegistry;
@Configuration(proxyBeanMethods=false)
public class PlatformConfiguration {
 @Bean PlatformStore platformStore(JdbcTemplate jdbc,PlatformTransactionManager manager,MeterRegistry metrics) {
  return new PlatformStore(jdbc,manager,java.time.Clock.systemUTC(),metrics);
 }
 @Bean WorkerControls workerControls(JdbcTemplate jdbc){return new WorkerControls(jdbc);}
 @Bean OwnerConfiguration ownerConfiguration(JdbcTemplate jdbc,PlatformTransactionManager manager,PlatformStore commands,AuditWriter audit,MeterRegistry metrics){
  var clock=java.time.Clock.systemUTC();var repository=new JdbcExperienceConfigurationRepository(jdbc,clock);
  return new OwnerConfiguration(repository,commands,audit,clock,java.util.List.of(new ExperienceWorkerControlsPolicy(),new QueueReplayPolicy()));
 }
}
