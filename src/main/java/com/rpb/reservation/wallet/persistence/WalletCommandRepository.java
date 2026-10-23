package com.rpb.reservation.wallet.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.domain.*;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Called only inside the owning use-case transaction; serializes a retry before any money mutation. */
@Repository
public class WalletCommandRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public WalletCommandRepository(JdbcTemplate jdbc,ObjectMapper json) {this.jdbc=jdbc;this.json=json;}
    public <T> T run(StoreScope scope,UUID actor,String operation,String key,Object request,Class<T> type,Supplier<T> work) {
        if(key==null || key.isBlank() || key.length()>100 || actor==null) throw WalletException.invalid();
        try {
            String hash=WalletTokens.hash(json.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(java.util.Map.of("storeId",scope.storeId().value(),"request",request)));
            String lock=scope.tenantId().value()+":"+actor+":"+operation+":"+key;
            jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",lock);
            var rows=jdbc.queryForList("select request_hash,response::text from wallet_commands where tenant_id=? and principal_id=? and operation=? and idempotency_key=?",
                scope.tenantId().value(),actor,operation,key);
            if(!rows.isEmpty()) {
                if(!hash.equals(rows.getFirst().get("request_hash"))) throw new WalletException("WALLET_IDEMPOTENCY_CONFLICT",409,"同一请求编号不能用于不同内容");
                return json.readValue((String)rows.getFirst().get("response"),type);
            }
            T result=work.get();
            jdbc.update("insert into wallet_commands(tenant_id,principal_id,operation,idempotency_key,request_hash,response) values(?,?,?,?,?,?::jsonb)",
                scope.tenantId().value(),actor,operation,key,hash,json.writeValueAsString(result));
            return result;
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException("wallet serialization failed",e);}
    }
    /** Serialize recovery with the original command, closing an absent key against late arrival. */
    public <T> T reconcileOrClose(StoreScope scope,UUID actor,String operation,String key,Class<T> type,Supplier<T> closed) {
        if(key==null||key.isBlank()||key.length()>100||actor==null)throw WalletException.invalid();
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",scope.tenantId().value()+":"+actor+":"+operation+":"+key);
        var rows=jdbc.query("select response::text from wallet_commands where tenant_id=? and principal_id=? and operation=? and idempotency_key=?",(rs,n)->rs.getString(1),scope.tenantId().value(),actor,operation,key);
        if(!rows.isEmpty())try{return json.readValue(rows.getFirst(),type);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("wallet serialization failed",e);}
        return run(scope,actor,operation,key,java.util.Map.of("reconciledClosed",true),type,closed);
    }
    public <T> T result(StoreScope scope,UUID actor,String operation,String key,Class<T> type) {
        if(key==null||key.isBlank()||key.length()>100)throw WalletException.invalid();
        String response=jdbc.query("select response::text from wallet_commands where tenant_id=? and principal_id=? and operation=? and idempotency_key=?",(rs,n)->rs.getString(1),scope.tenantId().value(),actor,operation,key).stream().findFirst().orElseThrow(WalletException::missing);
        try{return json.readValue(response,type);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("wallet serialization failed",e);}
    }
    public void audit(StoreScope scope,UUID actor,String action,UUID source,Object details) {
        try {
            jdbc.update("insert into wallet_audit(tenant_id,store_id,actor_id,action,source_id,details) values(?,?,?,?,?,?::jsonb)",
                scope.tenantId().value(),scope.storeId().value(),actor,action,source,json.writeValueAsString(details));
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException(e);}
    }
}
