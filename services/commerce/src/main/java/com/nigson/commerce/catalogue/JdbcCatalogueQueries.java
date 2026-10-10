package com.nigson.commerce.catalogue;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.shared.problem.*;

public final class JdbcCatalogueQueries implements CatalogueQueryRepository {
    private final JdbcTemplate jdbc;private final ProductRepository products;private final CatalogueAssets assets;
    private static final String ELIGIBLE="p.status='ACTIVE' AND p.deleted_at IS NULL AND EXISTS(SELECT 1 FROM commerce.product_market_eligibility e JOIN commerce.market m ON m.id=e.market_id WHERE e.product_id=p.id AND e.eligible AND m.enabled AND m.code=?)";
    private static final String AVAILABLE="EXISTS(SELECT 1 FROM commerce.sku s JOIN commerce.inventory_balance b ON b.sku_id=s.id JOIN commerce.stock_location l ON l.id=b.location_id WHERE s.product_id=p.id AND s.active AND s.deleted_at IS NULL AND l.active AND b.on_hand-b.reserved-b.allocated>0)";
    public JdbcCatalogueQueries(JdbcTemplate jdbc,ProductRepository products,CatalogueAssets assets){this.jdbc=jdbc;this.products=products;this.assets=assets;}
    private void market(String code){if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.market WHERE code=? AND enabled)",Boolean.class,code)))throw new Problem.Failure(ProblemCode.NOT_FOUND);}
    private String order(String sort){return switch(sort){case "name,asc"->"p.name ASC,p.id";case "name,desc"->"p.name DESC,p.id";case "createdAt,asc"->"p.created_at ASC,p.id";case "createdAt,desc"->"p.created_at DESC,p.id";case "relevance,desc"->"EXISTS(SELECT 1 FROM commerce.sku exact_sku WHERE exact_sku.product_id=p.id AND exact_sku.active AND exact_sku.deleted_at IS NULL AND lower(btrim(exact_sku.code))=lower(btrim(?))) DESC,(to_tsvector('simple',p.name) @@ term.query) DESC,ts_rank_cd(p.search_vector,term.query) DESC,p.id";default->throw ApiValues.invalid();};}
    public CatalogueValues.Page<CatalogueValues.Summary> search(Search s){
        market(s.market());String sorting=order(s.sort());var args=new ArrayList<Object>();args.add(s.q());args.add(s.market());args.add(s.q());args.add(s.q());
        String where=" FROM commerce.product p LEFT JOIN commerce.brand b ON b.id=p.brand_id AND b.active AND b.deleted_at IS NULL CROSS JOIN (SELECT plainto_tsquery('simple',regexp_replace(?,'[-/]+',' ','g')) AS query) term WHERE "+ELIGIBLE+" AND (?='' OR p.search_vector @@ term.query OR EXISTS(SELECT 1 FROM commerce.sku exact_sku WHERE exact_sku.product_id=p.id AND exact_sku.active AND exact_sku.deleted_at IS NULL AND lower(btrim(exact_sku.code))=lower(btrim(?))))";
        if(s.category()!=null){where+=" AND EXISTS(SELECT 1 FROM commerce.product_category pc JOIN commerce.category c ON c.id=pc.category_id WHERE pc.product_id=p.id AND pc.category_id=? AND c.active AND c.deleted_at IS NULL)";args.add(s.category());}
        if(s.brand()!=null){where+=" AND b.id=?";args.add(s.brand());}
        if(s.productType()!=null){where+=" AND EXISTS(SELECT 1 FROM commerce.product_attribute a WHERE a.product_id=p.id AND a.attribute_key='product_type' AND a.value=to_jsonb(?::text))";args.add(s.productType());}
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT count(*)"+where,Long.class,args.toArray()));
        if(s.sort().equals("relevance,desc"))args.add(s.q());args.add(s.size());args.add((long)s.page()*s.size());
        var rows=jdbc.query("SELECT p.id,p.name,p.slug,b.name,"+AVAILABLE+",p.revision"+where+" ORDER BY "+sorting+" LIMIT ? OFFSET ?",(r,n)->new CatalogueValues.Summary(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),null,r.getBoolean(5),r.getLong(6)),args.toArray());
        var images=assets.images(rows.stream().map(CatalogueValues.Summary::id).toList());
        var result=rows.stream().map(v->new CatalogueValues.Summary(v.id(),v.name(),v.slug(),v.brandName(),images.get(v.id()),v.available(),v.revision())).toList();
        return new CatalogueValues.Page<>(result,s.page(),s.size(),total,(total+s.size()-1)/s.size());
    }
    public CatalogueValues.Product product(UUID id,String market){
        market(market);if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.product p WHERE p.id=? AND "+ELIGIBLE+")",Boolean.class,id,market)))throw new Problem.Failure(ProblemCode.NOT_FOUND);
        var v=products.find(id,false);var input=v.input();
        var brands=input.brandId()==null?List.<CatalogueValues.Reference>of():jdbc.query("SELECT id,name FROM commerce.brand WHERE id=? AND active AND deleted_at IS NULL",(r,n)->new CatalogueValues.Reference(r.getObject(1,UUID.class),r.getString(2)),input.brandId());
        var categories=jdbc.query("SELECT c.id,c.name FROM commerce.product_category pc JOIN commerce.category c ON c.id=pc.category_id WHERE pc.product_id=? AND c.active AND c.deleted_at IS NULL ORDER BY c.sort_order,c.id LIMIT 101",(r,n)->new CatalogueValues.Reference(r.getObject(1,UUID.class),r.getString(2)),id);
        var skus=jdbc.query("SELECT s.id,s.code,s.variant_name,s.unit,s.pack_size,EXISTS(SELECT 1 FROM commerce.inventory_balance b JOIN commerce.stock_location l ON l.id=b.location_id WHERE b.sku_id=s.id AND l.active AND b.on_hand-b.reserved-b.allocated>0) FROM commerce.sku s WHERE s.product_id=? AND s.active AND s.deleted_at IS NULL ORDER BY s.id LIMIT 101",(r,n)->new CatalogueValues.Sku(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getBigDecimal(5).stripTrailingZeros().toPlainString(),r.getBoolean(6)),id);
        if(categories.size()>100||skus.size()>100)throw new Problem.Failure(ProblemCode.CATALOGUE_RULE_VIOLATION);
        return new CatalogueValues.Product(id,input.name(),input.slug(),input.description(),input.specifications(),brands.isEmpty()?null:brands.getFirst(),categories,skus,assets.published(id),v.revision());
    }
    public List<CatalogueValues.PublicCategory> categories(String market){
        market(market);var rows=jdbc.query("SELECT c.id,c.parent_id,c.name,c.slug,c.sort_order FROM commerce.category c WHERE c.active AND c.deleted_at IS NULL AND EXISTS(SELECT 1 FROM commerce.product_category pc JOIN commerce.product p ON p.id=pc.product_id WHERE pc.category_id=c.id AND "+ELIGIBLE+") ORDER BY c.sort_order,c.id LIMIT 1001",(r,n)->new CatalogueValues.PublicCategory(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getInt(5)),market);
        if(rows.size()>1000)throw new Problem.Failure(ProblemCode.CATALOGUE_RULE_VIOLATION);return rows;
    }
    public List<CatalogueValues.PublicBrand> brands(){var rows=jdbc.query("SELECT id,name,slug FROM commerce.brand WHERE active AND deleted_at IS NULL ORDER BY name,id LIMIT 1001",(r,n)->new CatalogueValues.PublicBrand(r.getObject(1,UUID.class),r.getString(2),r.getString(3)));if(rows.size()>1000)throw new Problem.Failure(ProblemCode.CATALOGUE_RULE_VIOLATION);return rows;}
    public CatalogueValues.Page<CatalogueValues.ProductAdmin> staff(String q,String status,int page,int size,String sort){
        String sorting=order(sort);if(sort.equals("relevance,desc"))sorting="p.created_at DESC,p.id";
        String where=" FROM commerce.product p WHERE p.deleted_at IS NULL AND (?='' OR strpos(lower(p.name),lower(?))>0 OR EXISTS(SELECT 1 FROM commerce.sku s WHERE s.product_id=p.id AND strpos(lower(s.code),lower(?))>0))";
        var args=new ArrayList<Object>(List.of(q,q,q));if(status!=null){where+=" AND p.status=?";args.add(status);}
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT count(*)"+where,Long.class,args.toArray()));args.add(size);args.add((long)page*size);
        var ids=jdbc.query("SELECT p.id"+where+" ORDER BY "+sorting+" LIMIT ? OFFSET ?",(r,n)->r.getObject(1,UUID.class),args.toArray());
        return new CatalogueValues.Page<>(products.admins(ids),page,size,total,(total+size-1)/size);
    }
}
