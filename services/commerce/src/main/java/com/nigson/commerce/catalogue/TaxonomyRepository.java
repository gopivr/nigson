package com.nigson.commerce.catalogue;

import java.util.UUID;

public interface TaxonomyRepository {
    enum Kind { BRAND,CATEGORY }
    record Record(UUID id,String name,String slug,UUID parentId,int sortOrder,boolean active,long version){}
    void serialize();
    Record find(Kind kind,UUID id);
    Record save(Kind kind,Record value,String actor,boolean create);
    void validateTree(UUID id,UUID parent);
}
