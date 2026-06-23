package com.rpb.reservation.queue.state;

import com.rpb.reservation.common.state.StateMachine;
import com.rpb.reservation.common.state.TransitionResult;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralized QueueTicket transition skeleton. Queue ordering and call-hold
 * policy checks remain in rule/policy components.
 */
public final class QueueTicketStateMachine implements StateMachine<QueueTicketStatus> {

    private static final Map<QueueTicketStatus, Set<QueueTicketStatus>> ALLOWED = new EnumMap<>(QueueTicketStatus.class);

    static {
        ALLOWED.put(QueueTicketStatus.WAITING, EnumSet.of(QueueTicketStatus.CALLED, QueueTicketStatus.CANCELLED, QueueTicketStatus.EXPIRED));
        ALLOWED.put(QueueTicketStatus.CALLED, EnumSet.of(QueueTicketStatus.SEATED, QueueTicketStatus.SKIPPED, QueueTicketStatus.CANCELLED, QueueTicketStatus.EXPIRED));
        ALLOWED.put(QueueTicketStatus.SKIPPED, EnumSet.of(QueueTicketStatus.WAITING, QueueTicketStatus.REJOINED, QueueTicketStatus.CANCELLED, QueueTicketStatus.EXPIRED));
        ALLOWED.put(QueueTicketStatus.REJOINED, EnumSet.of(QueueTicketStatus.WAITING, QueueTicketStatus.CALLED, QueueTicketStatus.CANCELLED, QueueTicketStatus.EXPIRED));
    }

    @Override
    public boolean canTransition(QueueTicketStatus from, QueueTicketStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    @Override
    public TransitionResult<QueueTicketStatus> validateTransition(QueueTicketStatus from, QueueTicketStatus to) {
        if (canTransition(from, to)) {
            return TransitionResult.accepted(from, to);
        }
        return TransitionResult.rejected(from, to, "illegal_state_transition");
    }
}
