package com.rpb.reservation.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.reservation.application.port.out.ReservationMealPeriodRepositoryPort;
import com.rpb.reservation.reservation.application.service.ReservationMealPeriodScheduleService;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationMealPeriodScheduleServiceTest {
    private static final StoreScope SCOPE = new StoreScope(
        new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000001")),
        UUID.fromString("20000000-0000-0000-0000-000000000001")
    );

    @Test
    void generatesInclusiveLunchAndCrossDayDinnerSlotsFromPlatformSeed() {
        FakeMealPeriodRepository repository = FakeMealPeriodRepository.usingPlatformSeed();
        ReservationMealPeriodScheduleService service = new ReservationMealPeriodScheduleService(repository);

        List<ReservationTimeSlot> slots = service.listSlots(
            SCOPE,
            "Asia/Singapore",
            LocalDate.of(2026, 6, 20),
            Instant.parse("2026-06-20T02:59:00Z")
        );

        assertThat(slots).filteredOn(slot -> slot.periodKey().equals("lunch")).hasSize(9);
        assertThat(slots).filteredOn(slot -> slot.periodKey().equals("dinner")).hasSize(16);
        assertThat(slots.getFirst().time()).isEqualTo(LocalTime.of(11, 0));
        assertThat(slots.getFirst().startAt()).isEqualTo(Instant.parse("2026-06-20T03:00:00Z"));

        ReservationTimeSlot last = slots.getLast();
        assertThat(last.periodKey()).isEqualTo("dinner");
        assertThat(last.time()).isEqualTo(LocalTime.of(0, 30));
        assertThat(last.startAt()).isEqualTo(Instant.parse("2026-06-20T16:30:00Z"));
        assertThat(last.nextDay()).isTrue();
        assertThat(last.selectable()).isTrue();
    }

    @Test
    void marksPastSlotsUnselectableButKeepsFutureSlotsSelectable() {
        FakeMealPeriodRepository repository = FakeMealPeriodRepository.usingPlatformSeed();
        ReservationMealPeriodScheduleService service = new ReservationMealPeriodScheduleService(repository);

        List<ReservationTimeSlot> slots = service.listSlots(
            SCOPE,
            "Asia/Singapore",
            LocalDate.of(2026, 6, 20),
            Instant.parse("2026-06-20T03:15:00Z")
        );

        assertThat(slots)
            .filteredOn(slot -> slot.periodKey().equals("lunch") && slot.time().equals(LocalTime.of(11, 0)))
            .singleElement()
            .extracting(ReservationTimeSlot::selectable)
            .isEqualTo(false);
        assertThat(slots)
            .filteredOn(slot -> slot.periodKey().equals("lunch") && slot.time().equals(LocalTime.of(11, 30)))
            .singleElement()
            .extracting(ReservationTimeSlot::selectable)
            .isEqualTo(true);
    }

    @Test
    void validatesOnlyExactEffectiveSlots() {
        FakeMealPeriodRepository repository = FakeMealPeriodRepository.usingPlatformSeed();
        ReservationMealPeriodScheduleService service = new ReservationMealPeriodScheduleService(repository);

        assertThat(service.isSelectableSlot(
            SCOPE,
            "Asia/Singapore",
            LocalDate.of(2026, 6, 20),
            Instant.parse("2026-06-20T03:30:00Z"),
            Instant.parse("2026-06-20T02:59:00Z")
        )).isTrue();
        assertThat(service.isSelectableSlot(
            SCOPE,
            "Asia/Singapore",
            LocalDate.of(2026, 6, 20),
            Instant.parse("2026-06-20T04:10:00Z"),
            Instant.parse("2026-06-20T02:59:00Z")
        )).isFalse();
    }

    private static final class FakeMealPeriodRepository implements ReservationMealPeriodRepositoryPort {
        private final List<ReservationMealPeriod> platformPeriods;

        private FakeMealPeriodRepository(List<ReservationMealPeriod> platformPeriods) {
            this.platformPeriods = platformPeriods;
        }

        static FakeMealPeriodRepository usingPlatformSeed() {
            return new FakeMealPeriodRepository(List.of(
                new ReservationMealPeriod(
                    UUID.fromString("9d81f2ab-f8de-4b8a-bc77-58bb7b026001"),
                    "lunch",
                    "午餐",
                    LocalTime.of(11, 0),
                    LocalTime.of(15, 0),
                    false,
                    30,
                    "active",
                    10,
                    0
                ),
                new ReservationMealPeriod(
                    UUID.fromString("9d81f2ab-f8de-4b8a-bc77-58bb7b026002"),
                    "dinner",
                    "晚餐",
                    LocalTime.of(17, 0),
                    LocalTime.of(0, 30),
                    true,
                    30,
                    "active",
                    20,
                    0
                )
            ));
        }

        @Override
        public Optional<Boolean> findUsePlatformSeed(StoreScope scope) {
            return Optional.of(true);
        }

        @Override
        public List<ReservationMealPeriod> findPlatformSeedPeriods() {
            return platformPeriods;
        }

        @Override
        public List<ReservationMealPeriod> findStorePeriods(StoreScope scope) {
            return List.of();
        }
    }
}
