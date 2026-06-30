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
        settings.availabilityRules = List.of(dateRule("dinner", PublicBookingSettings.MODE_TABLE_COUNT, null, 2, null));
        FakeTableCapacityRepository tables = new FakeTableCapacityRepository(List.of(2, 4, 6, 8));
        PublicBookingCapacityPolicy policy = new PublicBookingCapacityPolicy(settings, tables, 50);

        ReservationCapacityDecision decision = policy.evaluate(customerQuery(8, 6));

        assertThat(decision.accepted()).isTrue();
        assertThat(decision.capacityLimit()).isEqualTo(14);
    }

    @Test
    void allPeriodDateRuleAppliesToSpecificMealPeriodWhenNoSpecificRuleExists() {
        FakeSettingsRepository settings = new FakeSettingsRepository();
        settings.settings = Optional.of(PublicBookingSettings.enabledPercentage(100));
        settings.availabilityRules = List.of(dateRule(null, PublicBookingSettings.MODE_CLOSED, null, null, null));
        FakeTableCapacityRepository tables = new FakeTableCapacityRepository(List.of(4, 4, 6));
        PublicBookingCapacityPolicy policy = new PublicBookingCapacityPolicy(settings, tables, 50);

        ReservationCapacityDecision decision = policy.evaluate(customerQuery(0, 2));

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("public_booking_closed");
    }

    @Test
    void dateSpecificRuleOverridesDateAllPeriodAndWeeklyRules() {
        FakeSettingsRepository settings = new FakeSettingsRepository();
        settings.settings = Optional.of(PublicBookingSettings.enabledPercentage(10));
        settings.availabilityRules = List.of(
            weeklyRule(null, PublicBookingSettings.MODE_CLOSED, null, null, null),
            dateRule(null, PublicBookingSettings.MODE_CLOSED, null, null, null),
            dateRule("dinner", PublicBookingSettings.MODE_TABLE_COUNT, null, 2, null)
        );
        FakeTableCapacityRepository tables = new FakeTableCapacityRepository(List.of(4, 6, 8, 10));
        PublicBookingCapacityPolicy policy = new PublicBookingCapacityPolicy(settings, tables, 50);

        ReservationCapacityDecision decision = policy.evaluate(customerQuery(12, 5));

        assertThat(decision.accepted()).isTrue();
        assertThat(decision.capacityLimit()).isEqualTo(18);
    }

    @Test
    void weeklyAllPeriodRuleAppliesWhenNoDateRuleExists() {
        FakeSettingsRepository settings = new FakeSettingsRepository();
        settings.settings = Optional.of(PublicBookingSettings.enabledPercentage(100));
        settings.availabilityRules = List.of(weeklyRule(null, PublicBookingSettings.MODE_CLOSED, null, null, null));
        FakeTableCapacityRepository tables = new FakeTableCapacityRepository(List.of(4, 4, 6));
        PublicBookingCapacityPolicy policy = new PublicBookingCapacityPolicy(settings, tables, 50);

        ReservationCapacityDecision decision = policy.evaluate(customerQuery(0, 2));

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("public_booking_closed");
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

    private static PublicBookingAvailabilityRule dateRule(
        String periodKey,
        String quotaMode,
        Integer quotaPercent,
        Integer tableCount,
        Integer guestCount
    ) {
        return new PublicBookingAvailabilityRule(
            null,
            PublicBookingAvailabilityRule.TYPE_DATE_EXCEPTION,
            BUSINESS_DATE.value(),
            null,
            periodKey,
            quotaMode,
            quotaPercent,
            tableCount,
            guestCount
        );
    }

    private static PublicBookingAvailabilityRule weeklyRule(
        String periodKey,
        String quotaMode,
        Integer quotaPercent,
        Integer tableCount,
        Integer guestCount
    ) {
        return new PublicBookingAvailabilityRule(
            null,
            PublicBookingAvailabilityRule.TYPE_WEEKLY,
            null,
            BUSINESS_DATE.value().getDayOfWeek().getValue(),
            periodKey,
            quotaMode,
            quotaPercent,
            tableCount,
            guestCount
        );
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

    private record FakeTableCapacityRepository(List<Integer> capacities)
        implements PublicBookingTableCapacityRepositoryPort {

        @Override
        public List<Integer> findActiveTableCapacityMaxValues(StoreScope scope) {
            return capacities;
        }
    }
}
