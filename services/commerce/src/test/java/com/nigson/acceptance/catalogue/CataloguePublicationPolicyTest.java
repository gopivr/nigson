package com.nigson.acceptance.catalogue;
import com.nigson.commerce.catalogue.CataloguePublicationPolicy;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CataloguePublicationPolicyTest {
 @Test void requiresImagesUnlessExplicitlyEnabledLocally(){for(String env:new String[]{"local","test","staging","production","prod",""})assertThat(new CataloguePublicationPolicy(env,false).imageRequired()).isTrue();assertThat(new CataloguePublicationPolicy("local",true).imageRequired()).isFalse();}
 @Test void rejectsEveryNonlocalEnablement(){for(String env:new String[]{"test","staging","production","prod","LOCAL",""})assertThatThrownBy(()->new CataloguePublicationPolicy(env,true)).hasMessage("IMAGELESS_PUBLICATION_LOCAL_ONLY");}
}
