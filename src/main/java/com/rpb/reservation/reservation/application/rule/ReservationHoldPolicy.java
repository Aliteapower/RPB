package com.rpb.reservation.reservation.application.rule;

import com.rpb.reservation.store.domain.StorePolicy;
import java.time.Instant;
import java.util.Optional;

public final class ReservationHoldPolicy {

    public static final int DEFAULT_HOLD_MINUTES = 15;
    public static final int DEFAULT_EXPECTED_DINING_MINUTES = 90;

    public Instant holdUntilAt(Instant reservedStartAt, Optional<StorePolicy> policy) {
        return reservedStartAt.plusSeconds((long) reservationHoldMinutes(policy) * 60L);
    }

    public Instant deriveReservedEndAt(Instant reservedStartAt, Optional<StorePolicy> policy) {
        return reservedStartAt.plusSeconds((long) expectedDiningMinutes(policy) * 60L);
    }

    public int reservationHoldMinutes(Optional<StorePolicy> policy) {
        return policy.map(StorePolicy::reservationHoldMinutes).orElse(DEFAULT_HOLD_MINUTES);
    }

    public int expectedDiningMinutes(Optional<StorePolicy> policy) {
        return policy.map(StorePolicy::expectedDiningMinutes).orElse(DEFAULT_EXPECTED_DINING_MINUTES);
    }
}
