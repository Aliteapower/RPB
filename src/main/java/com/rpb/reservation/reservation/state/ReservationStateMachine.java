package com.rpb.reservation.reservation.state;

import com.rpb.reservation.common.state.StateMachine;
import com.rpb.reservation.common.state.TransitionResult;
import com.rpb.reservation.reservation.status.ReservationStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralized Reservation transition skeleton. AuditLog and
 * StateTransitionLog hooks are intentionally deferred to application workflow.
 */
public final class ReservationStateMachine implements StateMachine<ReservationStatus> {

    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED = new EnumMap<>(ReservationStatus.class);

    static {
        ALLOWED.put(ReservationStatus.DRAFT, EnumSet.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED));
        ALLOWED.put(ReservationStatus.CONFIRMED, EnumSet.of(ReservationStatus.ARRIVED, ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW));
        ALLOWED.put(ReservationStatus.ARRIVED, EnumSet.of(ReservationStatus.SEATED, ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW));
        ALLOWED.put(ReservationStatus.SEATED, EnumSet.of(ReservationStatus.COMPLETED));
    }

    @Override
    public boolean canTransition(ReservationStatus from, ReservationStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    @Override
    public TransitionResult<ReservationStatus> validateTransition(ReservationStatus from, ReservationStatus to) {
        if (canTransition(from, to)) {
            return TransitionResult.accepted(from, to);
        }
        return TransitionResult.rejected(from, to, "illegal_state_transition");
    }
}
