package com.nigson.experience.lead;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.nigson.shared.problem.*;

/** Test-only stateful Commerce eligibility fixture; never registered as a runtime bean. */
final class StatefulQuoteSkuEligibilityMock implements QuoteLeadAssociation.SkuEligibilityPort {
 private final Map<UUID,Integer> eligible=new java.util.concurrent.ConcurrentHashMap<>();private final AtomicInteger calls=new AtomicInteger();private volatile boolean unavailable;
 void allow(UUID sku,int scale){eligible.put(sku,scale);}void unavailable(){unavailable=true;}int calls(){return calls.get();}
 public List<QuoteLeadValues.EligibleSku> validate(UUID party,UUID market,UUID organization,List<QuoteLeadValues.Product> products){calls.incrementAndGet();if(unavailable)throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);if(party==null||market==null)throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var out=new ArrayList<QuoteLeadValues.EligibleSku>();for(var item:products){Integer scale=eligible.get(item.skuId());if(scale==null||item.quantity().scale()>scale)throw new Problem.Failure(ProblemCode.QUOTE_RULE_VIOLATION);out.add(new QuoteLeadValues.EligibleSku(item.skuId(),scale));}return List.copyOf(out);}
}
