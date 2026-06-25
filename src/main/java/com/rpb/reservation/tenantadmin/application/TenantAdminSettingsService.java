package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.persistence.TenantAdminSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAdminSettingsService {
    private final TenantAdminSettingsRepository repository;

    public TenantAdminSettingsService(TenantAdminSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public TenantAdminSettings getSettings(StoreScope scope) {
        return repository.find(scope)
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID));
    }

    @Transactional
    public TenantAdminSettings updateSettings(StoreScope scope, TenantAdminSettingsCommand command) {
        NormalizedSettingsInput input = normalize(command);
        repository.updateStore(
            scope,
            input.storeName(),
            input.timezone(),
            input.locale(),
            input.dateFormat(),
            input.timeFormat(),
            input.currency()
        );
        repository.upsertCurrentPolicy(
            scope,
            input.reservationHoldMinutes(),
            input.queueCallHoldMinutes(),
            input.expectedDiningMinutes()
        );
        return getSettings(scope);
    }

    private static NormalizedSettingsInput normalize(TenantAdminSettingsCommand command) {
        if (command == null) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return new NormalizedSettingsInput(
            requiredText(command.storeName()),
            requiredText(command.timezone()),
            requiredText(command.locale()),
            requiredText(command.dateFormat()),
            requiredText(command.timeFormat()),
            requiredText(command.currency()),
            positive(command.reservationHoldMinutes()),
            positive(command.queueCallHoldMinutes()),
            positive(command.expectedDiningMinutes())
        );
    }

    private static int positive(Integer value) {
        if (value == null || value <= 0) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return value;
    }

    private static String requiredText(String value) {
        if (value == null || value.isBlank()) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return value.trim();
    }

    private record NormalizedSettingsInput(
        String storeName,
        String timezone,
        String locale,
        String dateFormat,
        String timeFormat,
        String currency,
        int reservationHoldMinutes,
        int queueCallHoldMinutes,
        int expectedDiningMinutes
    ) {
    }
}
