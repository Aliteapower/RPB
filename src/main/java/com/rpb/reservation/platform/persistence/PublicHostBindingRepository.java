package com.rpb.reservation.platform.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublicHostBindingRepository {
    private final JdbcTemplate jdbc;

    public PublicHostBindingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsertPendingBinding(
        UUID hostAliasId,
        UUID tenantId,
        String hostPrefix,
        String hostType,
        String hostname
    ) {
        jdbc.update(
            """
            insert into public_host_bindings (
                host_alias_id, tenant_id, host_prefix, host_type, hostname, tls_status
            )
            values (?, ?, ?, ?, ?, 'pending')
            on conflict (host_alias_id) where deleted_at is null
            do update set
                host_prefix = excluded.host_prefix,
                host_type = excluded.host_type,
                hostname = excluded.hostname,
                tls_status = case
                    when public_host_bindings.tls_status = 'covered'
                     and public_host_bindings.hostname = excluded.hostname
                    then 'covered'
                    else 'pending'
                end,
                certificate_name = case
                    when public_host_bindings.hostname = excluded.hostname
                    then public_host_bindings.certificate_name
                    else null
                end,
                covered_at = case
                    when public_host_bindings.tls_status = 'covered'
                     and public_host_bindings.hostname = excluded.hostname
                    then public_host_bindings.covered_at
                    else null
                end,
                last_checked_at = case
                    when public_host_bindings.hostname = excluded.hostname
                    then public_host_bindings.last_checked_at
                    else null
                end,
                last_error = null,
                updated_at = now(),
                version = public_host_bindings.version + 1
            """,
            hostAliasId,
            tenantId,
            hostPrefix,
            hostType,
            hostname
        );
    }

    public void archiveBinding(UUID hostAliasId) {
        jdbc.update(
            """
            update public_host_bindings
            set tls_status = 'archived',
                deleted_at = coalesce(deleted_at, now()),
                updated_at = now(),
                version = version + 1
            where host_alias_id = ?
              and deleted_at is null
            """,
            hostAliasId
        );
    }
}
