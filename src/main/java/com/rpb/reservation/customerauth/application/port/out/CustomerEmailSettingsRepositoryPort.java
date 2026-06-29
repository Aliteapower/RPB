package com.rpb.reservation.customerauth.application.port.out;

import com.rpb.reservation.customerauth.application.CustomerEmailSettings;
import java.util.Optional;
import java.util.UUID;

public interface CustomerEmailSettingsRepositoryPort {
    Optional<CustomerEmailSettings> findEmailSettings(UUID tenantId, UUID storeId);
}
