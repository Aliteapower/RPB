package com.rpb.reservation.customerauth.persistence;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.customerauth.application.CustomerAuthError;
import com.rpb.reservation.customerauth.application.CustomerAuthServiceException;
import com.rpb.reservation.customerauth.application.port.out.CustomerPasswordRepositoryPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerPasswordJdbcRepository implements CustomerPasswordRepositoryPort {
    private final JdbcTemplate jdbc;
    public CustomerPasswordJdbcRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void recordAttempt(TenantScope scope, String subject, String remoteAddr) {
        // Always acquire buckets in this order; UPSERT serializes requests across backend instances.
        int ip = increment(scope, "ip:" + digest(remoteAddr == null ? "unknown" : remoteAddr));
        int phone = increment(scope, "subject:" + digest(subject));
        if (ip > 50 || phone > 10) throw new CustomerAuthServiceException(CustomerAuthError.RATE_LIMITED);
    }

    private int increment(TenantScope scope, String bucket) {
        return jdbc.queryForObject("""
            insert into customer_password_login_limits(tenant_id,bucket_key) values(?,?)
            on conflict (tenant_id,bucket_key) do update set
              attempts = case when customer_password_login_limits.window_started_at < now() - interval '15 minutes'
                then 1 else least(customer_password_login_limits.attempts + 1, 1000000) end,
              window_started_at = case when customer_password_login_limits.window_started_at < now() - interval '15 minutes'
                then now() else customer_password_login_limits.window_started_at end,
              updated_at = now()
            returning attempts
            """, Integer.class, scope.tenantId().value(), bucket);
    }

    @Override public void lockCustomer(TenantScope scope, UUID id) {
        jdbc.queryForList("select id from customers where tenant_id=? and id=? for update",scope.tenantId().value(),id);
    }
    @Override public Optional<String> passwordHash(TenantScope scope, UUID id) {
        return jdbc.query("select password_hash from customer_password_credentials where tenant_id=? and customer_id=?",
            (rs,n) -> rs.getString(1),scope.tenantId().value(),id).stream().findFirst();
    }
    @Override public void savePassword(TenantScope scope, UUID id, String hash) {
        jdbc.update("""
            insert into customer_password_credentials(tenant_id,customer_id,password_hash) values(?,?,?)
            on conflict (tenant_id,customer_id) do update set password_hash=excluded.password_hash,
                updated_at=now(),version=customer_password_credentials.version+1
            """,scope.tenantId().value(),id,hash);
        jdbc.update("""
            insert into audit_logs(tenant_id,operation_code,target_type,target_id,actor_type,actor_id,source)
            values(?,'customer.password.set','customer',?,'customer',?,'customer')
            """,scope.tenantId().value(),id,id);
    }
    @Override public void revokeSessions(TenantScope scope, UUID id) {
        jdbc.update("""
            update customer_auth_sessions set status='revoked',revoked_at=now(),updated_at=now(),version=version+1
            where tenant_id=? and customer_id=? and status='active' and deleted_at is null
            """,scope.tenantId().value(),id);
    }
    @Override public boolean passwordChangeRequired(TenantScope scope, UUID id) {
        return jdbc.query("select password_change_required from customer_password_credentials where tenant_id=? and customer_id=?",
            (r,n)->r.getBoolean(1),scope.tenantId().value(),id).stream().findFirst().orElse(true);
    }
    @Override public void completePasswordChange(TenantScope scope, UUID id) {
        jdbc.update("update customer_password_credentials set password_change_required=false where tenant_id=? and customer_id=?",scope.tenantId().value(),id);
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
