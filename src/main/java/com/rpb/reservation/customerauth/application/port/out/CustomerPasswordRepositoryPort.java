package com.rpb.reservation.customerauth.application.port.out;

import com.rpb.reservation.common.scope.TenantScope;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPasswordRepositoryPort {
    void recordAttempt(TenantScope scope, String subject, String remoteAddr);
    void lockCustomer(TenantScope scope, UUID customerId);
    Optional<String> passwordHash(TenantScope scope, UUID customerId);
    void savePassword(TenantScope scope, UUID customerId, String hash);
    void revokeSessions(TenantScope scope, UUID customerId);
}
