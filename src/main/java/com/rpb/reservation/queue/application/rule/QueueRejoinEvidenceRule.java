package com.rpb.reservation.queue.application.rule;

import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.audit.domain.StateTransitionLog;
import com.rpb.reservation.queue.application.QueueRejoinError;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import java.util.List;

public class QueueRejoinEvidenceRule {

    private static final String TARGET_QUEUE_TICKET = "queue_ticket";
    private static final String EVENT_QUEUE_TICKET_SKIPPED = "queue_ticket.skipped";
    private static final String EVENT_QUEUE_TICKET_REJOINED = "queue_ticket.rejoined";
    private static final String TRANSITION_QUEUE_TICKET_SKIP = "queue_ticket.skip";
    private static final String TRANSITION_QUEUE_TICKET_REJOIN = "queue_ticket.rejoin";
    private static final String OPERATION_SKIP = "queue.skip";
    private static final String OPERATION_REJOIN = "queue.rejoin";

    public QueueRejoinError validateSkippedSourceEvidence(
        QueueTicket queueTicket,
        List<BusinessEvent> businessEvents,
        List<StateTransitionLog> transitions,
        List<AuditLog> auditLogs
    ) {
        if (queueTicket.status() != QueueTicketStatus.SKIPPED || queueTicket.skippedAt() == null) {
            return QueueRejoinError.QUEUE_REJOIN_EVIDENCE_INCOMPLETE;
        }
        if (!hasSkipEvent(queueTicket, businessEvents)) {
            return QueueRejoinError.QUEUE_REJOIN_EVIDENCE_INCOMPLETE;
        }
        if (!hasSkipTransition(queueTicket, transitions)) {
            return QueueRejoinError.QUEUE_REJOIN_EVIDENCE_INCOMPLETE;
        }
        if (!hasSkipAudit(queueTicket, auditLogs)) {
            return QueueRejoinError.QUEUE_REJOIN_EVIDENCE_INCOMPLETE;
        }
        return null;
    }

    public QueueRejoinError validateAlreadyRejoinedEvidence(
        QueueTicket queueTicket,
        List<BusinessEvent> businessEvents,
        List<StateTransitionLog> transitions,
        List<AuditLog> auditLogs
    ) {
        if (queueTicket.status() != QueueTicketStatus.WAITING || queueTicket.rejoinedAt() == null || queueTicket.queuePosition() == null) {
            return QueueRejoinError.QUEUE_REJOIN_EVIDENCE_INCOMPLETE;
        }
        if (!hasRejoinEvent(queueTicket, businessEvents)) {
            return QueueRejoinError.QUEUE_REJOIN_EVIDENCE_INCOMPLETE;
        }
        if (!hasRejoinTransition(queueTicket, transitions)) {
            return QueueRejoinError.QUEUE_REJOIN_EVIDENCE_INCOMPLETE;
        }
        if (!hasRejoinAudit(queueTicket, auditLogs)) {
            return QueueRejoinError.QUEUE_REJOIN_EVIDENCE_INCOMPLETE;
        }
        return null;
    }

    private static boolean hasSkipEvent(QueueTicket queueTicket, List<BusinessEvent> events) {
        return events.stream()
            .anyMatch(event -> EVENT_QUEUE_TICKET_SKIPPED.equals(event.eventType())
                && TARGET_QUEUE_TICKET.equals(event.targetType())
                && queueTicket.id().value().equals(event.targetId()));
    }

    private static boolean hasSkipTransition(QueueTicket queueTicket, List<StateTransitionLog> transitions) {
        return transitions.stream()
            .anyMatch(transition -> TARGET_QUEUE_TICKET.equals(transition.targetType())
                && queueTicket.id().value().equals(transition.targetId())
                && QueueTicketStatus.CALLED.code().equals(transition.fromStatus())
                && QueueTicketStatus.SKIPPED.code().equals(transition.toStatus())
                && TRANSITION_QUEUE_TICKET_SKIP.equals(transition.transitionCode()));
    }

    private static boolean hasSkipAudit(QueueTicket queueTicket, List<AuditLog> auditLogs) {
        return auditLogs.stream()
            .anyMatch(auditLog -> OPERATION_SKIP.equals(auditLog.operationCode())
                && TARGET_QUEUE_TICKET.equals(auditLog.targetType())
                && queueTicket.id().value().equals(auditLog.targetId()));
    }

    private static boolean hasRejoinEvent(QueueTicket queueTicket, List<BusinessEvent> events) {
        return events.stream()
            .anyMatch(event -> EVENT_QUEUE_TICKET_REJOINED.equals(event.eventType())
                && TARGET_QUEUE_TICKET.equals(event.targetType())
                && queueTicket.id().value().equals(event.targetId()));
    }

    private static boolean hasRejoinTransition(QueueTicket queueTicket, List<StateTransitionLog> transitions) {
        return transitions.stream()
            .anyMatch(transition -> TARGET_QUEUE_TICKET.equals(transition.targetType())
                && queueTicket.id().value().equals(transition.targetId())
                && QueueTicketStatus.SKIPPED.code().equals(transition.fromStatus())
                && QueueTicketStatus.WAITING.code().equals(transition.toStatus())
                && TRANSITION_QUEUE_TICKET_REJOIN.equals(transition.transitionCode()));
    }

    private static boolean hasRejoinAudit(QueueTicket queueTicket, List<AuditLog> auditLogs) {
        return auditLogs.stream()
            .anyMatch(auditLog -> OPERATION_REJOIN.equals(auditLog.operationCode())
                && TARGET_QUEUE_TICKET.equals(auditLog.targetType())
                && queueTicket.id().value().equals(auditLog.targetId()));
    }
}
