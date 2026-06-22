package com.rpb.reservation.seating.state;

import com.rpb.reservation.common.state.StateMachine;
import com.rpb.reservation.common.state.TransitionResult;
import com.rpb.reservation.seating.status.SeatingStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralized Seating transition skeleton. Source and resource XOR validation
 * stay in validators.
 */
public final class SeatingStateMachine implements StateMachine<SeatingStatus> {

    private static final Map<SeatingStatus, Set<SeatingStatus>> ALLOWED = new EnumMap<>(SeatingStatus.class);

    static {
        ALLOWED.put(SeatingStatus.PLANNED, EnumSet.of(SeatingStatus.LOCKED, SeatingStatus.CANCELLED));
        ALLOWED.put(SeatingStatus.LOCKED, EnumSet.of(SeatingStatus.OCCUPIED, SeatingStatus.CANCELLED));
        ALLOWED.put(SeatingStatus.OCCUPIED, EnumSet.of(SeatingStatus.COMPLETED));
        ALLOWED.put(SeatingStatus.COMPLETED, EnumSet.of(SeatingStatus.CLEANING_TRIGGERED));
    }

    @Override
    public boolean canTransition(SeatingStatus from, SeatingStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    @Override
    public TransitionResult<SeatingStatus> validateTransition(SeatingStatus from, SeatingStatus to) {
        if (canTransition(from, to)) {
            return TransitionResult.accepted(from, to);
        }
        return TransitionResult.rejected(from, to, "illegal_state_transition");
    }
}
