package com.nigson.commerce.party;

import java.util.*;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;

/** Party locks serialize owner commands; all joins stay in commerce. */
public final class JdbcPartyRepository implements PartyRepository {
    private final JdbcTemplate jdbc;private final Clock clock;private final int phase;private final com.nigson.commerce.platform.AuditWriter audit;
    public JdbcPartyRepository(JdbcTemplate jdbc,Clock clock,int phase,com.nigson.commerce.platform.AuditWriter audit){if(phase<1||phase>5)throw new IllegalArgumentException("PHASE_REQUIRED");this.jdbc=jdbc;this.clock=clock;this.phase=phase;this.audit=audit;}
    private java.sql.Timestamp now(){return java.sql.Timestamp.from(clock.instant());}
    public void requireCurrent(ActorAssertion.Actor actor,boolean lock){
        if(actor==null)throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        if(!actor.roles().contains("CUSTOMER")||actor.organization()!=null)throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        var rows=jdbc.query("SELECT id FROM commerce.party WHERE id=? AND identity_issuer=? AND identity_subject=? AND status='ACTIVE'"+(lock?" FOR UPDATE":""),(r,n)->r.getObject(1,UUID.class),actor.party(),actor.identityIssuer(),actor.identitySubject());
        if(rows.size()!=1)throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
    }
    public PartyValues.Profile profile(UUID party){
        var rows=jdbc.query("SELECT p.id,p.display_name,p.email,p.email_verified,c.phone,coalesce(c.locale,'en'),p.version FROM commerce.party p LEFT JOIN commerce.customer_profile c ON c.party_id=p.id WHERE p.id=? AND p.status='ACTIVE'",(r,n)->new PartyValues.Profile(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getBoolean(4),r.getString(5),r.getString(6),r.getLong(7)),party);
        if(rows.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);return rows.getFirst();
    }
    public void patchProfile(UUID party,long version,JsonNode patch){
        var before=profile(party);if(before.version()!=version)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
        String name=patch.has("displayName")?patch.get("displayName").asString():before.displayName();
        String phone=patch.has("phone")?(patch.get("phone").isNull()?null:patch.get("phone").asString()):before.phone();
        String locale=patch.has("locale")?patch.get("locale").asString():before.locale();
        if(Objects.equals(name,before.displayName())&&Objects.equals(phone,before.phone())&&Objects.equals(locale,before.locale()))return;
        jdbc.update("INSERT INTO commerce.customer_profile(id,created_by,updated_by,party_id,phone,locale) VALUES (?,?,?,?,?,?) ON CONFLICT(party_id) DO UPDATE SET phone=EXCLUDED.phone,locale=EXCLUDED.locale,updated_at=?,updated_by=?,version=commerce.customer_profile.version+1",EventCodec.uuid7(clock.instant()),party.toString(),party.toString(),party,phone,locale,now(),party.toString());
        if(jdbc.update("UPDATE commerce.party SET display_name=?,version=version+1,updated_by=?,updated_at=? WHERE id=? AND version=?",name,party.toString(),now(),party,version)!=1)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
    }
    private PartyValues.Address row(java.sql.ResultSet r)throws java.sql.SQLException{return new PartyValues.Address(r.getObject("id",UUID.class),r.getString("label"),r.getString("recipient"),r.getString("phone"),r.getString("line1"),r.getString("line2"),r.getString("city"),r.getString("region"),r.getString("postal_code"),r.getString("country_code"),r.getObject("organization_id",UUID.class),r.getBoolean("is_default"),r.getLong("version"));}
    public List<PartyValues.Address> addresses(UUID party){
        var result=jdbc.query("SELECT a.* FROM commerce.address a WHERE a.party_id=? AND a.deleted_at IS NULL AND (a.organization_id IS NULL OR (? >= 3 AND EXISTS(SELECT 1 FROM commerce.organization_membership m JOIN commerce.organization o ON o.id=m.organization_id WHERE m.party_id=? AND m.organization_id=a.organization_id AND m.status='ACTIVE' AND o.status='ACTIVE'))) ORDER BY a.created_at,a.id LIMIT 101",(r,n)->row(r),party,phase,party);
        if(result.size()>100)throw new Problem.Failure(ProblemCode.PARTY_RULE_VIOLATION);return List.copyOf(result);
    }
    public PartyValues.Address address(UUID party,UUID id){
        var rows=jdbc.query("SELECT * FROM commerce.address WHERE party_id=? AND id=? AND deleted_at IS NULL",(r,n)->row(r),party,id);
        if(rows.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);var address=rows.getFirst();requireOrganization(party,address.organizationId(),true);return address;
    }
    public void requireOrganization(UUID party,UUID organization,boolean lock){
        if(organization==null)return;if(phase<3)throw new Problem.Failure(ProblemCode.NOT_FOUND);
        var rows=jdbc.query("SELECT m.id FROM commerce.organization_membership m JOIN commerce.organization o ON o.id=m.organization_id WHERE m.party_id=? AND m.organization_id=? AND m.status='ACTIVE' AND o.status='ACTIVE'"+(lock?" FOR SHARE OF m,o":""),(r,n)->r.getObject(1,UUID.class),party,organization);
        if(rows.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);
    }
    public void validateCountry(PartyValues.Address address){
        var policies=jdbc.query("SELECT d.address_rules::text FROM commerce.market_destination d JOIN commerce.market m ON m.id=d.market_id WHERE m.enabled AND d.country_code=? ORDER BY m.id,d.id LIMIT 101 FOR SHARE OF m,d",(r,n)->EventCodec.JSON.readTree(r.getString(1)),address.countryCode());
        if(policies.isEmpty())throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);
        if(policies.size()>100||policies.stream().map(EventCodec::hash).distinct().count()!=1)throw new Problem.Failure(ProblemCode.PARTY_RULE_VIOLATION);
        AddressRules.parse(policies.getFirst()).validate(address);
    }
    private void defaultAddress(UUID party,UUID id,UUID correlation){
        var changed=jdbc.query("UPDATE commerce.address SET is_default=false,version=version+1,updated_at=?,updated_by=? WHERE party_id=? AND id<>? AND is_default AND deleted_at IS NULL RETURNING id,version",(r,n)->Map.entry(r.getObject(1,UUID.class),r.getLong(2)),now(),party.toString(),party,id);
        for(var row:changed)audit.append(party.toString(),AuditValues.ActorKind.USER,AuditValues.Action.ADDRESS_UPDATE,AuditValues.Resource.ADDRESS,row.getKey(),correlation,AuditValues.Reason.OPERATOR_REQUEST,Map.of("version",row.getValue()-1),Map.of("version",row.getValue()),null);
    }
    public void insertAddress(UUID party,PartyValues.Address a,UUID correlation){
        Integer count=jdbc.queryForObject("SELECT count(*) FROM commerce.address WHERE party_id=? AND deleted_at IS NULL",Integer.class,party);if(count!=null&&count>=100)throw new Problem.Failure(ProblemCode.PARTY_RULE_VIOLATION);
        if(a.isDefault())defaultAddress(party,a.id(),correlation);
        jdbc.update("INSERT INTO commerce.address(id,created_by,updated_by,party_id,organization_id,label,recipient,phone,line1,line2,city,region,postal_code,country_code,is_default) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",a.id(),party.toString(),party.toString(),party,a.organizationId(),a.label(),a.recipient(),a.phone(),a.line1(),a.line2(),a.city(),a.region(),a.postalCode(),a.countryCode(),a.isDefault());
    }
    public void patchAddress(UUID party,PartyValues.Address before,PartyValues.Address a,UUID correlation){
        if(a.isDefault())defaultAddress(party,a.id(),correlation);
        if(jdbc.update("UPDATE commerce.address SET organization_id=?,label=?,recipient=?,phone=?,line1=?,line2=?,city=?,region=?,postal_code=?,country_code=?,is_default=?,version=version+1,updated_by=?,updated_at=? WHERE id=? AND party_id=? AND version=? AND deleted_at IS NULL",a.organizationId(),a.label(),a.recipient(),a.phone(),a.line1(),a.line2(),a.city(),a.region(),a.postalCode(),a.countryCode(),a.isDefault(),party.toString(),now(),a.id(),party,before.version())!=1)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
    }
    public void deleteAddress(UUID party,PartyValues.Address a){
        if(jdbc.update("UPDATE commerce.address SET deleted_at=?,deleted_by=?,is_default=false,version=version+1,updated_at=?,updated_by=? WHERE party_id=? AND id=? AND version=? AND deleted_at IS NULL",now(),party.toString(),now(),party.toString(),party,a.id(),a.version())!=1)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
    }
    public UUID provision(PartyValues.VerifiedIdentity identity){
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"party-identity:"+identity.issuer()+":"+identity.subject());
        UUID id=EventCodec.uuid7(clock.instant());
        int created=jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,email_verified,display_name) VALUES (?,'oidc-provisioning','oidc-provisioning',?,?,?,?,?) ON CONFLICT(identity_issuer,identity_subject) DO NOTHING",id,identity.issuer(),identity.subject(),identity.email(),identity.emailVerified(),identity.displayName());
        var rows=jdbc.query("SELECT id,status FROM commerce.party WHERE identity_issuer=? AND identity_subject=? FOR UPDATE",(r,n)->Map.entry(r.getObject(1,UUID.class),r.getString(2)),identity.issuer(),identity.subject());
        if(rows.size()!=1||!rows.getFirst().getValue().equals("ACTIVE"))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        id=rows.getFirst().getKey();
        // Signed identity email updates never remap a subject or merge parties by address.
        long previous=profile(id).version();
        int updated=jdbc.update("UPDATE commerce.party SET email=?,email_verified=?,version=version+1,updated_by='oidc-provisioning',updated_at=? WHERE id=? AND (email IS DISTINCT FROM ? OR email_verified IS DISTINCT FROM ?)",identity.email(),identity.emailVerified(),now(),id,identity.email(),identity.emailVerified());
        jdbc.update("INSERT INTO commerce.customer_profile(id,created_by,updated_by,party_id) VALUES (?,'oidc-provisioning','oidc-provisioning',?) ON CONFLICT(party_id) DO NOTHING",EventCodec.uuid7(clock.instant()),id);
        if(created==1||updated==1){var context=com.nigson.shared.observability.WireContext.current();UUID correlation=context==null?EventCodec.uuid7(clock.instant()):context.correlationId;
            audit.append("oidc-provisioning",AuditValues.ActorKind.SYSTEM,created==1?AuditValues.Action.PARTY_PROVISION:AuditValues.Action.PROFILE_UPDATE,AuditValues.Resource.PARTY,id,correlation,AuditValues.Reason.SECURITY_POLICY,created==1?null:Map.of("version",previous),Map.of("version",profile(id).version()),null);
        }return id;
    }
}
