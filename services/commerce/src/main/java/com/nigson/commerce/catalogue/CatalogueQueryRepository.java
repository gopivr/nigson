package com.nigson.commerce.catalogue;

import java.util.*;

public interface CatalogueQueryRepository {
    record Search(String market,String q,UUID category,UUID brand,String productType,int page,int size,String sort){}
    CatalogueValues.Page<CatalogueValues.Summary> search(Search search);
    CatalogueValues.Product product(UUID id,String market);
    List<CatalogueValues.PublicCategory> categories(String market);
    List<CatalogueValues.PublicBrand> brands();
    CatalogueValues.Page<CatalogueValues.ProductAdmin> staff(String q,String status,int page,int size,String sort);
}
