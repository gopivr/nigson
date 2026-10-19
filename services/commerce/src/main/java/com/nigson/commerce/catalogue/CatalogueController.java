package com.nigson.commerce.catalogue;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.observability.WireContext;

@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public class CatalogueController {
    private final CatalogueReads reads;private final ProductService products;private final TaxonomyService taxonomy;private final RecommendationService recommendations;
    public CatalogueController(CatalogueReads reads,ProductService products,TaxonomyService taxonomy,RecommendationService recommendations){this.reads=reads;this.products=products;this.taxonomy=taxonomy;this.recommendations=recommendations;}
    private ActorAssertion.Actor actor(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated())throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!(auth.getPrincipal() instanceof ActorAssertion.Actor a))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return a;}
    private Map<String,String> query(HttpServletRequest request,Set<String> allowed,boolean noBody)throws java.io.IOException{
        if(!allowed.containsAll(request.getParameterMap().keySet()))throw ApiValues.invalid();var result=new HashMap<String,String>();
        for(var entry:request.getParameterMap().entrySet()){if(entry.getValue().length!=1||entry.getValue()[0].length()>200)throw ApiValues.invalid();result.put(entry.getKey(),entry.getValue()[0]);}
        if(noBody&&request.getInputStream().read()!=-1)throw ApiValues.invalid();return result;
    }
    private String header(HttpServletRequest request,String name){var values=Collections.list(request.getHeaders(name));if(values.size()!=1)throw ApiValues.invalid();return values.getFirst();}
    private int number(String value){try{if(!value.matches("0|[1-9][0-9]{0,9}"))throw ApiValues.invalid();return Integer.parseInt(value);}catch(NumberFormatException bad){throw ApiValues.invalid();}}
    private UUID id(String value){return value==null?null:ApiValues.uuid(value);}
    private UUID correlation(){return WireContext.current()==null?WireContext.correlation(null):WireContext.current().correlationId;}
    private <T>ResponseEntity<T> cached(HttpServletRequest request,T value,long revision){
        String etag="\""+revision+"-"+EventCodec.hash(EventCodec.JSON.valueToTree(value))+"\"";var tags=Collections.list(request.getHeaders("If-None-Match"));if(tags.size()>1)throw ApiValues.invalid();
        boolean match=!tags.isEmpty()&&Arrays.stream(tags.getFirst().split(",")).map(String::strip).anyMatch(t->t.equals("*")||t.equals(etag)||t.equals("W/"+etag));
        return ResponseEntity.status(match?304:200).eTag(etag).header("Cache-Control","public, max-age=0, must-revalidate").header("Vary","Accept-Language").body(match?null:value);
    }
    @GetMapping(value="/api/v1/catalogue/products",produces="application/json")
    public ResponseEntity<CatalogueValues.Page<CatalogueValues.Summary>> search(HttpServletRequest r)throws java.io.IOException{
        var q=query(r,Set.of("market","q","categoryId","brandId","productType","page","size","sort"),true);var result=reads.search(new CatalogueQueryRepository.Search(CatalogueReads.market(q.get("market")),CatalogueReads.q(q.getOrDefault("q","")),id(q.get("categoryId")),id(q.get("brandId")),q.get("productType"),number(q.getOrDefault("page","0")),number(q.getOrDefault("size","20")),q.getOrDefault("sort","relevance,desc")));
        return cached(r,result,result.items().stream().mapToLong(CatalogueValues.Summary::revision).max().orElse(0));
    }
    @GetMapping(value="/api/v1/catalogue/products/{id}",produces="application/json")
    public ResponseEntity<CatalogueValues.Product> product(HttpServletRequest r,@PathVariable String id)throws java.io.IOException{var q=query(r,Set.of("market"),true);var value=reads.product(ApiValues.uuid(id),CatalogueReads.market(q.get("market")));return cached(r,value,value.revision());}
    @GetMapping(value="/api/v1/catalogue/products/{id}/recommendations",produces="application/json")
    public List<RecommendationValues.PublicItem> recommendations(HttpServletRequest r,@PathVariable String id)throws java.io.IOException{var q=query(r,Set.of("market","limit"),true);return reads.boundedQuery(()->recommendations.publicList(ApiValues.uuid(id),CatalogueReads.market(q.get("market")),number(q.getOrDefault("limit","6"))));}
    @PutMapping(value="/api/v1/staff/products/{id}/recommendations",consumes="application/json",produces="application/json")
    public ResponseEntity<RecommendationValues.Result> replaceRecommendations(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input)throws java.io.IOException{query(r,Set.of(),false);var result=recommendations.replace(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),input,header(r,"Idempotency-Key"),correlation());return ResponseEntity.ok().eTag(Long.toString(result.version())).body(result);}
    @GetMapping(value="/internal/v1/catalogue/knowledge/{id}",produces="application/json")
    public RecommendationValues.Knowledge knowledge(HttpServletRequest r,@PathVariable String id)throws java.io.IOException{query(r,Set.of(),true);var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!(auth.getPrincipal() instanceof com.nigson.shared.security.PrivateWorkload.Proof proof))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return reads.boundedQuery(()->recommendations.knowledge(proof,ApiValues.uuid(id)));}
    @GetMapping(value="/api/v1/catalogue/categories",produces="application/json")
    public ResponseEntity<List<CatalogueValues.PublicCategory>> categories(HttpServletRequest r)throws java.io.IOException{var q=query(r,Set.of("market"),true);return cached(r,reads.categories(CatalogueReads.market(q.get("market"))),0);}
    @GetMapping(value="/api/v1/catalogue/brands",produces="application/json")
    public ResponseEntity<List<CatalogueValues.PublicBrand>> brands(HttpServletRequest r)throws java.io.IOException{query(r,Set.of(),true);return cached(r,reads.brands(),0);}
    @GetMapping(value="/api/v1/staff/products",produces="application/json")
    public CatalogueValues.Page<CatalogueValues.ProductAdmin> staff(HttpServletRequest r)throws java.io.IOException{var q=query(r,Set.of("q","status","page","size","sort"),true);return reads.staff(actor(),q.getOrDefault("q",""),q.get("status"),number(q.getOrDefault("page","0")),number(q.getOrDefault("size","20")),q.getOrDefault("sort","createdAt,desc"));}
    @PostMapping(value="/api/v1/staff/products",consumes="application/json",produces="application/json")
    public ResponseEntity<CatalogueValues.ProductAdmin> create(HttpServletRequest r,@RequestBody JsonNode input)throws java.io.IOException{query(r,Set.of(),false);var result=products.write(actor(),null,0,input,header(r,"Idempotency-Key"),correlation());return ResponseEntity.created(java.net.URI.create("/api/v1/staff/products/"+result.id())).eTag(Long.toString(result.version())).body(result);}
    @PatchMapping(value="/api/v1/staff/products/{id}",consumes="application/json",produces="application/json")
    public ResponseEntity<CatalogueValues.ProductAdmin> patch(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input)throws java.io.IOException{query(r,Set.of(),false);var result=products.write(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),input,header(r,"Idempotency-Key"),correlation());return ResponseEntity.ok().eTag(Long.toString(result.version())).body(result);}
    @PostMapping(value="/api/v1/staff/products/{id}/publication",consumes="application/json",produces="application/json")
    public ResponseEntity<CatalogueValues.ProductAdmin> publish(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input)throws java.io.IOException{query(r,Set.of(),false);var result=products.publish(actor(),ApiValues.uuid(id),ApiValues.version(header(r,"If-Match")),input,header(r,"Idempotency-Key"),correlation());return ResponseEntity.status(201).eTag(Long.toString(result.version())).body(result);}
    @PostMapping(value="/api/v1/staff/products/{id}/skus",consumes="application/json",produces="application/json")
    public ResponseEntity<CatalogueValues.SkuAdmin> createSku(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input)throws java.io.IOException{query(r,Set.of(),false);var result=products.sku(actor(),ApiValues.uuid(id),true,ApiValues.version(header(r,"If-Match")),input,header(r,"Idempotency-Key"),correlation());return ResponseEntity.status(201).eTag(Long.toString(result.version())).body(result);}
    @PatchMapping(value="/api/v1/staff/skus/{id}",consumes="application/json",produces="application/json")
    public ResponseEntity<CatalogueValues.SkuAdmin> patchSku(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input)throws java.io.IOException{query(r,Set.of(),false);var result=products.sku(actor(),ApiValues.uuid(id),false,ApiValues.version(header(r,"If-Match")),input,header(r,"Idempotency-Key"),correlation());return ResponseEntity.ok().eTag(Long.toString(result.version())).body(result);}
    private ResponseEntity<CatalogueValues.Taxonomy> taxonomy(HttpServletRequest r,TaxonomyRepository.Kind kind,UUID id,JsonNode input)throws java.io.IOException{query(r,Set.of(),false);var value=taxonomy.write(actor(),kind,id,id==null?0:ApiValues.version(header(r,"If-Match")),input,header(r,"Idempotency-Key"),correlation());return ResponseEntity.status(id==null?201:200).eTag(Long.toString(value.version())).body(value);}
    @PostMapping(value="/api/v1/staff/brands",consumes="application/json",produces="application/json")public ResponseEntity<CatalogueValues.Taxonomy> createBrand(HttpServletRequest r,@RequestBody JsonNode input)throws java.io.IOException{return taxonomy(r,TaxonomyRepository.Kind.BRAND,null,input);}
    @PatchMapping(value="/api/v1/staff/brands/{id}",consumes="application/json",produces="application/json")public ResponseEntity<CatalogueValues.Taxonomy> patchBrand(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input)throws java.io.IOException{return taxonomy(r,TaxonomyRepository.Kind.BRAND,ApiValues.uuid(id),input);}
    @PostMapping(value="/api/v1/staff/categories",consumes="application/json",produces="application/json")public ResponseEntity<CatalogueValues.Taxonomy> createCategory(HttpServletRequest r,@RequestBody JsonNode input)throws java.io.IOException{return taxonomy(r,TaxonomyRepository.Kind.CATEGORY,null,input);}
    @PatchMapping(value="/api/v1/staff/categories/{id}",consumes="application/json",produces="application/json")public ResponseEntity<CatalogueValues.Taxonomy> patchCategory(HttpServletRequest r,@PathVariable String id,@RequestBody JsonNode input)throws java.io.IOException{return taxonomy(r,TaxonomyRepository.Kind.CATEGORY,ApiValues.uuid(id),input);}
}
