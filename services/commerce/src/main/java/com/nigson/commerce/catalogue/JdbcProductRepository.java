package com.nigson.commerce.catalogue;

import java.util.*;
import java.time.Clock;
import java.math.BigDecimal;
import tools.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;

public final class JdbcProductRepository implements ProductRepository {
    private final JdbcTemplate jdbc;private final Clock clock;private final CatalogueAssets assets;
    public JdbcProductRepository(JdbcTemplate jdbc,Clock clock,CatalogueAssets assets){this.jdbc=jdbc;this.clock=clock;this.assets=assets;}
    private Problem.Failure rule(){return new Problem.Failure(ProblemCode.CATALOGUE_RULE_VIOLATION);}
    public void serializeMetadata(){
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");
        jdbc.execute("SET LOCAL lock_timeout='500ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");
        try{jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended('configuration:catalogue.metadata',0))",Object.class);}
        catch(org.springframework.dao.DataAccessException failure){if(failure.getMostSpecificCause() instanceof java.sql.SQLException sql&&"55P03".equals(sql.getSQLState()))throw new Problem.Failure(ProblemCode.CONFLICT);throw failure;}
    }
    public CatalogueRules rules(){
        var values=jdbc.query("SELECT value::text,active FROM commerce.feature_configuration WHERE key='catalogue.metadata' ORDER BY revision DESC LIMIT 1",(r,n)->Map.entry(r.getString(1),r.getBoolean(2)));
        if(values.isEmpty()||!values.getFirst().getValue())throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);
        return CatalogueMetadataPolicy.parse(EventCodec.JSON.readTree(values.getFirst().getKey()));
    }
    public Snapshot find(UUID id,boolean lock){
        var rows=jdbc.query("SELECT id,name,slug,description,brand_id,division_code,specifications::text,status,revision,version FROM commerce.product WHERE id=? AND deleted_at IS NULL"+(lock?" FOR UPDATE":""),(r,n)->new Snapshot(r.getObject(1,UUID.class),new CatalogueValues.ProductInput(r.getString(2),r.getString(3),r.getString(4),r.getObject(5,UUID.class),r.getString(6),EventCodec.JSON.readTree(r.getString(7)),categoryIds(id)),r.getString(8),r.getLong(9),r.getLong(10)),id);
        if(rows.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);return rows.getFirst();
    }
    private List<UUID> categoryIds(UUID id){return jdbc.query("SELECT category_id FROM commerce.product_category WHERE product_id=? ORDER BY category_id LIMIT 101",(r,n)->r.getObject(1,UUID.class),id);}
    private void validate(CatalogueValues.ProductInput v){
        if(v.divisionCode()!=null||!v.specifications().isEmpty())rules().specifications(v.divisionCode(),v.specifications());
        if(v.brandId()!=null){var found=jdbc.query("SELECT id FROM commerce.brand WHERE id=? AND active AND deleted_at IS NULL FOR SHARE",(r,n)->r.getObject(1,UUID.class),v.brandId());if(found.isEmpty())throw rule();}
        for(UUID category:v.categoryIds()){var found=jdbc.query("SELECT id FROM commerce.category WHERE id=? AND active AND deleted_at IS NULL FOR SHARE",(r,n)->r.getObject(1,UUID.class),category);if(found.isEmpty())throw rule();}
    }
    public Snapshot save(UUID id,CatalogueValues.ProductInput v,String actor,boolean create){
        validate(v);var now=java.sql.Timestamp.from(clock.instant());
        if(create)jdbc.update("INSERT INTO commerce.product(id,created_by,updated_by,name,slug,description,brand_id,division_code,specifications) VALUES (?,?,?,?,?,?,?,?,?::jsonb)",id,actor,actor,v.name(),v.slug(),v.description(),v.brandId(),v.divisionCode(),EventCodec.JSON.writeValueAsString(v.specifications()));
        else jdbc.update("UPDATE commerce.product SET name=?,slug=?,description=?,brand_id=?,division_code=?,specifications=?::jsonb,version=version+1,revision=revision+1,updated_at=?,updated_by=? WHERE id=?",v.name(),v.slug(),v.description(),v.brandId(),v.divisionCode(),EventCodec.JSON.writeValueAsString(v.specifications()),now,actor,id);
        jdbc.update("DELETE FROM commerce.product_category WHERE product_id=?",id);
        for(UUID category:v.categoryIds())jdbc.update("INSERT INTO commerce.product_category(id,created_by,updated_by,product_id,category_id) VALUES (?,?,?,?,?)",EventCodec.uuid7(clock.instant()),actor,actor,id,category);
        // Attributes mirror validated specification keys for the typed productType filter.
        jdbc.update("DELETE FROM commerce.product_attribute WHERE product_id=?",id);
        for(var entry:v.specifications().properties())jdbc.update("INSERT INTO commerce.product_attribute(id,created_by,updated_by,product_id,attribute_key,value) VALUES (?,?,?,?,?,?::jsonb)",EventCodec.uuid7(clock.instant()),actor,actor,id,entry.getKey(),EventCodec.JSON.writeValueAsString(entry.getValue()));
        return find(id,false);
    }
    public Snapshot publication(UUID id,String state,String actor){
        if(state.equals("ACTIVE"))assets.publishClean(id,actor);
        jdbc.update("UPDATE commerce.product SET status=?,published_at=CASE WHEN ?='ACTIVE' THEN ? ELSE published_at END,version=version+1,revision=revision+1,updated_at=?,updated_by=? WHERE id=?",state,state,java.sql.Timestamp.from(clock.instant()),java.sql.Timestamp.from(clock.instant()),actor,id);return find(id,false);
    }
    public void touch(UUID id,String actor){jdbc.update("UPDATE commerce.product SET version=version+1,revision=revision+1,updated_at=?,updated_by=? WHERE id=?",java.sql.Timestamp.from(clock.instant()),actor,id);}
    public List<String> eligibleMarkets(UUID id){var list=jdbc.query("SELECT m.code FROM commerce.product_market_eligibility e JOIN commerce.market m ON m.id=e.market_id WHERE e.product_id=? AND e.eligible AND m.enabled ORDER BY m.code LIMIT 101",(r,n)->r.getString(1),id);if(list.size()>100)throw rule();return list;}
    public boolean publicationReady(UUID id){return !eligibleMarkets(id).isEmpty()&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.sku WHERE product_id=? AND active AND deleted_at IS NULL) AND EXISTS(SELECT 1 FROM commerce.product_category pc JOIN commerce.category c ON c.id=pc.category_id WHERE pc.product_id=? AND c.active AND c.deleted_at IS NULL)",Boolean.class,id,id));}
    public CatalogueValues.ProductAdmin admin(UUID id){
        return admins(List.of(id)).getFirst();
    }
    public List<CatalogueValues.ProductAdmin> admins(List<UUID> ids){
        if(ids.size()>100||new HashSet<>(ids).size()!=ids.size())throw ApiValues.invalid();if(ids.isEmpty())return List.of();
        var categoryGroups=new HashMap<UUID,List<CatalogueValues.Reference>>();var skuGroups=new HashMap<UUID,List<CatalogueValues.Sku>>();
        jdbc.query("SELECT pc.product_id,c.id,c.name FROM commerce.category c JOIN commerce.product_category pc ON pc.category_id=c.id WHERE pc.product_id=ANY(?::uuid[]) AND c.deleted_at IS NULL ORDER BY pc.product_id,c.sort_order,c.id LIMIT 10001",ps->ps.setArray(1,ps.getConnection().createArrayOf("uuid",ids.toArray())),(org.springframework.jdbc.core.RowCallbackHandler)r->{var group=categoryGroups.computeIfAbsent(r.getObject(1,UUID.class),ignored->new ArrayList<>());group.add(new CatalogueValues.Reference(r.getObject(2,UUID.class),r.getString(3)));if(group.size()>100)throw rule();});
        jdbc.query("SELECT s.product_id,s.id,s.code,s.variant_name,s.unit,s.pack_size,s.active AND EXISTS(SELECT 1 FROM commerce.inventory_balance b JOIN commerce.stock_location l ON l.id=b.location_id WHERE b.sku_id=s.id AND l.active AND b.on_hand-b.reserved-b.allocated>0) FROM commerce.sku s WHERE s.product_id=ANY(?::uuid[]) AND s.deleted_at IS NULL ORDER BY s.product_id,s.id LIMIT 10001",ps->ps.setArray(1,ps.getConnection().createArrayOf("uuid",ids.toArray())),(org.springframework.jdbc.core.RowCallbackHandler)r->{var group=skuGroups.computeIfAbsent(r.getObject(1,UUID.class),ignored->new ArrayList<>());group.add(new CatalogueValues.Sku(r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getString(5),r.getBigDecimal(6).stripTrailingZeros().toPlainString(),r.getBoolean(7)));if(group.size()>100)throw rule();});
        var assetGroups=assets.reviewed(ids);
        var rows=jdbc.query("SELECT p.id,p.name,p.slug,p.description,p.specifications::text,b.id,b.name,p.revision,p.status,p.version FROM commerce.product p LEFT JOIN commerce.brand b ON b.id=p.brand_id AND b.deleted_at IS NULL WHERE p.id=ANY(?::uuid[]) AND p.deleted_at IS NULL",ps->ps.setArray(1,ps.getConnection().createArrayOf("uuid",ids.toArray())),(r,n)->{UUID id=r.getObject(1,UUID.class),brand=r.getObject(6,UUID.class);return new CatalogueValues.ProductAdmin(id,r.getString(2),r.getString(3),r.getString(4),EventCodec.JSON.readTree(r.getString(5)),brand==null?null:new CatalogueValues.Reference(brand,r.getString(7)),List.copyOf(categoryGroups.getOrDefault(id,List.of())),List.copyOf(skuGroups.getOrDefault(id,List.of())),assetGroups.getOrDefault(id,List.of()),r.getLong(8),r.getString(9),r.getLong(10));});
        var byId=new HashMap<UUID,CatalogueValues.ProductAdmin>();for(var row:rows)byId.put(row.id(),row);if(byId.size()!=ids.size())throw new Problem.Failure(ProblemCode.NOT_FOUND);return ids.stream().map(byId::get).toList();
    }
    public UUID skuProduct(UUID sku){var ids=jdbc.query("SELECT product_id FROM commerce.sku WHERE id=? AND deleted_at IS NULL",(r,n)->r.getObject(1,UUID.class),sku);if(ids.isEmpty())throw new Problem.Failure(ProblemCode.NOT_FOUND);return ids.getFirst();}
    private CatalogueValues.SkuAdmin sku(UUID id){return jdbc.queryForObject("SELECT id,code,variant_name,unit,quantity_scale,pack_size,version FROM commerce.sku WHERE id=?",(r,n)->new CatalogueValues.SkuAdmin(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getInt(5),r.getBigDecimal(6).stripTrailingZeros().toPlainString(),r.getLong(7)),id);}
    public CatalogueValues.SkuAdmin createSku(UUID product,JsonNode input,String actor){
        CatalogueValues.object(input,Set.of("code","variantName","unit","quantityScale","packSize","weightKg","carrierPackaging"),false);
        int scale=CatalogueValues.integer(input,"quantityScale",-1);String unit=CatalogueValues.string(input,"unit",20,false,false);rules().requireUnit(unit,scale);
        String code=CatalogueValues.string(input,"code",80,false,false),name=CatalogueValues.string(input,"variantName",160,false,false),pack=CatalogueValues.quantity(input,"packSize",scale);
        var packaging=CatalogueRules.packaging(input.get("carrierPackaging"),scale);var weight=weight(input,null,packaging);
        UUID id=EventCodec.uuid7(clock.instant());
        jdbc.update("INSERT INTO commerce.sku(id,created_by,updated_by,product_id,code,variant_name,unit,quantity_scale,pack_size,weight_kg,carrier_packaging,dimensions) VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb)",id,actor,actor,product,code,name,unit,scale,new BigDecimal(pack),weight,EventCodec.JSON.writeValueAsString(packaging),EventCodec.JSON.writeValueAsString(Map.of("lengthCm",packaging.lengthCm(),"widthCm",packaging.widthCm(),"heightCm",packaging.heightCm())));
        touch(product,actor);return sku(id);
    }
    private BigDecimal weight(JsonNode input,BigDecimal fallback,CatalogueRules.Packaging packaging){
        BigDecimal minimum=new BigDecimal(packaging.unitWeightKg());BigDecimal requested=input.has("weightKg")?new BigDecimal(CatalogueRules.decimal(input,"weightKg",true,9)):fallback;
        if(requested==null)requested=minimum;if(requested.compareTo(minimum)<0)throw rule();return requested;
    }
    public CatalogueValues.SkuAdmin patchSku(UUID id,long expected,JsonNode input,String actor){
        CatalogueValues.object(input,Set.of("variantName","active","weightKg","carrierPackaging"),false);
        var before=sku(id);if(before.version()!=expected)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
        var packaging=CatalogueRules.packaging(input.get("carrierPackaging"),before.quantityScale());rules().requireUnit(before.unit(),before.quantityScale());
        String name=input.has("variantName")?CatalogueValues.string(input,"variantName",160,false,false):before.variantName();
        var oldWeight=jdbc.queryForObject("SELECT weight_kg FROM commerce.sku WHERE id=? FOR UPDATE",BigDecimal.class,id);var weight=weight(input,oldWeight,packaging);
        jdbc.update("UPDATE commerce.sku SET variant_name=?,active=coalesce(?,active),weight_kg=?,carrier_packaging=?::jsonb,dimensions=?::jsonb,version=version+1,updated_by=?,updated_at=? WHERE id=?",name,input.has("active")?CatalogueValues.bool(input,"active",false):null,weight,EventCodec.JSON.writeValueAsString(packaging),EventCodec.JSON.writeValueAsString(Map.of("lengthCm",packaging.lengthCm(),"widthCm",packaging.widthCm(),"heightCm",packaging.heightCm())),actor,java.sql.Timestamp.from(clock.instant()),id);
        touch(skuProduct(id),actor);return sku(id);
    }
}
