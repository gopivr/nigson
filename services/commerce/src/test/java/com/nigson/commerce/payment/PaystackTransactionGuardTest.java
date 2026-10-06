package com.nigson.commerce.payment;

import com.nigson.commerce.payment.internal.infrastructure.external.paystack.PaystackAdapter;
import com.nigson.shared.security.ProviderHttp;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class PaystackTransactionGuardTest {
 @Test void providerCallsAreRejectedInsideActualOwnedTransactions(){
  String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"Real PostgreSQL qualification required");
  if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");
  var ds=new DriverManagerDataSource(url+"?currentSchema=commerce","nigson_commerce_app","mock-commerce-runtime");
  var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  var provider=new PaystackAdapter("test",ProviderHttp.Mode.MOCK,"http://127.0.0.1:1","sk_mock_nigson");
  assertThatThrownBy(()->tx.execute(status->{provider.initialize("forbidden","synthetic@example.invalid","1.00");return null;})).isInstanceOf(IllegalStateException.class).hasMessage("PROVIDER_CALL_INSIDE_TRANSACTION");
 }
}
