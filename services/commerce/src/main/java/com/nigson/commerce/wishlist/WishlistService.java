package com.nigson.commerce.wishlist;
import java.util.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.commerce.party.PartyRepository;
import com.nigson.commerce.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
public final class WishlistService {
 private final WishlistRepository repository;
 private final PartyRepository parties;
 private final PlatformStore commands;
 private final AuditWriter audit;
 private final TransactionTemplate tx;
 public WishlistService(WishlistRepository repository,PartyRepository parties,PlatformStore commands,AuditWriter audit,TransactionTemplate tx){this.repository=repository;this.parties=parties;this.commands=commands;this.audit=audit;this.tx=tx;}
 public List<WishlistValues.Item> list(ActorAssertion.Actor actor){return tx.execute(s->{parties.requireCurrent(actor,false);return repository.list(actor.party());});}
 public void set(ActorAssertion.Actor actor,UUID sku,boolean saved,String key,UUID correlation){
  ApiValues.idempotencyKey(key);if(sku==null||sku.equals(new UUID(0,0))||correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();
  tx.executeWithoutResult(s->{
   // Serialize this account's bounded set and refresh identity even on idempotency replay.
   parties.requireCurrent(actor,true);
   commands.executeLocal(actor.party().toString(),saved?"API-217":"API-218",key,EventCodec.JSON.valueToTree(Map.of("skuId",sku)),db->{
    if(saved){
     if(!repository.eligible(sku))throw new Problem.Failure(ProblemCode.NOT_FOUND);
     if(!repository.contains(actor.party(),sku)&&repository.count(actor.party())>=100)throw new Problem.Failure(ProblemCode.BUSINESS_RULE_VIOLATION);
    }
    boolean changed=saved?repository.add(actor.party(),sku):repository.remove(actor.party(),sku);
    if(changed)audit.append(actor.party().toString(),AuditValues.ActorKind.USER,saved?AuditValues.Action.WISHLIST_SAVE:AuditValues.Action.WISHLIST_REMOVE,AuditValues.Resource.WISHLIST,sku,correlation,AuditValues.Reason.OPERATOR_REQUEST,Map.of("enabled",!saved),Map.of("enabled",saved),null);
    return new PlatformStore.Response(204,EventCodec.JSON.nullNode(),sku);
   });
  });
 }
}
