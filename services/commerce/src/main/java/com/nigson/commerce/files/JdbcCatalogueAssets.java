package com.nigson.commerce.files;

import com.nigson.commerce.catalogue.*;
import com.nigson.shared.problem.*;
import java.util.*;
import java.net.URI;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/** Owns the asset publication transition inside the caller's commerce transaction. */
@Component
public final class JdbcCatalogueAssets implements CatalogueAssets {
    private final JdbcTemplate jdbc;private final String base;
    public JdbcCatalogueAssets(JdbcTemplate jdbc,@Value("${APP_ENV}")String env,@Value("${NIGSON_PUBLIC_ASSET_BASE_URL:}")String configured){
        this.jdbc=jdbc;
        if(configured.isBlank()){base=null;return;}
        URI uri;try{uri=URI.create(configured);}catch(IllegalArgumentException bad){throw new IllegalArgumentException("PUBLIC_ASSET_TARGET_INVALID");}
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!uri.normalize().equals(uri)||!Set.of("local","test").contains(env)&&(uri.getHost().endsWith(".test")||uri.getHost().endsWith(".invalid")||uri.getHost().endsWith(".example")||uri.getHost().endsWith(".localhost")||uri.getHost().matches("[0-9.]+")||uri.getHost().contains(":")||Set.of("localhost","example.com","example.net","example.org").contains(uri.getHost())))throw new IllegalArgumentException("PUBLIC_ASSET_TARGET_INVALID");
        base=configured.endsWith("/")?configured:configured+"/";
    }
    private String url(String key){
        if(base==null)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);
        if(key==null||key.length()>500||!key.matches("[A-Za-z0-9_./-]+")||key.startsWith("/")||Arrays.asList(key.split("/",-1)).stream().anyMatch(p->p.isEmpty()||p.equals(".")||p.equals("..")))throw new Problem.Failure(ProblemCode.CATALOGUE_RULE_VIOLATION);
        return base+key;
    }
    public List<CatalogueValues.Asset> published(UUID product){
        var result=jdbc.query("SELECT id,purpose,object_key FROM commerce.asset_reference WHERE product_id=? AND classification='PUBLIC' AND purpose IN ('IMAGE','BROCHURE') AND status='PUBLISHED' ORDER BY sort_order,id LIMIT 101",(r,n)->new CatalogueValues.Asset(r.getObject(1,UUID.class),r.getString(2),url(r.getString(3))),product);
        if(result.size()>100)throw new Problem.Failure(ProblemCode.CATALOGUE_RULE_VIOLATION);return result;
    }
    public List<CatalogueValues.AdminAsset> reviewed(UUID product){
        return reviewed(List.of(product)).getOrDefault(product,List.of());
    }
    public Map<UUID,List<CatalogueValues.AdminAsset>> reviewed(List<UUID> products){
        if(products.size()>100)throw ApiValues.invalid();if(products.isEmpty())return Map.of();
        var result=new HashMap<UUID,List<CatalogueValues.AdminAsset>>();
        jdbc.query("SELECT product_id,id,status,purpose,mime_type,byte_size,checksum_sha256,object_key,classification,version FROM commerce.asset_reference WHERE product_id=ANY(?::uuid[]) AND purpose IN ('IMAGE','BROCHURE') ORDER BY product_id,sort_order,id LIMIT 10001",ps->ps.setArray(1,ps.getConnection().createArrayOf("uuid",products.toArray())),(org.springframework.jdbc.core.RowCallbackHandler)r->{
            var values=result.computeIfAbsent(r.getObject(1,UUID.class),ignored->new ArrayList<>());
            values.add(new CatalogueValues.AdminAsset(r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getString(5),r.getLong(6),r.getString(7),r.getString(3).equals("PUBLISHED")&&r.getString(9).equals("PUBLIC")?url(r.getString(8)):null,r.getLong(10)));
            if(values.size()>100)throw new Problem.Failure(ProblemCode.CATALOGUE_RULE_VIOLATION);
        });result.replaceAll((id,values)->List.copyOf(values));return Map.copyOf(result);
    }
    public void publishClean(UUID product,String actor){
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");
        var keys=jdbc.query("SELECT object_key FROM commerce.asset_reference WHERE product_id=? AND purpose='IMAGE' AND classification='PUBLIC' AND status IN ('CLEAN','PUBLISHED') ORDER BY id LIMIT 101 FOR UPDATE",(r,n)->r.getString(1),product);
        if(keys.isEmpty()||keys.size()>100)throw new Problem.Failure(ProblemCode.CATALOGUE_RULE_VIOLATION);
        keys.forEach(this::url);
        jdbc.update("UPDATE commerce.asset_reference SET status='PUBLISHED',version=version+1,updated_by=?,updated_at=now() WHERE product_id=? AND classification='PUBLIC' AND purpose IN ('IMAGE','BROCHURE') AND status='CLEAN'",actor,product);
    }
    public Map<UUID,String> images(List<UUID> products){
        if(products.size()>100)throw ApiValues.invalid();if(products.isEmpty())return Map.of();
        var rows=jdbc.query("SELECT DISTINCT ON(product_id) product_id,object_key FROM commerce.asset_reference WHERE product_id=ANY(?::uuid[]) AND purpose='IMAGE' AND classification='PUBLIC' AND status='PUBLISHED' ORDER BY product_id,sort_order,id",ps->ps.setArray(1,ps.getConnection().createArrayOf("uuid",products.toArray())),(r,n)->Map.entry(r.getObject(1,UUID.class),url(r.getString(2))));
        var urls=new HashMap<UUID,String>();for(var row:rows)urls.put(row.getKey(),row.getValue());return Map.copyOf(urls);
    }
}
