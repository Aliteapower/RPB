package com.rpb.reservation.customerauth.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CustomerSecurityEmailPort {
    record Account(UUID id, UUID customerId, String email, boolean verified) {}
    record Challenge(UUID id, UUID accountId, String email, String codeHash, int attempts, Instant expiresAt) {}
    Optional<Account> account(StoreScope scope, UUID customerId);
    Optional<UUID> customerByPhone(StoreScope scope, String phone);
    boolean emailAvailable(StoreScope scope, UUID accountId, String email);
    void bind(StoreScope scope, UUID accountId, String email);
    void issue(StoreScope scope, UUID customerId, UUID accountId, String purpose, UUID id, String email, String hash, Instant expiresAt);
    Optional<Challenge> latest(StoreScope scope, UUID customerId, String purpose);
    void fail(StoreScope scope, UUID id);
    void consume(StoreScope scope, UUID id);
    void invalidate(StoreScope scope, UUID customerId, String purpose);
    void invalidateAll(TenantScope scope, UUID customerId);
}
