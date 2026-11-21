package com.nigson.commerce.catalogue;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/** Explicit development exception; never permits nonlocal image-less publication. */
@Component
public final class CataloguePublicationPolicy {
    private final boolean allowImageless;
    public CataloguePublicationPolicy(@Value("${APP_ENV:}") String environment,@Value("${NIGSON_ALLOW_IMAGELESS_PUBLISH:false}") boolean enabled){
        if(enabled&&!"local".equals(environment))throw new IllegalArgumentException("IMAGELESS_PUBLICATION_LOCAL_ONLY");
        allowImageless=enabled;
    }
    public boolean imageRequired(){return !allowImageless;}
}
