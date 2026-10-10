package com.nigson.acceptance.files;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import com.nigson.commerce.files.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class FileConfigurationTest {
    MockEnvironment input(String env){return new MockEnvironment().withProperty("APP_ENV",env).withProperty("NIGSON_FILES_ENABLED","true").withProperty("NIGSON_AWS_REGION","us-east-1").withProperty("NIGSON_S3_QUARANTINE_BUCKET","synthetic-quarantine").withProperty("NIGSON_S3_PUBLIC_BUCKET","synthetic-public").withProperty("NIGSON_S3_PRIVATE_BUCKET","synthetic-private");}
    @Test void productionRejectsLocalTargetsAndMissingApprovalWithoutFallback(){
        var factory=new FileConfiguration();assertThatThrownBy(()->factory.fileStorage(new MockEnvironment().withProperty("NIGSON_FILES_ENABLED","true"))).hasMessage("FILE_STORAGE_CONFIGURATION_REQUIRED");
        assertThatThrownBy(()->factory.fileStorage(input("prod"))).hasMessage("PRODUCTION_S3_CONFIGURATION_REQUIRED");
        assertThatThrownBy(()->factory.fileStorage(input("prod").withProperty("NIGSON_S3_LOCAL_ENDPOINT","https://localhost:9"))).hasMessage("PRODUCTION_S3_CONFIGURATION_REQUIRED");
        assertThatThrownBy(()->factory.fileStorage(input("prod").withProperty("NIGSON_S3_KMS_KEY","alias/unapproved"))).hasMessage("PRODUCTION_S3_CONFIGURATION_REQUIRED");
        assertThatThrownBy(()->factory.fileStorage(input("test").withProperty("NIGSON_S3_LOCAL_ENDPOINT","https://s3.us-east-1.amazonaws.com"))).hasMessage("LOCAL_S3_TARGET_REQUIRED");
        assertThatThrownBy(()->factory.fileStorage(input("test").withProperty("NIGSON_S3_LOCAL_ENDPOINT","http://localhost:9"))).hasMessage("LOCAL_S3_TARGET_REQUIRED");
        assertThatThrownBy(()->factory.fileStorage(input("test").withProperty("NIGSON_S3_PRIVATE_BUCKET","synthetic-public"))).hasMessage("FILE_STORAGE_BUCKET_INVALID");
    }
    @Test void disabledConfigurationCannotPretendProviderSuccess(){
        try(var storage=new FileConfiguration().fileStorage(new MockEnvironment())){assertThat(storage.configured()).isFalse();assertThatThrownBy(()->storage.quarantine("ignored")).hasMessage("FEATURE_DISABLED");assertThatThrownBy(()->storage.promote("ignored","ignored","ignored","ignored",false)).hasMessage("FEATURE_DISABLED");assertThatThrownBy(()->storage.download("ignored",true,Instant.now())).hasMessage("FEATURE_DISABLED");}
    }
}
