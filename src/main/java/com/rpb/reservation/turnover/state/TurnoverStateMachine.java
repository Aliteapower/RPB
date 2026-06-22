package com.rpb.reservation.turnover.state;

import com.rpb.reservation.common.state.StateMachine;
import com.rpb.reservation.common.state.TransitionResult;
import com.rpb.reservation.turnover.status.TurnoverStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralized Turnover transition skeleton. Calculation stays outside this
 * state machine.
 */
public final class TurnoverStateMachine implements StateMachine<TurnoverStatus> {

    private static final Map<TurnoverStatus, Set<TurnoverStatus>> ALLOWED = new EnumMap<>(TurnoverStatus.class);

    static {
        ALLOWED.put(TurnoverStatus.PENDING, EnumSet.of(TurnoverStatus.RECORDED));
        ALLOWED.put(TurnoverStatus.RECORDED, EnumSet.of(TurnoverStatus.ARCHIVED));
    }

    @Override
    public boolean canTransition(TurnoverStatus from, TurnoverStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    @Override
    public TransitionResult<TurnoverStatus> validateTransition(TurnoverStatus from, TurnoverStatus to) {
        if (canTransition(from, to)) {
            return TransitionResult.accepted(from, to);
        }
        return TransitionResult.rejected(from, to, "illegal_state_transition");
    }
}
