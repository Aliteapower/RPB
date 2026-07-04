package com.rpb.reservation.publicbooking.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.customerauth.application.port.out.CustomerEmailSettingsRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerOAuthSettingsRepositoryPort;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingSettingsRepositoryPort;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import com.rpb.reservation.reservation.application.ReservationMealPeriod;
import com.rpb.reservation.reservation.application.port.out.ReservationMealPeriodRepositoryPort;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicBookingApplicationServiceTest {

    private static final StoreScope SCOPE = new StoreScope(
        new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000983")),
        UUID.fromString("20000000-0000-0000-0000-000000000983")
    );
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 1);

    @Test
    void publicContextAppliesWeeklyAvailabilityRulesToCustomerSlots() {
        FakeSettingsRepository settings = new FakeSettingsRepository();
        settings.settings = Optional.of(PublicBookingSettings.enabledPercentage(100));
        settings.availabilityRules = List.of(weeklyRule("dinner", PublicBookingSettings.MODE_CLOSED));
        PublicBookingApplicationService service = new PublicBookingApplicationService(
            new FakeStoreRepository(),
            settings,
            noOAuthSettings(),
            noEmailSettings(),
            null,
            null,
            new FakeMealPeriodRepository(),
            Clock.fixed(Instant.parse("2026-06-30T00:00:00Z"), ZoneOffset.UTC)
        );

        PublicBookingContext context = service.getContext(SCOPE.storeId().value(), WEDNESDAY.toString()).orElseThrow();

        assertThat(context.timeSlots())
            .filteredOn(slot -> "dinner".equals(slot.periodKey()))
            .isNotEmpty()
            .allSatisfy(slot -> assertThat(slot.selectable()).isFalse());
        assertThat(context.timeSlots())
            .filteredOn(slot -> "lunch".equals(slot.periodKey()))
            .isNotEmpty()
            .allSatisfy(slot -> assertThat(slot.selectable()).isTrue());
    }

    private static PublicBookingAvailabilityRule weeklyRule(String periodKey, String quotaMode) {
        return new PublicBookingAvailabilityRule(
            null,
            PublicBookingAvailabilityRule.TYPE_WEEKLY,
            null,
            WEDNESDAY.getDayOfWeek().getValue(),
            periodKey,
            quotaMode,
            null,
            null,
            null
        );
    }

    private static CustomerOAuthSettingsRepositoryPort noOAuthSettings() {
        return (tenantId, storeId, provider) -> Optional.empty();
    }

    private static CustomerEmailSettingsRepositoryPort noEmailSettings() {
        return (tenantId, storeId) -> Optional.empty();
    }

    private static final class FakeStoreRepository implements PublicBookingStoreRepositoryPort {
        private final PublicBookingStoreProfile profile = new PublicBookingStoreProfile(
            SCOPE,
            "Test Store",
            "UTC",
            null,
            null,
            null,
            null,
            null
        );

        @Override
        public Optional<PublicBookingStoreProfile> findActiveStoreProfileByStoreId(UUID storeId) {
            return SCOPE.storeId().value().equals(storeId) ? Optional.of(profile) : Optional.empty();
        }

        @Override
        public Optional<PublicBookingStoreProfile> findActiveStoreProfile(StoreScope scope) {
            return SCOPE.equals(scope) ? Optional.of(profile) : Optional.empty();
        }
    }

    private static final class FakeSettingsRepository implements PublicBookingSettingsRepositoryPort {
        Optional<PublicBookingSettings> settings = Optional.empty();
        List<PublicBookingAvailabilityRule> availabilityRules = List.of();

        @Override
        public Optional<PublicBookingSettings> findSettings(StoreScope scope) {
            return settings;
        }

        @Override
        public List<PublicBookingAvailabilityRule> findAvailabilityRules(StoreScope scope) {
            return availabilityRules;
        }

        @Override
        public Optional<PublicBookingQuotaOverride> findQuotaOverride(
            StoreScope scope,
            BusinessDate businessDate,
            String periodKey
        ) {
            return Optional.empty();
        }
    }

    private static final class FakeMealPeriodRepository implements ReservationMealPeriodRepositoryPort {
        @Override
        public Optional<Boolean> findUsePlatformSeed(StoreScope scope) {
            return Optional.of(false);
        }

        @Override
        public List<ReservationMealPeriod> findPlatformSeedPeriods() {
            return List.of();
        }

        @Override
        public List<ReservationMealPeriod> findStorePeriods(StoreScope scope) {
            return List.of(
                new ReservationMealPeriod(
                    UUID.fromString("9d81f2ab-f8de-4b8a-bc77-58bb7b026101"),
                    "lunch",
                    "Lunch",
                    LocalTime.of(11, 0),
                    LocalTime.of(12, 0),
                    false,
                    30,
                    "active",
                    10,
                    0
                ),
                new ReservationMealPeriod(
                    UUID.fromString("9d81f2ab-f8de-4b8a-bc77-58bb7b026102"),
                    "dinner",
                    "Dinner",
                    LocalTime.of(18, 0),
                    LocalTime.of(19, 0),
                    false,
                    30,
                    "active",
                    20,
                    0
                )
            );
        }
    }
}
