package com.rpb.reservation.publicbooking.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingSettingsManagementPort;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingSettingsRepositoryPort;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicBookingAdminService {

    private final PublicBookingSettingsRepositoryPort settingsRepository;
    private final PublicBookingSettingsManagementPort managementPort;

    public PublicBookingAdminService(
        PublicBookingSettingsRepositoryPort settingsRepository,
        PublicBookingSettingsManagementPort managementPort
    ) {
        this.settingsRepository = settingsRepository;
        this.managementPort = managementPort;
    }

    @Transactional(readOnly = true)
    public PublicBookingSettings getSettings(StoreScope scope) {
        return settingsRepository.findSettings(scope).orElse(PublicBookingSettings.disabled());
    }

    @Transactional
    public PublicBookingSettings saveSettings(StoreScope scope, PublicBookingSettingsCommand command) {
        PublicBookingSettings current = getSettings(scope);
        PublicBookingSettings next = new PublicBookingSettings(
            optional(command == null ? null : command.enabled(), current.enabled()),
            optional(command == null ? null : command.requireCustomerLogin(), current.requireCustomerLogin()),
            normalizeMode(command == null ? null : command.defaultQuotaMode(), current.defaultQuotaMode()),
            bounded(command == null ? null : command.defaultQuotaPercent(), current.defaultQuotaPercent(), 0, 100),
            nonNegative(command == null ? null : command.defaultTableCount(), current.defaultTableCount()),
            nonNegative(command == null ? null : command.defaultGuestCount(), current.defaultGuestCount()),
            bounded(command == null ? null : command.minLeadMinutes(), current.minLeadMinutes(), 0, 10080),
            bounded(command == null ? null : command.maxAdvanceDays(), current.maxAdvanceDays(), 0, 366)
        );
        return managementPort.saveSettings(scope, next);
    }

    @Transactional
    public PublicBookingQuotaOverride saveQuotaOverride(StoreScope scope, PublicBookingQuotaOverrideCommand command) {
        if (command == null || command.businessDate() == null) {
            throw new IllegalArgumentException("business_date_required");
        }
        PublicBookingQuotaOverride override = new PublicBookingQuotaOverride(
            trimToNull(command.periodKey()),
            normalizeOverrideMode(command.quotaMode()),
            boundedNullable(command.quotaPercent(), 0, 100),
            nonNegative(command.tableCount(), null),
            nonNegative(command.guestCount(), null)
        );
        return managementPort.saveQuotaOverride(scope, command.businessDate(), override);
    }

    @Transactional(readOnly = true)
    public Optional<PublicBookingQuotaOverride> findQuotaOverride(
        StoreScope scope,
        BusinessDate businessDate,
        String periodKey
    ) {
        return settingsRepository.findQuotaOverride(scope, businessDate, periodKey);
    }

    private static boolean optional(Boolean candidate, boolean fallback) {
        return candidate == null ? fallback : candidate;
    }

    private static String normalizeMode(String candidate, String fallback) {
        String normalized = trimToNull(candidate);
        if (
            PublicBookingSettings.MODE_PERCENTAGE.equals(normalized)
                || PublicBookingSettings.MODE_TABLE_COUNT.equals(normalized)
                || PublicBookingSettings.MODE_GUEST_COUNT.equals(normalized)
        ) {
            return normalized;
        }
        return fallback;
    }

    private static String normalizeOverrideMode(String candidate) {
        String normalized = trimToNull(candidate);
        if (
            PublicBookingSettings.MODE_PERCENTAGE.equals(normalized)
                || PublicBookingSettings.MODE_TABLE_COUNT.equals(normalized)
                || PublicBookingSettings.MODE_GUEST_COUNT.equals(normalized)
                || PublicBookingSettings.MODE_CLOSED.equals(normalized)
        ) {
            return normalized;
        }
        throw new IllegalArgumentException("quota_mode_invalid");
    }

    private static Integer bounded(Integer candidate, Integer fallback, int min, int max) {
        int value = candidate == null ? fallback : candidate;
        return Math.max(min, Math.min(max, value));
    }

    private static Integer boundedNullable(Integer candidate, int min, int max) {
        return candidate == null ? null : Math.max(min, Math.min(max, candidate));
    }

    private static Integer nonNegative(Integer candidate, Integer fallback) {
        Integer value = candidate == null ? fallback : candidate;
        return value == null ? null : Math.max(0, value);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
