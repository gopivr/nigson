package com.nigson.integration.providers;

import com.nigson.shared.security.ProviderHttp;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.util.*;

/** SMTP provider transport; caller owns notification-job intent and uncertain delivery. */
public final class EmailAdapter {
 private final JavaMailSenderImpl sender=new JavaMailSenderImpl();private final String from;
 public EmailAdapter(String environment,ProviderHttp.Mode mode,String host,int port,String username,String password,String from) {
  if(!Set.of("local","test","dev","stage","prod").contains(environment)||mode==null)throw new IllegalArgumentException("SMTP_ENVIRONMENT_INVALID");
  boolean local=Set.of("local","test").contains(environment);
  if(mode==ProviderHttp.Mode.MOCK){if(!local||!(host.equals("mailpit")&&port==1025||environment.equals("test")&&host.equals("127.0.0.1")&&port>0))throw new IllegalArgumentException("SMTP_MOCK_TARGET_INVALID");}
  else {if(host==null||host.equals("mailpit")||host.equals("localhost")||host.equals("127.0.0.1")||port!=587||username==null||username.isBlank()||password==null||password.isBlank()||password.toLowerCase(Locale.ROOT).matches(".*(mock|dummy|placeholder).*"))throw new IllegalArgumentException("SMTP_CONFIGURATION_INVALID");sender.setUsername(username);sender.setPassword(password);}
  if(environment.equals("prod")&&mode!=ProviderHttp.Mode.LIVE)throw new IllegalArgumentException("SMTP_PRODUCTION_MODE_INVALID");
  if(from==null||!from.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("SMTP_SENDER_INVALID");this.from=from;
  sender.setHost(host);sender.setPort(port);var properties=sender.getJavaMailProperties();properties.setProperty("mail.smtp.connectiontimeout","2000");properties.setProperty("mail.smtp.timeout","8000");properties.setProperty("mail.smtp.writetimeout","8000");properties.setProperty("mail.smtp.auth",Boolean.toString(mode!=ProviderHttp.Mode.MOCK));properties.setProperty("mail.smtp.starttls.enable",Boolean.toString(mode!=ProviderHttp.Mode.MOCK));properties.setProperty("mail.smtp.starttls.required",Boolean.toString(mode!=ProviderHttp.Mode.MOCK));properties.setProperty("mail.smtp.ssl.checkserveridentity","true");
 }
 public void send(String recipient,String subject,String text){
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");
  if(recipient==null||recipient.length()>254||!recipient.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||subject==null||subject.length()>200||subject.contains("\r")||subject.contains("\n")||text==null||text.length()>32000)throw new IllegalArgumentException("EMAIL_INVALID");
  var message=new SimpleMailMessage();message.setFrom(from);message.setTo(recipient);message.setSubject(subject);message.setText(text);
  try{sender.send(message);}catch(RuntimeException failure){throw new ProviderHttp.Failure("SMTP_DELIVERY_UNCERTAIN",true,0);}
 }
}
