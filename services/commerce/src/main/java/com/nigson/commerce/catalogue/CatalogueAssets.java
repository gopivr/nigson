package com.nigson.commerce.catalogue;

import java.util.*;

/** Files-owned local port. Shipping labels never enter catalogue DTOs. */
public interface CatalogueAssets {
    List<CatalogueValues.Asset> published(UUID product);
    List<CatalogueValues.AdminAsset> reviewed(UUID product);
    Map<UUID,List<CatalogueValues.AdminAsset>> reviewed(List<UUID> products);
    Map<UUID,String> images(List<UUID> products);
    void publishClean(UUID product,String actor);
}
