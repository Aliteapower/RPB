package com.rpb.reservation.customerauth.persistence;

import com.rpb.reservation.common.scope.*;
import com.rpb.reservation.customerauth.application.port.out.CustomerActivationPort;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.rpb.reservation.customerauth.application.CustomerActivationState;

@Repository
public class CustomerActivationJdbcAdapter implements CustomerActivationPort {
    private final JdbcTemplate jdbc;
    public CustomerActivationJdbcAdapter(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Map<UUID,CustomerActivationState> states(TenantScope scope,Set<UUID> customers){
        if(customers.isEmpty())return Map.of();
        var result=new HashMap<UUID,CustomerActivationState>();
        new NamedParameterJdbcTemplate(jdbc).query("""
            select c.id,case
              when c.status<>'active' or c.phone_e164 is null or btrim(c.phone_e164)='' or exists(
                select 1 from customer_auth_accounts a where a.tenant_id=c.tenant_id and a.customer_id=c.id
                and a.deleted_at is null and a.status<>'active') then 'unavailable'
              when exists(select 1 from customer_password_credentials p where p.tenant_id=c.tenant_id
                and p.customer_id=c.id and not p.password_change_required) then 'activated'
              when exists(select 1 from customer_auth_accounts a where a.tenant_id=c.tenant_id and a.customer_id=c.id
                and a.status='active' and a.deleted_at is null and a.email is not null and a.email_verified_at is not null) then 'email_verified'
              when exists(select 1 from customer_activation_codes x where x.tenant_id=c.tenant_id
                and x.customer_id=c.id and x.identity_confirmed_at is not null) then 'verified_pending'
              else 'required' end as activation_state
            from customers c where c.tenant_id=:tenant and c.id in (:customers) and c.deleted_at is null
            """,Map.of("tenant",scope.tenantId().value(),"customers",customers),r->{
                result.put(r.getObject("id",UUID.class),CustomerActivationState.valueOf(r.getString("activation_state")));
            });
        return Map.copyOf(result);
    }
    public void issue(StoreScope s,UUID customer,UUID actor,UUID id,String hash,Instant expires){
        consumeAll(s.tenantScope(),customer);
        jdbc.update("insert into customer_activation_codes(id,tenant_id,store_id,customer_id,issued_by,code_hash,expires_at) values(?,?,?,?,?,?,?)",id,s.tenantId().value(),s.storeId().value(),customer,actor,hash,Timestamp.from(expires));
    }
    public Optional<Challenge> latest(StoreScope s,UUID customer){
        return jdbc.query("""
            select id,code_hash,attempts,expires_at from customer_activation_codes
            where tenant_id=? and store_id=? and customer_id=? and consumed_at is null
            order by created_at desc,id desc limit 1 for update
            """,(r,n)->new Challenge(r.getObject("id",UUID.class),r.getString("code_hash"),r.getInt("attempts"),r.getTimestamp("expires_at").toInstant()),s.tenantId().value(),s.storeId().value(),customer).stream().findFirst();
    }
    public void fail(StoreScope s,UUID id){jdbc.update("update customer_activation_codes set attempts=least(attempts+1,5),updated_at=now() where tenant_id=? and store_id=? and id=?",s.tenantId().value(),s.storeId().value(),id);}
    public void consumeAll(TenantScope s,UUID customer){jdbc.update("update customer_activation_codes set consumed_at=now(),updated_at=now() where tenant_id=? and customer_id=? and consumed_at is null",s.tenantId().value(),customer);}
    public void audit(StoreScope s,UUID customer,UUID actor,String action){
        jdbc.update("insert into audit_logs(tenant_id,store_id,operation_code,target_type,target_id,actor_type,actor_id,source) values(?,?,?,'customer',?,?,?,?)",s.tenantId().value(),s.storeId().value(),action,customer,action.endsWith("issued")?"staff":"customer",actor,action.endsWith("issued")?"staff":"customer");
    }
}
