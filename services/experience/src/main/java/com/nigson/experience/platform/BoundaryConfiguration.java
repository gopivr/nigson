package com.nigson.experience.platform;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.boot.jackson.autoconfigure.*;
import io.micrometer.core.instrument.MeterRegistry;
@Configuration(proxyBeanMethods=false)
public class BoundaryConfiguration {
 @Bean static org.springframework.beans.factory.config.BeanPostProcessor observeDatabase(org.springframework.beans.factory.ObjectProvider<MeterRegistry> metrics){return new org.springframework.beans.factory.config.BeanPostProcessor(){public Object postProcessAfterInitialization(Object bean,String name){return bean instanceof javax.sql.DataSource source?new JdbcTrace(source,metrics::getObject):bean;}};}
 @Bean org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer<MeterRegistry> boundedMetricLabels(){return registry->registry.config().meterFilter(new MetricPolicy()).meterFilter(io.micrometer.core.instrument.config.MeterFilter.maximumAllowableMetrics(2048));}
 @Bean PlatformMetrics ownerMetrics(org.springframework.jdbc.core.JdbcTemplate jdbc,javax.sql.DataSource source,MeterRegistry metrics){return new PlatformMetrics(jdbc,source,metrics);}
 @Bean Object validateMetricTarget(@Value("${APP_ENV}")String env,@Value("${NIGSON_METRICS_EXPORT_ENABLED:false}")boolean enabled,@Value("${NIGSON_OTLP_METRICS_URL:}")String endpoint){if(enabled){var uri=java.net.URI.create(endpoint);if(!"/v1/metrics".equals(uri.getPath()))throw new IllegalArgumentException("METRICS_TARGET_REQUIRED");Telemetry.target(env,uri);}return new Object();}
 @Bean Telemetry telemetry(MeterRegistry metrics,@Value("${APP_ENV}")String env,@Value("${NIGSON_OTEL_ENDPOINT:}")String endpoint){return new Telemetry(metrics,env,endpoint);}
 @Bean ProblemWriter problemWriter(@Value("${APP_ENV}")String env,@Value("${NIGSON_PROBLEM_TYPE_BASE:}")String base){return new ProblemWriter(env,base);}
 @Bean FilterRegistrationBean<RequestBoundary> requestBoundary(Telemetry telemetry,ProblemWriter writer){var filter=new FilterRegistrationBean<>(new RequestBoundary(telemetry,writer));filter.setOrder(Ordered.HIGHEST_PRECEDENCE+10);return filter;}
 @Bean JsonMapperBuilderCustomizer strictJson(){return builder->builder.enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);}
 @Bean JsonFactoryBuilderCustomizer duplicateJsonFields(){return builder->builder.enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION);}
 @Bean AuditWriter auditWriter(org.springframework.jdbc.core.JdbcTemplate jdbc,MeterRegistry metrics){return new AuditWriter(jdbc,java.time.Clock.systemUTC(),metrics);}
}
