package com.rpb.reservation.publicbooking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.customerauth.application.port.out.CustomerEmailSettingsRepositoryPort;
import com.rpb.reservation.customerauth.application.port.out.CustomerOAuthSettingsRepositoryPort;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingSettingsRepositoryPort;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import com.rpb.reservation.reservation.application.ReservationCreateResult;
import com.rpb.reservation.reservation.application.ReservationMealPeriod;
import com.rpb.reservation.reservation.application.command.CreateReservationCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationMealPeriodRepositoryPort;
import com.rpb.reservation.reservation.application.service.ReservationCreateApplicationService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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

    @Test
    void tenantPublicBookingEntryResolvesOnlyWhenExactlyOneEnabledStoreExists() {
        FakeStoreRepository storeRepository = new FakeStoreRepository();
        PublicBookingEntryApplicationService service = new PublicBookingEntryApplicationService(storeRepository);

        PublicBookingEntryResult result = service.resolveTenantEntry("20000000");

        assertThat(result.success()).isTrue();
        assertThat(result.store()).isEqualTo(storeRepository.profile);
        assertThat(result.error()).isNull();
    }

    @Test
    void tenantPublicBookingEntryRejectsMultipleEnabledStoresWithoutGuessing() {
        FakeStoreRepository storeRepository = new FakeStoreRepository();
        storeRepository.tenantEntryProfiles = List.of(storeRepository.profile, storeRepository.secondProfile);
        PublicBookingEntryApplicationService service = new PublicBookingEntryApplicationService(storeRepository);

        PublicBookingEntryResult result = service.resolveTenantEntry("20000000");

        assertThat(result.success()).isFalse();
        assertThat(result.store()).isNull();
        assertThat(result.error()).isEqualTo(PublicBookingEntryError.MULTIPLE_ENABLED_STORES);
    }

    @Test
    void publicBookingPassesOptionalCustomerProfileToReservationCreate() {
        FakeSettingsRepository settings = new FakeSettingsRepository();
        settings.settings = Optional.of(new PublicBookingSettings(
            true,
            false,
            PublicBookingSettings.MODE_PERCENTAGE,
            100,
            null,
            null,
            0,
            30
        ));
        ReservationCreateApplicationService reservationService = Mockito.mock(ReservationCreateApplicationService.class);
        when(reservationService.createReservation(any())).thenReturn(reservationSuccess());
        PublicBookingApplicationService service = new PublicBookingApplicationService(
            new FakeStoreRepository(),
            settings,
            noOAuthSettings(),
            noEmailSettings(),
            null,
            reservationService,
            new FakeMealPeriodRepository(),
            Clock.fixed(Instant.parse("2026-06-30T00:00:00Z"), ZoneOffset.UTC)
        );

        PublicBookingCreateResult result = service.createBooking(new PublicBookingCreateCommand(
            SCOPE.storeId().value(),
            3,
            Instant.parse("2026-07-01T11:00:00Z"),
            WEDNESDAY,
            "Public Guest",
            "先生",
            "public-guest@example.test",
            "+6591234567",
            "Window seat",
            "idem-public-profile",
            null
        ));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<CreateReservationCommand> commandCaptor = ArgumentCaptor.forClass(CreateReservationCommand.class);
        verify(reservationService).createReservation(commandCaptor.capture());
        CreateReservationCommand command = commandCaptor.getValue();
        assertThat(command.customerName()).isEqualTo("Public Guest");
        assertThat(command.customerNickname()).isEqualTo("先生");
        assertThat(command.customerEmail()).isEqualTo("public-guest@example.test");
        assertThat(command.phoneE164()).isEqualTo("+6591234567");
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

    private static ReservationCreateResult reservationSuccess() {
        return ReservationCreateResult.success(
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            "R-20260701-0001",
            3,
            WEDNESDAY,
            Instant.parse("2026-07-01T11:00:00Z"),
            Instant.parse("2026-07-01T12:30:00Z"),
            Instant.parse("2026-07-01T11:15:00Z"),
            "confirmed",
            "completed",
            List.of(),
            List.of(),
            UUID.fromString("60000000-0000-0000-0000-000000000001")
        );
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
        private final PublicBookingStoreProfile secondProfile = new PublicBookingStoreProfile(
            new StoreScope(
                SCOPE.tenantId(),
                UUID.fromString("20000000-0000-0000-0000-000000000984")
            ),
            "Second Store",
            "UTC",
            null,
            null,
            null,
            null,
            null
        );
        private List<PublicBookingStoreProfile> tenantEntryProfiles = List.of(profile);

        @Override
        public Optional<PublicBookingStoreProfile> findActiveStoreProfileByStoreId(UUID storeId) {
            return SCOPE.storeId().value().equals(storeId) ? Optional.of(profile) : Optional.empty();
        }

        @Override
        public Optional<PublicBookingStoreProfile> findActiveStoreProfile(StoreScope scope) {
            return SCOPE.equals(scope) ? Optional.of(profile) : Optional.empty();
        }

        @Override
        public List<PublicBookingStoreProfile> findEnabledPublicBookingStoreProfilesByTenantCode(String tenantCode) {
            return "20000000".equals(tenantCode) ? tenantEntryProfiles : List.of();
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
