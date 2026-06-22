package com.rpb.reservation.queue.application.rule;

import com.rpb.reservation.store.domain.StorePolicy;
import java.time.Instant;
import java.util.Optional;

public class QueueCallHoldPolicy {

    public static final int DEFAULT_HOLD_MINUTES = 3;

    public HoldWindow resolve(Optional<StorePolicy> storePolicy, Instant calledAt) {
        int minutes = storePolicy.map(StorePolicy::queueCallHoldMinutes).orElse(DEFAULT_HOLD_MINUTES);
        if (minutes <= 0) {
            throw new IllegalArgumentException("queue_call_hold_minutes_must_be_positive");
        }
        String source = storePolicy.isPresent() ? "store_policy" : "default";
        return new HoldWindow(minutes, calledAt.plusSeconds(minutes * 60L), source);
    }

    public record HoldWindow(int minutes, Instant holdUntilAt, String source) {
    }
}
