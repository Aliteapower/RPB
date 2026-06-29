package com.rpb.reservation.publicbooking.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.common.value.OperationSource;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingSettingsRepositoryPort;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingTableCapacityRepositoryPort;
import com.rpb.reservation.reservation.application.ReservationCapacityDecision;
import com.rpb.reservation.reservation.application.ReservationCapacityQuery;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicBookingCapacityPolicyTest {

    private static final StoreScope SCOPE = new StoreScope(
        new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000983")),
        UUID.fromString("20000000-0000-0000-0000-000000000983")
    );
    private static final BusinessDate BUSINESS_DATE = new BusinessDate(LocalDate.of(2026, 6, 30));
    private static final TimeRange TIME_RANGE = new TimeRange(
        Instant.parse("2026-06-30T10:00:00Z"),
        Instant.parse("2026-06-30T11:30:00Z")
    );

    @Test
    void publicBookingSourceUsesTwentyPercentOfActiveTableCapacityByDefault() {
        FakeSettingsRepository settings = new FakeSettingsRepository();
        settings.settings = Optional.of(PublicBookingSettings.enabledPercentage(20));
        FakeTableCapacityRepository tables = new FakeTableCapacityRepository(List.of(4, 4, 6, 6, 8));
        PublicBookingCapacityPolicy policy = new PublicBookingCapacityPolicy(settings, tables, 50);

        ReservationCapacityDecision accepted = policy.evaluate(customerQuery(0, 5));
        ReservationCapacityDecision rejected = policy.evaluate(customerQuery(6, 1));

        assertThat(accepted.accepted()).isTrue();
        assertThat(accepted.capacityLimit()).isEqualTo(6);
        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.reasonCode()).isEqualTo("public_booking_capacity_insufficient");
    }

    @Test
    void publicBookingSourceCanUseCustomTableCountOverrideForTheBusinessDateAndPeriod() {
        FakeSettingsRepository settings = new FakeSettingsRepository();
        settings.settings = Optional.of(PublicBookingSettings.enabledPercentage(20));
        settings.override = Optional.of(PublicBookingQuotaOverride.tableCount("dinner", 2));
        FakeTableCapacityRepository tables = new FakeTableCapacityRepository(List.of(2, 4, 6, 8));
        PublicBookingCapacityPolicy policy = new PublicBookingCapacityPolicy(settings, tables, 50);

        ReservationCapacityDecision decision = policy.evaluate(customerQuery(8, 6));

        assertThat(decision.accepted()).isTrue();
        assertThat(decision.capacityLimit()).isEqualTo(14);
    }

    @Test
    void staffSourceUsesFallbackCapacityAndDoesNotConsumePublicQuota() {
        FakeSettingsRepository settings = new FakeSettingsRepository();
        settings.settings = Optional.of(PublicBookingSettings.disabled());
        FakeTableCapacityRepository tables = new FakeTableCapacityRepository(List.of(2));
        PublicBookingCapacityPolicy policy = new PublicBookingCapacityPolicy(settings, tables, 50);

        ReservationCapacityDecision decision = policy.evaluate(staffQuery(49, 1));

        assertThat(decision.accepted()).isTrue();
        assertThat(decision.capacityLimit()).isEqualTo(50);
    }

    private static ReservationCapacityQuery customerQuery(int currentUsage, int partySize) {
        return new ReservationCapacityQuery(
            SCOPE,
            OperationSource.PUBLIC_BOOKING,
            "dinner",
            BUSINESS_DATE,
            TIME_RANGE,
            new PartySize(partySize),
            currentUsage
        );
    }

    private static ReservationCapacityQuery staffQuery(int currentUsage, int partySize) {
        return new ReservationCapacityQuery(
            SCOPE,
            OperationSource.STAFF,
            "dinner",
            BUSINESS_DATE,
            TIME_RANGE,
            new PartySize(partySize),
            currentUsage
        );
    }

    private static final class FakeSettingsRepository implements PublicBookingSettingsRepositoryPort {
        Optional<PublicBookingSettings> settings = Optional.empty();
        Optional<PublicBookingQuotaOverride> override = Optional.empty();

        @Override
        public Optional<PublicBookingSettings> findSettings(StoreScope scope) {
            return settings;
        }

        @Override
        public Optional<PublicBookingQuotaOverride> findQuotaOverride(
            StoreScope scope,
            BusinessDate businessDate,
            String periodKey
        ) {
            return override;
        }
    }

    private record FakeTableCapacityRepository(List<Integer> capacities)
        implements PublicBookingTableCapacityRepositoryPort {

        @Override
        public List<Integer> findActiveTableCapacityMaxValues(StoreScope scope) {
            return capacities;
        }
    }
}
