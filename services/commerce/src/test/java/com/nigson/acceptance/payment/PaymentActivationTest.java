package com.nigson.acceptance.payment;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.nigson.commerce.payment.*;
import static org.assertj.core.api.Assertions.*;

class PaymentActivationTest {
 @Test void productionCannotUseLocalReceiptKeyOrDisableSeparateApprovalWithoutReference(){String local=Base64.getEncoder().encodeToString(new byte[32]);assertThatThrownBy(()->PaymentReceiptKeys.load(null,"prod","r1","","",local,"{}")).hasMessage("PAYMENT_RECEIPT_KEY_POLICY_REQUIRED");assertThatThrownBy(()->new PaymentPolicy(true,"prod",false,"",3600)).hasMessage("PAYMENT_TWO_PERSON_APPROVAL_REQUIRED");assertThatThrownBy(()->PaymentReceiptKeys.load(null,"prod","r1","","","","{}")).hasMessage("PAYMENT_RECEIPT_KEY_POLICY_REQUIRED");}
 @Test void localKeysRequireExactPurposeConfigurationAndBoundedRotation(){String local=Base64.getEncoder().encodeToString(new byte[32]);assertThat(PaymentReceiptKeys.load(null,"test","r1","","",local,"{}")).isNotNull();assertThatThrownBy(()->PaymentReceiptKeys.load(null,"test","r1","kms","",local,"{}")).hasMessage("PAYMENT_RECEIPT_KEY_POLICY_REQUIRED");assertThatThrownBy(()->PaymentReceiptKeys.load(null,"test","r1","","",local,"{\"r0\":\"synthetic\"}")).hasMessage("PAYMENT_RECEIPT_KEY_POLICY_REQUIRED");assertThatThrownBy(()->PaymentReceiptKeys.load(null,"test","r1","","","%%%","{}")).hasMessage("PAYMENT_RECEIPT_KEY_POLICY_REQUIRED");assertThatThrownBy(()->PaymentReceiptKeys.load(null,"test","r1","","",local,"{} null")).isInstanceOf(IllegalArgumentException.class);}
}
