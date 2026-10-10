package com.nigson.commerce.market;
import java.util.*;
public interface MarketRepository {
 MarketValues.Market create(MarketValues.Create input,String actor);
 MarketValues.Market find(UUID id,boolean lock);
 MarketValues.Market book(UUID id,long expected,MarketValues.Book input,String actor);
 void restriction(UUID product,UUID market,MarketValues.Eligibility input,String actor);
 List<MarketValues.PublicMarket> publicMarkets();
}
