package com.rpb.reservation.auth.persistence;

import com.rpb.reservation.auth.application.AuthPrincipal;
import com.rpb.reservation.auth.application.AuthStoreAccess;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {
    private final JdbcTemplate jdbc;

    public AuthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertChallenge(SliderChallengeRecord challenge) {
        jdbc.update(
            """
            insert into auth_slider_captcha_challenges (
                id, target_x, target_y, piece_size, image_width, image_height,
                tolerance_px, status, attempt_count, max_attempts, expires_at,
                remote_addr, user_agent
            )
            values (?, ?, ?, ?, ?, ?, ?, 'created', 0, ?, ?, ?, ?)
            """,
            challenge.id(),
            challenge.targetX(),
            challenge.targetY(),
            challenge.pieceSize(),
            challenge.imageWidth(),
            challenge.imageHeight(),
            challenge.tolerancePx(),
            challenge.maxAttempts(),
            Timestamp.from(challenge.expiresAt()),
            challenge.remoteAddr(),
            challenge.userAgent()
        );
    }

    public Optional<SliderChallengeRecord> findChallenge(UUID challengeId) {
        return jdbc.query(
            """
            select id, target_x, target_y, piece_size, image_width, image_height,
                   tolerance_px, status, attempt_count, max_attempts, expires_at,
                   remote_addr, user_agent
            from auth_slider_captcha_challenges
            where id = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> challenge(rs),
            challengeId
        ).stream().findFirst();
    }

    public void markChallengeConsumed(UUID challengeId) {
        jdbc.update(
            """
            update auth_slider_captcha_challenges
            set status = 'consumed',
                attempt_count = attempt_count + 1,
                consumed_at = now(),
                updated_at = now(),
                version = version + 1
            where id = ?
            """,
            challengeId
        );
    }

    public void markChallengeFailed(UUID challengeId) {
        jdbc.update(
            """
            update auth_slider_captcha_challenges
            set status = 'failed',
                attempt_count = attempt_count + 1,
                failed_at = now(),
                updated_at = now(),
                version = version + 1
            where id = ?
            """,
            challengeId
        );
    }

    public void markChallengeExpired(UUID challengeId) {
        jdbc.update(
            """
            update auth_slider_captcha_challenges
            set status = 'expired',
                updated_at = now(),
                version = version + 1
            where id = ?
              and status = 'created'
            """,
            challengeId
        );
    }

    public Optional<AuthAccountRecord> findActiveAccountByUsername(String username) {
        List<AuthAccountRecord> accounts = jdbc.query(
            """
            select id, tenant_id, username, display_name, actor_type, status,
                   password_hash, default_store_id
            from auth_accounts
            where lower(username) = lower(?)
              and deleted_at is null
            """,
            (rs, rowNum) -> account(rs),
            username
        );
        return accounts.size() == 1 ? Optional.of(accounts.get(0)) : Optional.empty();
    }

    public Optional<AuthAccountRecord> findActivePlatformAccountByUsername(String username) {
        return jdbc.query(
            """
            select id, tenant_id, username, display_name, actor_type, status,
                   password_hash, default_store_id
            from auth_accounts
            where actor_type = 'platform_admin'
              and lower(username) = lower(?)
              and deleted_at is null
            """,
            (rs, rowNum) -> account(rs),
            username
        ).stream().findFirst();
    }

    public Optional<AuthAccountRecord> findActiveTenantAccountByTenantCodeAndUsername(
        String tenantCode,
        String actorType,
        String username
    ) {
        return jdbc.query(
            """
            select account.id, account.tenant_id, account.username, account.display_name,
                   account.actor_type, account.status, account.password_hash, account.default_store_id
            from auth_accounts account
            join tenants tenant on tenant.id = account.tenant_id
            where lower(tenant.tenant_code) = lower(?)
              and account.actor_type = ?
              and lower(account.username) = lower(?)
              and tenant.deleted_at is null
              and tenant.status = 'active'
              and account.deleted_at is null
            """,
            (rs, rowNum) -> account(rs),
            tenantCode,
            actorType,
            username
        ).stream().findFirst();
    }

    public void recordSuccessfulLogin(UUID accountId) {
        jdbc.update(
            """
            update auth_accounts
            set failed_login_count = 0,
                locked_until_at = null,
                last_login_at = now(),
                updated_at = now(),
                version = version + 1
            where id = ?
            """,
            accountId
        );
    }

    public void recordFailedLogin(UUID accountId) {
        jdbc.update(
            """
            update auth_accounts
            set failed_login_count = failed_login_count + 1,
                last_login_failed_at = now(),
                updated_at = now(),
                version = version + 1
            where id = ?
            """,
            accountId
        );
    }

    public void createSession(
        UUID sessionId,
        AuthAccountRecord account,
        String sessionHash,
        Instant expiresAt,
        String remoteAddr,
        String userAgent
    ) {
        jdbc.update(
            """
            insert into auth_user_sessions (
                id, account_id, tenant_id, session_hash, status, expires_at,
                remote_addr, user_agent
            )
            values (?, ?, ?, ?, 'active', ?, ?, ?)
            """,
            sessionId,
            account.id(),
            account.tenantId(),
            sessionHash,
            Timestamp.from(expiresAt),
            remoteAddr,
            userAgent
        );
    }

    public Optional<AuthSessionRecord> findActiveSessionByHash(String sessionHash) {
        return jdbc.query(
            """
            select
                session.id as session_id,
                session.expires_at,
                account.id as account_id,
                account.tenant_id,
                account.username,
                account.display_name,
                account.actor_type,
                account.status,
                account.password_hash,
                account.default_store_id
            from auth_user_sessions session
            join auth_accounts account on account.id = session.account_id
            where session.session_hash = ?
              and session.status = 'active'
              and session.deleted_at is null
              and session.expires_at > now()
              and account.deleted_at is null
              and account.status = 'active'
            """,
            (rs, rowNum) -> new AuthSessionRecord(
                rs.getObject("session_id", UUID.class),
                accountFromSession(rs),
                rs.getTimestamp("expires_at").toInstant()
            ),
            sessionHash
        ).stream().findFirst();
    }

    public void touchSession(UUID sessionId) {
        jdbc.update(
            """
            update auth_user_sessions
            set last_seen_at = now(),
                updated_at = now(),
                version = version + 1
            where id = ?
              and status = 'active'
            """,
            sessionId
        );
    }

    public void revokeSession(String sessionHash) {
        jdbc.update(
            """
            update auth_user_sessions
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

    public AuthPrincipal principalFor(AuthAccountRecord account) {
        return new AuthPrincipal(
            account.id(),
            account.tenantId(),
            account.username(),
            account.displayName(),
            account.actorType(),
            account.defaultStoreId(),
            storeIds(account.id()),
            strings("auth_account_roles", "role_code", account.id()),
            strings("auth_account_permissions", "permission_code", account.id())
        );
    }

    public List<AuthStoreAccess> authorizedStores(AuthPrincipal principal) {
        if (principal == null || principal.tenantId() == null || "platform_admin".equals(principal.actorType())) {
            return List.of();
        }
        return jdbc.query(
            """
            select
                tenant.id as tenant_id,
                tenant.tenant_code,
                entity.id as operating_entity_id,
                entity.display_name as operating_entity_name,
                store.id as store_id,
                store.store_code,
                store.display_name,
                store.status,
                store.locale,
                account.default_store_id = store.id as default_store
            from auth_accounts account
            join auth_account_store_access access
              on access.account_id = account.id
             and access.tenant_id = account.tenant_id
             and access.deleted_at is null
            join tenants tenant
              on tenant.id = access.tenant_id
             and tenant.deleted_at is null
             and tenant.status = 'active'
            join stores store
              on store.id = access.store_id
             and store.tenant_id = access.tenant_id
             and store.deleted_at is null
             and store.status = 'active'
            left join operating_entities entity
              on entity.id = store.operating_entity_id
             and entity.tenant_id = store.tenant_id
             and entity.deleted_at is null
            where account.id = ?
              and account.tenant_id = ?
              and account.deleted_at is null
            order by default_store desc, lower(store.display_name), lower(store.store_code), store.id
            """,
            (rs, rowNum) -> new AuthStoreAccess(
                rs.getObject("tenant_id", UUID.class),
                rs.getString("tenant_code"),
                rs.getObject("operating_entity_id", UUID.class),
                rs.getString("operating_entity_name"),
                rs.getObject("store_id", UUID.class),
                rs.getString("store_code"),
                rs.getString("display_name"),
                rs.getString("status"),
                rs.getString("locale"),
                rs.getBoolean("default_store")
            ),
            principal.accountId(),
            principal.tenantId()
        );
    }

    private Set<String> strings(String tableName, String columnName, UUID accountId) {
        return new LinkedHashSet<>(jdbc.queryForList(
            """
            select %s
            from %s
            where account_id = ?
              and deleted_at is null
            order by %s
            """.formatted(columnName, tableName, columnName),
            String.class,
            accountId
        ));
    }

    private Set<UUID> storeIds(UUID accountId) {
        return new LinkedHashSet<>(jdbc.queryForList(
            """
            select store_id
            from auth_account_store_access
            where account_id = ?
              and deleted_at is null
            order by store_id
            """,
            UUID.class,
            accountId
        ));
    }

    private static AuthAccountRecord account(ResultSet rs) throws SQLException {
        return new AuthAccountRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("actor_type"),
            rs.getString("status"),
            rs.getString("password_hash"),
            rs.getObject("default_store_id", UUID.class)
        );
    }

    private static AuthAccountRecord accountFromSession(ResultSet rs) throws SQLException {
        return new AuthAccountRecord(
            rs.getObject("account_id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("actor_type"),
            rs.getString("status"),
            rs.getString("password_hash"),
            rs.getObject("default_store_id", UUID.class)
        );
    }

    private static SliderChallengeRecord challenge(ResultSet rs) throws SQLException {
        return new SliderChallengeRecord(
            rs.getObject("id", UUID.class),
            rs.getInt("target_x"),
            rs.getInt("target_y"),
            rs.getInt("piece_size"),
            rs.getInt("image_width"),
            rs.getInt("image_height"),
            rs.getInt("tolerance_px"),
            rs.getString("status"),
            rs.getInt("attempt_count"),
            rs.getInt("max_attempts"),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getString("remote_addr"),
            rs.getString("user_agent")
        );
    }

    public record AuthAccountRecord(
        UUID id,
        UUID tenantId,
        String username,
        String displayName,
        String actorType,
        String status,
        String passwordHash,
        UUID defaultStoreId
    ) {
    }

    public record AuthSessionRecord(
        UUID sessionId,
        AuthAccountRecord account,
        Instant expiresAt
    ) {
    }

    public record SliderChallengeRecord(
        UUID id,
        int targetX,
        int targetY,
        int pieceSize,
        int imageWidth,
        int imageHeight,
        int tolerancePx,
        String status,
        int attemptCount,
        int maxAttempts,
        Instant expiresAt,
        String remoteAddr,
        String userAgent
    ) {
    }
}
