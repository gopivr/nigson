package com.nigson.commerce.catalogue;
import java.util.*;
/** Market changes share the commerce transaction and product optimistic version. */
public interface CatalogueMarketChanges {
 void lock(UUID product,long expected);
 long changed(UUID product,String actor,UUID correlation);
}
