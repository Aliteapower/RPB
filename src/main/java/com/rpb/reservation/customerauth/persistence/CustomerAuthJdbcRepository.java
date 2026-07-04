package com.rpb.reservation.customerauth.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerEmailSettings;
import com.rpb.reservation.customerauth.application.CustomerOAuthProviderSettings;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthIntegrationManagementPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthRepositoryPort.CustomerAuthAccountRecord;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthRepositoryPort.CustomerAuthSessionRecord;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthRepositoryPort.EmailCodeRecord;
import com.rpb.reservation.customerauth.application.port.out.CustomerEmailSettingsRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerOAuthSettingsRepositoryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerAuthJdbcRepository implements
    CustomerAuthRepositoryPort,
    CustomerEmailSettingsRepositoryPort,
    CustomerOAuthSettingsRepositoryPort,
    CustomerAuthIntegrationManagementPort {
    private final JdbcTemplate jdbc;

    public CustomerAuthJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void createEmailCode(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String email,
        String codeHash,
        Instant expiresAt,
        String remoteAddr,
        String userAgent
    ) {
        jdbc.update(
            """
            insert into customer_email_login_codes (
                id, tenant_id, store_id, email, code_hash, status,
                expires_at, remote_addr, user_agent
            )
            values (?, ?, ?, ?, ?, 'created', ?, ?, ?)
            """,
            id,
            tenantId,
            storeId,
            email,
            codeHash,
            Timestamp.from(expiresAt),
            remoteAddr,
            userAgent
        );
    }

    @Override
    public Optional<EmailCodeRecord> findLatestUsableEmailCode(UUID tenantId, UUID storeId, String email) {
        return jdbc.query(
            """
            select id, tenant_id, store_id, email, code_hash, status, attempt_count, max_attempts, expires_at
            from customer_email_login_codes
            where tenant_id = ?
              and store_id = ?
              and lower(email) = lower(?)
              and status = 'created'
              and deleted_at is null
              and attempt_count < max_attempts
            order by created_at desc
            limit 1
            """,
            (rs, rowNum) -> emailCode(rs),
            tenantId,
            storeId,
            email
        ).stream().findFirst();
    }

    @Override
    public void markEmailCodeConsumed(UUID codeId) {
        jdbc.update(
            """
            update customer_email_login_codes
            set status = 'consumed',
                consumed_at = now(),
                updated_at = now(),
                version = version + 1
            where id = ?
            """,
            codeId
        );
    }

    @Override
    public void markEmailCodeFailed(UUID codeId) {
        jdbc.update(
            """
            update customer_email_login_codes
            set status = case when attempt_count + 1 >= max_attempts then 'failed' else status end,
                attempt_count = attempt_count + 1,
                updated_at = now(),
                version = version + 1
            where id = ?
            """,
            codeId
        );
    }

    @Override
    public void markEmailCodeExpired(UUID codeId) {
        jdbc.update(
            """
            update customer_email_login_codes
            set status = 'expired',
                updated_at = now(),
                version = version + 1
            where id = ?
              and status = 'created'
            """,
            codeId
        );
    }

    @Override
    public Optional<CustomerAuthAccountRecord> findAccountByEmail(UUID tenantId, String email) {
        return jdbc.query(
            """
            select id, tenant_id, customer_id, email, display_name, status
            from customer_auth_accounts
            where tenant_id = ?
              and lower(email) = lower(?)
              and deleted_at is null
            """,
            (rs, rowNum) -> account(rs),
            tenantId,
            email
        ).stream().findFirst();
    }

    @Override
    public Optional<CustomerAuthAccountRecord> findAccountByIdentity(UUID tenantId, String provider, String providerSubject) {
        return jdbc.query(
            """
            select account.id, account.tenant_id, account.customer_id, account.email, account.display_name, account.status
            from customer_auth_identities identity
            join customer_auth_accounts account on account.id = identity.auth_account_id
            where identity.tenant_id = ?
              and identity.provider = ?
              and identity.provider_subject = ?
              and identity.deleted_at is null
              and account.deleted_at is null
              and account.status = 'active'
            """,
            (rs, rowNum) -> account(rs),
            tenantId,
            provider,
            providerSubject
        ).stream().findFirst();
    }

    @Override
    public CustomerAuthAccountRecord createAccount(UUID accountId, UUID tenantId, UUID customerId, String email, String displayName) {
        jdbc.update(
            """
            insert into customer_auth_accounts (
                id, tenant_id, customer_id, email, display_name, status, email_verified_at
            )
            values (?, ?, ?, ?, ?, 'active', now())
            """,
            accountId,
            tenantId,
            customerId,
            email,
            displayName
        );
        return new CustomerAuthAccountRecord(accountId, tenantId, customerId, email, displayName, "active");
    }

    @Override
    public void markEmailVerified(UUID accountId, String displayName) {
        jdbc.update(
            """
            update customer_auth_accounts
            set display_name = coalesce(?, display_name),
                email_verified_at = coalesce(email_verified_at, now()),
                updated_at = now(),
                version = version + 1
            where id = ?
            """,
            displayName,
            accountId
        );
    }

    @Override
    public void linkIdentity(
        UUID tenantId,
        UUID authAccountId,
        String provider,
        String providerSubject,
        String email,
        String displayName
    ) {
        jdbc.update(
            """
            insert into customer_auth_identities (
                tenant_id, auth_account_id, provider, provider_subject, email, display_name
            )
            values (?, ?, ?, ?, ?, ?)
            on conflict do nothing
            """,
            tenantId,
            authAccountId,
            provider,
            providerSubject,
            email,
            displayName
        );
    }

    @Override
    public void createSession(
        UUID sessionId,
        CustomerAuthAccountRecord account,
        String sessionHash,
        Instant expiresAt,
        String remoteAddr,
        String userAgent
    ) {
        jdbc.update(
            """
            insert into customer_auth_sessions (
                id, tenant_id, auth_account_id, customer_id, session_hash,
                status, expires_at, remote_addr, user_agent
            )
            values (?, ?, ?, ?, ?, 'active', ?, ?, ?)
            """,
            sessionId,
            account.tenantId(),
            account.id(),
            account.customerId(),
            sessionHash,
            Timestamp.from(expiresAt),
            remoteAddr,
            userAgent
        );
    }

    @Override
    public Optional<CustomerAuthSessionRecord> findActiveSessionByHash(String sessionHash) {
        return jdbc.query(
            """
            select
                session.id as session_id,
                session.expires_at,
                account.id,
                account.tenant_id,
                account.customer_id,
                account.email,
                account.display_name,
                account.status
            from customer_auth_sessions session
            join customer_auth_accounts account on account.id = session.auth_account_id
            where session.session_hash = ?
              and session.status = 'active'
              and session.deleted_at is null
              and session.expires_at > now()
              and account.deleted_at is null
              and account.status = 'active'
            """,
            (rs, rowNum) -> new CustomerAuthSessionRecord(
                rs.getObject("session_id", UUID.class),
                account(rs),
                rs.getTimestamp("expires_at").toInstant()
            ),
            sessionHash
        ).stream().findFirst();
    }

    @Override
    public void touchSession(UUID sessionId) {
        jdbc.update(
            """
            update customer_auth_sessions
            set last_seen_at = now(),
                updated_at = now(),
                version = version + 1
            where id = ?
              and status = 'active'
            """,
            sessionId
        );
    }

    @Override
    public void revokeSession(String sessionHash) {
        jdbc.update(
            """
            update customer_auth_sessions
            set status = 'revoked',
                revoked_at = now(),
                updated_at = now(),
                version = version + 1
            where session_hash = ?
              and status = 'active'
            """,
            sessionHash
        );
    }

    @Override
    public Optional<CustomerEmailSettings> findEmailSettings(UUID tenantId, UUID storeId) {
        return jdbc.query(
            """
            select enabled, provider, from_email, from_name, smtp_host, smtp_port,
                   smtp_username, smtp_password_secret, smtp_start_tls
            from store_customer_email_settings
            where tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> emailSettings(rs),
            tenantId,
            storeId
        ).stream().findFirst();
    }

    @Override
    public Optional<CustomerOAuthProviderSettings> findProviderSettings(UUID tenantId, UUID storeId, String provider) {
        return jdbc.query(
            """
            select enabled, provider, client_id, client_secret_secret
            from store_customer_oauth_provider_settings
            where tenant_id = ?
              and store_id = ?
              and provider = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> oauthSettings(rs),
            tenantId,
            storeId,
            provider
        ).stream().findFirst();
    }

    @Override
    public CustomerEmailSettings saveEmailSettings(StoreScope scope, CustomerEmailSettings settings) {
        int updated = jdbc.update(
            """
            update store_customer_email_settings
            set enabled = ?,
                provider = ?,
                from_email = ?,
                from_name = ?,
                smtp_host = ?,
                smtp_port = ?,
                smtp_username = ?,
                smtp_password_secret = ?,
                smtp_start_tls = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """,
            settings.enabled(),
            settings.provider(),
            settings.fromEmail(),
            settings.fromName(),
            settings.smtpHost(),
            settings.smtpPort(),
            settings.smtpUsername(),
            settings.smtpPassword(),
            settings.smtpStartTls(),
            scope.tenantId().value(),
            scope.storeId().value()
        );
        if (updated == 0) {
            jdbc.update(
                """
                insert into store_customer_email_settings (
                    tenant_id, store_id, enabled, provider, from_email, from_name,
                    smtp_host, smtp_port, smtp_username, smtp_password_secret, smtp_start_tls
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                scope.tenantId().value(),
                scope.storeId().value(),
                settings.enabled(),
                settings.provider(),
                settings.fromEmail(),
                settings.fromName(),
                settings.smtpHost(),
                settings.smtpPort(),
                settings.smtpUsername(),
                settings.smtpPassword(),
                settings.smtpStartTls()
            );
        }
        return findEmailSettings(scope.tenantId().value(), scope.storeId().value()).orElse(settings);
    }

    @Override
    public CustomerOAuthProviderSettings saveOAuthProviderSettings(StoreScope scope, CustomerOAuthProviderSettings settings) {
        int updated = jdbc.update(
            """
            update store_customer_oauth_provider_settings
            set enabled = ?,
                client_id = ?,
                client_secret_secret = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and store_id = ?
              and provider = ?
              and deleted_at is null
            """,
            settings.enabled(),
            settings.clientId(),
            settings.clientSecret(),
            scope.tenantId().value(),
            scope.storeId().value(),
            settings.provider()
        );
        if (updated == 0) {
            jdbc.update(
                """
                insert into store_customer_oauth_provider_settings (
                    tenant_id, store_id, provider, enabled, client_id, client_secret_secret
                )
                values (?, ?, ?, ?, ?, ?)
                """,
                scope.tenantId().value(),
                scope.storeId().value(),
                settings.provider(),
                settings.enabled(),
                settings.clientId(),
                settings.clientSecret()
            );
        }
        return findProviderSettings(
            scope.tenantId().value(),
            scope.storeId().value(),
            settings.provider()
        ).orElse(settings);
    }

    private static EmailCodeRecord emailCode(ResultSet rs) throws SQLException {
        return new EmailCodeRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("store_id", UUID.class),
            rs.getString("email"),
            rs.getString("code_hash"),
            rs.getString("status"),
            rs.getInt("attempt_count"),
            rs.getInt("max_attempts"),
            rs.getTimestamp("expires_at").toInstant()
        );
    }

    private static CustomerAuthAccountRecord account(ResultSet rs) throws SQLException {
        return new CustomerAuthAccountRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("customer_id", UUID.class),
            rs.getString("email"),
            rs.getString("display_name"),
            rs.getString("status")
        );
    }

    private static CustomerEmailSettings emailSettings(ResultSet rs) throws SQLException {
        String password = rs.getString("smtp_password_secret");
        return new CustomerEmailSettings(
            rs.getBoolean("enabled"),
            rs.getString("provider"),
            rs.getString("from_email"),
            rs.getString("from_name"),
            rs.getString("smtp_host"),
            rs.getInt("smtp_port"),
            rs.getString("smtp_username"),
            password,
            rs.getBoolean("smtp_start_tls"),
            password != null && !password.isBlank()
        );
    }

    private static CustomerOAuthProviderSettings oauthSettings(ResultSet rs) throws SQLException {
        String secret = rs.getString("client_secret_secret");
        return new CustomerOAuthProviderSettings(
            rs.getBoolean("enabled"),
            rs.getString("provider"),
            rs.getString("client_id"),
            secret,
            secret != null && !secret.isBlank()
        );
    }
}
