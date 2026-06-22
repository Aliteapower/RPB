package com.rpb.reservation.common.time;

import java.time.Instant;
import java.util.Objects;

/**
 * UTC instant range used by reservation and availability decisions.
 */
public record TimeRange(Instant start, Instant end) {

    public TimeRange {
        Objects.requireNonNull(start, "time_range_start_required");
        Objects.requireNonNull(end, "time_range_end_required");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("time_range_end_must_be_after_start");
        }
    }
}
