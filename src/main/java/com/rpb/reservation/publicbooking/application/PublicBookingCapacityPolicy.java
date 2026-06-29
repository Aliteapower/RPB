package com.rpb.reservation.publicbooking.application;

import com.rpb.reservation.common.value.OperationSource;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingSettingsRepositoryPort;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingTableCapacityRepositoryPort;
import com.rpb.reservation.reservation.application.ReservationCapacityDecision;
import com.rpb.reservation.reservation.application.ReservationCapacityQuery;
import com.rpb.reservation.reservation.application.port.out.ReservationCapacityPolicyPort;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PublicBookingCapacityPolicy implements ReservationCapacityPolicyPort {

    private final PublicBookingSettingsRepositoryPort settingsRepository;
    private final PublicBookingTableCapacityRepositoryPort tableCapacityRepository;
    private final ReservationCapacityPolicyPort staffFallbackPolicy;

    @Autowired
    public PublicBookingCapacityPolicy(
        PublicBookingSettingsRepositoryPort settingsRepository,
        PublicBookingTableCapacityRepositoryPort tableCapacityRepository
    ) {
        this(settingsRepository, tableCapacityRepository, 50);
    }

    public PublicBookingCapacityPolicy(
        PublicBookingSettingsRepositoryPort settingsRepository,
        PublicBookingTableCapacityRepositoryPort tableCapacityRepository,
        int fallbackCapacityLimit
    ) {
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settings_repository_required");
        this.tableCapacityRepository = Objects.requireNonNull(tableCapacityRepository, "table_capacity_repository_required");
        this.staffFallbackPolicy = ReservationCapacityPolicyPort.fixed(fallbackCapacityLimit);
    }

    @Override
    public ReservationCapacityDecision evaluate(ReservationCapacityQuery query) {
        if (!OperationSource.PUBLIC_BOOKING.equals(query.source())) {
            return staffFallbackPolicy.evaluate(query);
        }

        PublicBookingSettings settings = settingsRepository.findSettings(query.scope())
            .orElse(PublicBookingSettings.disabled());
        if (!settings.enabled()) {
            return ReservationCapacityDecision.reject(0, query.currentUsage(), "public_booking_disabled");
        }

        PublicBookingQuotaPlan quotaPlan = settingsRepository
            .findQuotaOverride(query.scope(), query.businessDate(), query.periodKey())
            .map(PublicBookingQuotaPlan::fromOverride)
            .orElseGet(() -> PublicBookingQuotaPlan.fromSettings(settings));
        if (PublicBookingSettings.MODE_CLOSED.equals(quotaPlan.mode())) {
            return ReservationCapacityDecision.reject(0, query.currentUsage(), "public_booking_closed");
        }

        int capacityLimit = capacityLimit(quotaPlan, tableCapacityRepository.findActiveTableCapacityMaxValues(query.scope()));
        if (capacityLimit > 0 && query.currentUsage() + query.partySize().value() <= capacityLimit) {
            return ReservationCapacityDecision.accept(capacityLimit, query.currentUsage());
        }
        return ReservationCapacityDecision.reject(capacityLimit, query.currentUsage(), "public_booking_capacity_insufficient");
    }

    private static int capacityLimit(PublicBookingQuotaPlan quotaPlan, List<Integer> tableCapacities) {
        return switch (quotaPlan.mode()) {
            case PublicBookingSettings.MODE_TABLE_COUNT -> tableCountLimit(tableCapacities, quotaPlan.tableCount());
            case PublicBookingSettings.MODE_GUEST_COUNT -> positiveOrZero(quotaPlan.guestCount());
            case PublicBookingSettings.MODE_PERCENTAGE -> percentageLimit(tableCapacities, quotaPlan.quotaPercent());
            default -> 0;
        };
    }

    private static int percentageLimit(List<Integer> tableCapacities, Integer quotaPercent) {
        int percent = positiveOrZero(quotaPercent);
        int totalCapacity = tableCapacities.stream()
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .filter(value -> value > 0)
            .sum();
        return (int) Math.ceil(totalCapacity * (percent / 100.0d));
    }

    private static int tableCountLimit(List<Integer> tableCapacities, Integer tableCount) {
        int count = positiveOrZero(tableCount);
        if (count == 0) {
            return 0;
        }
        return tableCapacities.stream()
            .filter(Objects::nonNull)
            .filter(value -> value > 0)
            .sorted(Comparator.reverseOrder())
            .limit(count)
            .mapToInt(Integer::intValue)
            .sum();
    }

    private static int positiveOrZero(Integer value) {
        return value == null || value <= 0 ? 0 : value;
    }

    private record PublicBookingQuotaPlan(
        String mode,
        Integer quotaPercent,
        Integer tableCount,
        Integer guestCount
    ) {
        private static PublicBookingQuotaPlan fromSettings(PublicBookingSettings settings) {
            return new PublicBookingQuotaPlan(
                settings.defaultQuotaMode(),
                settings.defaultQuotaPercent(),
                settings.defaultTableCount(),
                settings.defaultGuestCount()
            );
        }

        private static PublicBookingQuotaPlan fromOverride(PublicBookingQuotaOverride override) {
            return new PublicBookingQuotaPlan(
                override.quotaMode(),
                override.quotaPercent(),
                override.tableCount(),
                override.guestCount()
            );
        }
    }
}
