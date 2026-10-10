package com.nigson.commerce.catalogue;

import java.util.*;
import tools.jackson.databind.JsonNode;

public interface ProductRepository {
    record Snapshot(UUID id,CatalogueValues.ProductInput input,String status,long revision,long version){}
    void serializeMetadata();
    CatalogueRules rules();
    Snapshot find(UUID id,boolean lock);
    Snapshot save(UUID id,CatalogueValues.ProductInput value,String actor,boolean create);
    Snapshot publication(UUID id,String state,String actor);
    void touch(UUID id,String actor);
    CatalogueValues.ProductAdmin admin(UUID id);
    List<CatalogueValues.ProductAdmin> admins(List<UUID> ids);
    CatalogueValues.SkuAdmin createSku(UUID product,JsonNode input,String actor);
    UUID skuProduct(UUID sku);
    CatalogueValues.SkuAdmin patchSku(UUID sku,long expected,JsonNode input,String actor);
    List<String> eligibleMarkets(UUID id);
    boolean publicationReady(UUID id);
}
