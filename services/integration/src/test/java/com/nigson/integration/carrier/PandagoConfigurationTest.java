package com.nigson.integration.carrier;

import com.nigson.integration.providers.PandagoAdapter;
import com.nigson.integration.providers.PartnerConfiguration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.nigson.integration.platform.AuditWriter;
import com.nigson.integration.platform.PlatformStore;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PandagoConfigurationTest {
    @TempDir Path directory;
    final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PartnerConfiguration.class, PandagoConfiguration.class);
    @Test void disabledProviderDoesNotRequireCredentials() { runner.run(context -> assertThat(context).doesNotHaveBean(PandagoAdapter.class)); }
    @Test void enabledProviderRequiresPrivateServiceSecurity() {
        runner.withPropertyValues("NIGSON_PANDAGO_ENABLED=true").run(context -> assertThat(context).hasFailed());
    }
    @Test void enabledProviderLoadsFileSecretWithoutAuthenticatingOrCreatingOrders() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var path = directory.resolve("mock-key.pem"); Files.writeString(path, "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        runner.withPropertyValues("NIGSON_PANDAGO_ENABLED=true", "NIGSON_SECURITY_ENABLED=true", "APP_ENV=test", "NIGSON_PANDAGO_APPROVAL=test-qualification", "NIGSON_PANDAGO_APPROVAL_UNTIL=2099-12-31T23:59:59Z", "PANDAGO_MODE=MOCK", "PANDAGO_BASE_URL=http://127.0.0.1:12345", "PANDAGO_AUTH_BASE_URL=http://127.0.0.1:12345", "PANDAGO_CLIENT_ID=mock-client", "PANDAGO_KEY_ID=mock-key", "PANDAGO_SCOPE=pandago.api.ng.*", "PANDAGO_CLIENT_VENDOR_ID=mock-outlet", "PANDAGO_PRIVATE_KEY_FILE=" + path).withBean(PlatformStore.class, () -> mock(PlatformStore.class)).withBean(AuditWriter.class, () -> mock(AuditWriter.class)).run(context -> assertThat(context).hasSingleBean(PandagoAdapter.class));
    }
    @Test void enabledProviderRejectsMissingFileInsteadOfFallingBackToMocks() {
        runner.withPropertyValues("NIGSON_PANDAGO_ENABLED=true", "NIGSON_SECURITY_ENABLED=true", "PANDAGO_PRIVATE_KEY_FILE=" + directory.resolve("absent.pem")).run(context -> assertThat(context).hasFailed());
    }
}
