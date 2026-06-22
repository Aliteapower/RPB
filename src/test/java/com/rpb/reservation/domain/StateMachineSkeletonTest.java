package com.rpb.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.cleaning.state.CleaningStateMachine;
import com.rpb.reservation.cleaning.status.CleaningStatus;
import com.rpb.reservation.common.state.TransitionResult;
import com.rpb.reservation.queue.state.QueueTicketStateMachine;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.reservation.state.ReservationStateMachine;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.table.state.DiningTableStateMachine;
import com.rpb.reservation.table.status.DiningTableStatus;
import org.junit.jupiter.api.Test;

class StateMachineSkeletonTest {

    @Test
    void reservationTransitionsAreCentralized() {
        ReservationStateMachine machine = new ReservationStateMachine();

        assertThat(machine.canTransition(ReservationStatus.DRAFT, ReservationStatus.CONFIRMED)).isTrue();
        assertThat(machine.canTransition(ReservationStatus.CONFIRMED, ReservationStatus.SEATED)).isFalse();

        TransitionResult<ReservationStatus> decision =
            machine.validateTransition(ReservationStatus.CONFIRMED, ReservationStatus.SEATED);

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.violationCode()).isEqualTo("illegal_state_transition");
    }

    @Test
    void queueRejoinKeepsStatePathExplicit() {
        QueueTicketStateMachine machine = new QueueTicketStateMachine();

        assertThat(machine.canTransition(QueueTicketStatus.CALLED, QueueTicketStatus.SKIPPED)).isTrue();
        assertThat(machine.canTransition(QueueTicketStatus.CALLED, QueueTicketStatus.REJOINED)).isFalse();
    }

    @Test
    void tableCannotJumpFromOccupiedToAvailable() {
        DiningTableStateMachine machine = new DiningTableStateMachine();

        assertThat(machine.canTransition(DiningTableStatus.OCCUPIED, DiningTableStatus.CLEANING)).isTrue();
        assertThat(machine.canTransition(DiningTableStatus.OCCUPIED, DiningTableStatus.AVAILABLE)).isFalse();
    }

    @Test
    void cleaningMustCompleteBeforeRelease() {
        CleaningStateMachine machine = new CleaningStateMachine();

        assertThat(machine.canTransition(CleaningStatus.PENDING, CleaningStatus.RELEASED)).isFalse();
        assertThat(machine.canTransition(CleaningStatus.CLEANING, CleaningStatus.COMPLETED)).isTrue();
    }
}
