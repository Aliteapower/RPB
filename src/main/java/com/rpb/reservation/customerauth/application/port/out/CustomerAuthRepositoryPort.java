package com.rpb.reservation.customerauth.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAuthRepositoryPort {

    void createEmailCode(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String email,
        String codeHash,
        Instant expiresAt,
        String remoteAddr,
        String userAgent
    );

    Optional<EmailCodeRecord> findLatestUsableEmailCode(UUID tenantId, UUID storeId, String email);

    void markEmailCodeConsumed(UUID codeId);

    void markEmailCodeFailed(UUID codeId);

    void markEmailCodeExpired(UUID codeId);

    Optional<CustomerAuthAccountRecord> findAccountByEmail(UUID tenantId, String email);

    Optional<CustomerAuthAccountRecord> findAccountByIdentity(UUID tenantId, String provider, String providerSubject);

    CustomerAuthAccountRecord createAccount(UUID accountId, UUID tenantId, UUID customerId, String email, String displayName);

    void markEmailVerified(UUID accountId, String displayName);

    void linkIdentity(
        UUID tenantId,
        UUID authAccountId,
        String provider,
        String providerSubject,
        String email,
        String displayName
    );

    void createSession(
        UUID sessionId,
        CustomerAuthAccountRecord account,
        String sessionHash,
        Instant expiresAt,
        String remoteAddr,
        String userAgent
    );

    Optional<CustomerAuthSessionRecord> findActiveSessionByHash(String sessionHash);

    void touchSession(UUID sessionId);

    void revokeSession(String sessionHash);

    record EmailCodeRecord(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String email,
        String codeHash,
        String status,
        int attemptCount,
        int maxAttempts,
        Instant expiresAt
    ) {
    }

    record CustomerAuthAccountRecord(
        UUID id,
        UUID tenantId,
        UUID customerId,
        String email,
        String displayName,
        String status
    ) {
    }

    record CustomerAuthSessionRecord(
        UUID sessionId,
        CustomerAuthAccountRecord account,
        Instant expiresAt
    ) {
    }
}
