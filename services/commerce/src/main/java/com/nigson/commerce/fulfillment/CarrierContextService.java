package com.nigson.commerce.fulfillment;
import java.util.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.shared.security.PrivateWorkload;
import com.nigson.shared.problem.*;
/** Scoped source-owned snapshot; all statements stay inside commerce, with no provider HTTP. */
public final class CarrierContextService {
 private final JdbcShipmentRepository repository;private final JdbcTemplate jdbc;private final TransactionTemplate read;
 public CarrierContextService(JdbcShipmentRepository repository,JdbcTemplate jdbc,PlatformTransactionManager manager){this.repository=repository;this.jdbc=jdbc;read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(3);}
 public CarrierShipmentContext context(PrivateWorkload.Proof proof,UUID id){
  if(proof==null||proof.actor()!=null||!"nigson-integration".equals(proof.client())||proof.subject()==null||!proof.subject().matches("[A-Za-z0-9._:-]{1,100}")||!PrivateWorkload.ROUTES.stream().anyMatch(r->r.api().equals("API-119")&&r.equals(proof.route())))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);ApiValues.uuid(id==null?null:id.toString());
  return read.execute(status->{jdbc.execute("SET LOCAL statement_timeout='2s'");return repository.context(id);});
 }
}
