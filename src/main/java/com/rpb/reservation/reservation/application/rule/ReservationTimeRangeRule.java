package com.rpb.reservation.reservation.application.rule;

import com.rpb.reservation.reservation.application.ReservationCreateError;
import java.time.Instant;

public final class ReservationTimeRangeRule {

    public ReservationCreateError validate(Integer partySize, Instant reservedStartAt, Instant reservedEndAt, Instant now) {
        if (partySize == null || partySize <= 0) {
            return ReservationCreateError.INVALID_PARTY_SIZE;
        }
        if (reservedStartAt == null || reservedEndAt == null || !reservedEndAt.isAfter(reservedStartAt)) {
            return ReservationCreateError.INVALID_TIME_RANGE;
        }
        if (!reservedStartAt.isAfter(now)) {
            return ReservationCreateError.RESERVATION_START_IN_PAST;
        }
        return null;
    }
}
