package com.rpb.reservation.reservation.application.service;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.reservation.application.ReservationMealPeriod;
import com.rpb.reservation.reservation.application.ReservationTimeSlot;
import com.rpb.reservation.reservation.application.port.out.ReservationMealPeriodRepositoryPort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ReservationMealPeriodScheduleService {
    private final ReservationMealPeriodRepositoryPort repository;

    public ReservationMealPeriodScheduleService(ReservationMealPeriodRepositoryPort repository) {
        this.repository = Objects.requireNonNull(repository, "meal_period_repository_required");
    }

    public List<ReservationTimeSlot> listSlots(StoreScope scope, String timezone, LocalDate businessDate, Instant now) {
        ZoneId zoneId = zoneId(timezone);
        Instant reference = now == null ? Instant.now() : now;
        return effectivePeriods(scope).stream()
            .filter(ReservationMealPeriod::active)
            .sorted(Comparator.comparingInt(ReservationMealPeriod::sortOrder).thenComparing(ReservationMealPeriod::periodKey))
            .flatMap(period -> slotsFor(period, businessDate, zoneId, reference).stream())
            .toList();
    }

    public boolean isSelectableSlot(
        StoreScope scope,
        String timezone,
        LocalDate businessDate,
        Instant reservedStartAt,
        Instant now
    ) {
        if (businessDate == null || reservedStartAt == null) {
            return false;
        }
        return listSlots(scope, timezone, businessDate, now).stream()
            .anyMatch(slot -> slot.selectable() && slot.startAt().equals(reservedStartAt));
    }

    private List<ReservationMealPeriod> effectivePeriods(StoreScope scope) {
        boolean usePlatformSeed = repository.findUsePlatformSeed(scope).orElse(true);
        if (usePlatformSeed) {
            return repository.findPlatformSeedPeriods();
        }
        return repository.findStorePeriods(scope);
    }

    private static List<ReservationTimeSlot> slotsFor(
        ReservationMealPeriod period,
        LocalDate businessDate,
        ZoneId zoneId,
        Instant now
    ) {
        if (businessDate == null) {
            return List.of();
        }
        LocalDateTime start = LocalDateTime.of(businessDate, period.startLocalTime());
        LocalDate endDate = period.crossesNextDay() ? businessDate.plusDays(1) : businessDate;
        LocalDateTime end = LocalDateTime.of(endDate, period.endLocalTime());
        if (end.isBefore(start)) {
            return List.of();
        }

        java.util.ArrayList<ReservationTimeSlot> slots = new java.util.ArrayList<>();
        LocalDateTime cursor = start;
        while (!cursor.isAfter(end)) {
            Instant startAt = cursor.atZone(zoneId).toInstant();
            boolean nextDay = cursor.toLocalDate().isAfter(businessDate);
            slots.add(new ReservationTimeSlot(
                period.id(),
                period.periodKey(),
                period.displayName(),
                businessDate,
                cursor.toLocalTime(),
                startAt,
                nextDay,
                startAt.isAfter(now)
            ));
            cursor = cursor.plusMinutes(period.slotIntervalMinutes());
        }
        return slots;
    }

    private static ZoneId zoneId(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            return ZoneOffset.UTC;
        }
    }
}
