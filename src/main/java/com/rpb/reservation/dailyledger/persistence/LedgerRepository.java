package com.rpb.reservation.dailyledger.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rpb.reservation.common.scope.StoreScope;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public LedgerRepository(JdbcTemplate jdbc,ObjectMapper json) {this.jdbc=jdbc;this.json=json;}
    private UUID tenant(StoreScope s){return s.tenantId().value();}
    private UUID store(StoreScope s){return s.storeId().value();}
    private JsonNode parse(String s){try{return json.readTree(s);}catch(Exception e){throw new IllegalStateException("Invalid persisted ledger JSON",e);}}
    public record Settings(JsonNode config,JsonNode branding,int version) {}
    public com.rpb.reservation.dailyledger.application.LedgerStoreDisplay display(StoreScope s) {
        return jdbc.queryForObject("select timezone,locale,date_format,time_format,currency from stores where tenant_id=? and id=?",
            (rs,n)->new com.rpb.reservation.dailyledger.application.LedgerStoreDisplay(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),tenant(s),store(s));
    }
    public record StoredRecord(String period,int version,JsonNode payload,JsonNode submitted) {}
    public record SavedRequest(UUID actorId,String requestHash,LocalDate date,String period) {}
    public Optional<Settings> settings(StoreScope s) {
        return jdbc.query("select config::text,branding::text,version from daily_ledger_settings where tenant_id=? and store_id=?",
            (rs,n)->new Settings(parse(rs.getString(1)),parse(rs.getString(2)),rs.getInt(3)),tenant(s),store(s)).stream().findFirst();
    }
    public void lockSettings(StoreScope s,JsonNode defaults) {
        jdbc.update("insert into daily_ledger_settings(tenant_id,store_id,config) values(?,?,?::jsonb) on conflict do nothing",tenant(s),store(s),defaults.toString());
        jdbc.queryForObject("select version from daily_ledger_settings where tenant_id=? and store_id=? for update",Integer.class,tenant(s),store(s));
    }
    public boolean updateSettings(StoreScope s,JsonNode config,JsonNode branding,int version) {
        return jdbc.update("update daily_ledger_settings set config=?::jsonb,branding=?::jsonb,version=version+1,updated_at=now() where tenant_id=? and store_id=? and version=?",
            config.toString(),branding.toString(),tenant(s),store(s),version)==1;
    }
    public Optional<JsonNode> dayConfig(StoreScope s,LocalDate date) {
        return jdbc.query("select config::text from daily_ledger_days where tenant_id=? and store_id=? and business_date=?",
            (rs,n)->parse(rs.getString(1)),tenant(s),store(s),date).stream().findFirst();
    }
    public void createDay(StoreScope s,LocalDate date,JsonNode config) {
        jdbc.update("insert into daily_ledger_days(tenant_id,store_id,business_date,config) values(?,?,?,?::jsonb) on conflict do nothing",tenant(s),store(s),date,config.toString());
        jdbc.queryForObject("select business_date from daily_ledger_days where tenant_id=? and store_id=? and business_date=? for update",LocalDate.class,tenant(s),store(s),date);
    }
    public List<StoredRecord> records(StoreScope s,LocalDate date) {
        return jdbc.query("select period,version,payload::text,submitted_payload::text from daily_ledger_records where tenant_id=? and store_id=? and business_date=? order by period",
            (rs,n)->new StoredRecord(rs.getString(1),rs.getInt(2),parse(rs.getString(3)),rs.getString(4)==null?null:parse(rs.getString(4))),tenant(s),store(s),date);
    }
    public boolean writeRecord(StoreScope s,LocalDate date,String period,int version,JsonNode payload,JsonNode submitted) {
        if(version==0)return jdbc.update("""
            insert into daily_ledger_records(tenant_id,store_id,business_date,period,version,payload,submitted_payload)
            values(?,?,?,?,1,?::jsonb,?::jsonb) on conflict do nothing
            """,tenant(s),store(s),date,period,payload.toString(),submitted==null?null:submitted.toString())==1;
        return jdbc.update("""
            update daily_ledger_records set version=version+1,payload=?::jsonb,submitted_payload=?::jsonb,updated_at=now()
            where tenant_id=? and store_id=? and business_date=? and period=? and version=?
            """,payload.toString(),submitted==null?null:submitted.toString(),tenant(s),store(s),date,period,version)==1;
    }
    public Optional<SavedRequest> savedRequest(StoreScope s,String key) {
        return jdbc.query("select actor_id,request_hash,business_date,period from daily_ledger_saves where tenant_id=? and store_id=? and client_save_id=?",
            (rs,n)->new SavedRequest(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,LocalDate.class),rs.getString(4)),tenant(s),store(s),key).stream().findFirst();
    }
    public void saveRequest(StoreScope s,LocalDate date,String period,String key,UUID actor,String hash) {
        jdbc.update("insert into daily_ledger_saves(tenant_id,store_id,client_save_id,business_date,period,actor_id,request_hash) values(?,?,?,?,?,?,?)",tenant(s),store(s),key,date,period,actor,hash);
    }
    public void audit(StoreScope s,LocalDate date,String period,String action,UUID actor,String name,JsonNode payload) {
        jdbc.update("insert into daily_ledger_audit(tenant_id,store_id,business_date,period,action,actor_id,actor_name,payload) values(?,?,?,?,?,?,?,?::jsonb)",tenant(s),store(s),date,period,action,actor,name,payload.toString());
    }
    public List<JsonNode> audit(StoreScope s,LocalDate date,String period) {
        return jdbc.query("select id,actor_name,created_at,payload::text,action from daily_ledger_audit where tenant_id=? and store_id=? and business_date=? and period=? order by created_at desc limit 1000",
            (rs,n)->{ObjectNode row=json.createObjectNode().put("id",rs.getObject(1).toString()).put("actor",rs.getString(2)).put("saved_at",rs.getObject(3,OffsetDateTime.class).toString()).put("action",rs.getString(5));row.set("payload",parse(rs.getString(4)));return row;},tenant(s),store(s),date,period);
    }
    public List<LocalDate> dates(StoreScope s,LocalDate start,LocalDate end) {
        return jdbc.queryForList("select business_date from daily_ledger_days where tenant_id=? and store_id=? and business_date between ? and ? order by business_date desc",LocalDate.class,tenant(s),store(s),start,end);
    }
    public List<Integer> years(StoreScope s) {return jdbc.queryForList("select distinct extract(year from business_date)::int from daily_ledger_days where tenant_id=? and store_id=? order by 1 desc",Integer.class,tenant(s),store(s));}
    public String actorName(UUID actor) {return jdbc.queryForObject("select display_name from auth_accounts where id=? and deleted_at is null",String.class,actor);}
}
