package com.nigson.experience.lead;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InboundMessageSecretsTest {
    private byte[] key(int value){byte[] out=new byte[32];Arrays.fill(out,(byte)value);return out;}
    @Test void ciphertextIsAuthenticatedAndBoundToRecordAndPurpose(){
        var secrets=new InboundMessageSecrets(key(31));UUID id=UUID.randomUUID();byte[] body=secrets.encrypt(id,"BODY","private WhatsApp context");
        assertThat(secrets.decrypt(id,"BODY",body)).isEqualTo("private WhatsApp context");
        assertThatThrownBy(()->secrets.decrypt(UUID.randomUUID(),"BODY",body)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->secrets.decrypt(id,"SENDER",body)).isInstanceOf(IllegalArgumentException.class);
        body[body.length-1]^=1;assertThatThrownBy(()->secrets.decrypt(id,"BODY",body)).isInstanceOf(IllegalArgumentException.class);
        assertThat(secrets.toString()).doesNotContain("private");
    }
}
