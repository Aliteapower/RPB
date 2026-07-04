package com.rpb.reservation.customerauth.application.port.out;

import com.rpb.reservation.customerauth.application.CustomerOAuthProviderSettings;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOAuthSettingsRepositoryPort {
    Optional<CustomerOAuthProviderSettings> findProviderSettings(UUID tenantId, UUID storeId, String provider);
}
