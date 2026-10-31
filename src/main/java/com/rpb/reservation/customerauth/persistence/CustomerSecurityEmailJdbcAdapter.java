package com.rpb.reservation.customerauth.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.customerauth.application.port.out.CustomerSecurityEmailPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerSecurityEmailJdbcAdapter implements CustomerSecurityEmailPort {
    private final JdbcTemplate jdbc;
    public CustomerSecurityEmailJdbcAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Account> account(StoreScope s, UUID customer) {
        return jdbc.query("""
            select a.id,a.customer_id,a.email,a.email_verified_at is not null as verified
            from customer_auth_accounts a join customers c on c.id=a.customer_id and c.tenant_id=a.tenant_id
            where a.tenant_id=? and a.customer_id=? and a.status='active' and a.deleted_at is null
              and c.status='active' and c.deleted_at is null
            order by a.created_at,a.id limit 1
            """, (r,n) -> new Account(r.getObject("id",UUID.class),r.getObject("customer_id",UUID.class),r.getString("email"),r.getBoolean("verified")), s.tenantId().value(),customer).stream().findFirst();
    }
    public Optional<UUID> customerByPhone(StoreScope s, String phone) {
        return jdbc.query("select id from customers where tenant_id=? and phone_e164=? and status='active' and deleted_at is null",
            (r,n) -> r.getObject(1,UUID.class),s.tenantId().value(),phone).stream().findFirst();
    }
    public boolean emailAvailable(StoreScope s, UUID account, String email) {
        return !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from customer_auth_accounts where tenant_id=? and lower(email)=? and id<>? and deleted_at is null)",Boolean.class,s.tenantId().value(),email,account));
    }
    public void bind(StoreScope s, UUID account, String email) {
        jdbc.update("update customer_auth_accounts set email=?,email_verified_at=now(),updated_at=now(),version=version+1 where tenant_id=? and id=? and status='active' and deleted_at is null",email,s.tenantId().value(),account);
    }
    public void issue(StoreScope s, UUID customer, UUID account, String purpose, UUID id, String email, String hash, Instant expires) {
        invalidate(s,customer,purpose);
        jdbc.update("""
            insert into customer_security_email_codes(id,tenant_id,store_id,customer_id,auth_account_id,purpose,email,code_hash,expires_at)
            values(?,?,?,?,?,?,?,?,?)
            """,id,s.tenantId().value(),s.storeId().value(),customer,account,purpose,email,hash,Timestamp.from(expires));
    }
    public Optional<Challenge> latest(StoreScope s, UUID customer, String purpose) {
        return jdbc.query("""
            select id,auth_account_id,email,code_hash,attempts,expires_at from customer_security_email_codes
            where tenant_id=? and store_id=? and customer_id=? and purpose=? and consumed_at is null
            order by created_at desc,id desc limit 1 for update
            """,(r,n) -> new Challenge(r.getObject("id",UUID.class),r.getObject("auth_account_id",UUID.class),r.getString("email"),r.getString("code_hash"),r.getInt("attempts"),r.getTimestamp("expires_at").toInstant()),s.tenantId().value(),s.storeId().value(),customer,purpose).stream().findFirst();
    }
    public void fail(StoreScope s, UUID id) {
        jdbc.update("update customer_security_email_codes set attempts=least(attempts+1,5),updated_at=now() where tenant_id=? and store_id=? and id=?",s.tenantId().value(),s.storeId().value(),id);
    }
    public void consume(StoreScope s, UUID id) {
        jdbc.update("update customer_security_email_codes set consumed_at=now(),updated_at=now() where tenant_id=? and store_id=? and id=?",s.tenantId().value(),s.storeId().value(),id);
    }
    public void invalidate(StoreScope s, UUID customer, String purpose) {
        jdbc.update("update customer_security_email_codes set consumed_at=now(),updated_at=now() where tenant_id=? and customer_id=? and purpose=? and consumed_at is null",s.tenantId().value(),customer,purpose);
    }
    public void invalidateAll(TenantScope s, UUID customer) {
        jdbc.update("update customer_security_email_codes set consumed_at=now(),updated_at=now() where tenant_id=? and customer_id=? and consumed_at is null",s.tenantId().value(),customer);
    }
}
