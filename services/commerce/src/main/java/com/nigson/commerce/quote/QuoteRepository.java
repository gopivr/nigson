package com.nigson.commerce.quote;
import java.util.UUID;
import com.nigson.shared.security.ActorAssertion;
public interface QuoteRepository {
 QuoteValues.Quote create(ActorAssertion.Actor actor,QuoteValues.CreateInput input,String actorId);
 QuoteValues.Quote revise(ActorAssertion.Actor actor,UUID id,long version,QuoteValues.RevisionInput input,String actorId);
 QuoteValues.Quote approve(ActorAssertion.Actor actor,UUID id,UUID revision,long version,QuoteValues.Decision input,String actorId);
 QuoteValues.Quote offer(ActorAssertion.Actor actor,UUID id,long version,QuoteValues.Offer input,String actorId);
 QuoteValues.Quote get(ActorAssertion.Actor actor,UUID id);
 QuoteValues.Quote current(UUID id);
 void requireOwner(ActorAssertion.Actor actor,UUID id);
 QuoteValues.Quote owned(UUID id,UUID revision,ActorAssertion.Actor actor,boolean lock);
}
