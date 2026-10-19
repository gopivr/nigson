package com.nigson.commerce.quote;
import java.util.UUID;
public interface QuoteLeadPort {
 QuoteValues.LeadContext context(UUID leadId,UUID correlation);
 boolean associate(UUID leadId,UUID commandId,UUID quoteId,String state,UUID correlation);
}
