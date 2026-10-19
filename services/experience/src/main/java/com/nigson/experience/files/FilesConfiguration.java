package com.nigson.experience.files;

import com.nigson.experience.careers.CareerSecrets;
import com.nigson.experience.careers.CareerService;
import com.nigson.experience.platform.AuditWriter;
import com.nigson.experience.platform.PlatformStore;
import java.time.Clock;
import java.util.Arrays;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false)
public class FilesConfiguration {
 @Bean CareerUploadSessionService careerUploadSessionService(JdbcTemplate jdbc,PlatformStore store,AuditWriter audit,ObjectProvider<CareerSecrets> secrets,ObjectProvider<CareerService.AntiSpamVerifier> spam,Environment e){return new CareerUploadSessionService(jdbc,store,audit,secrets.getIfAvailable(),spam.getIfAvailable(),Clock.systemUTC(),e.getProperty("NIGSON_CAREERS_RETENTION_DAYS",Integer.class,0),e.getProperty("NIGSON_CAREERS_PRIVACY_POLICY_VERSION",""),e.getProperty("NIGSON_CAREERS_SHEET_RECIPIENT_GROUP",""));}
}
