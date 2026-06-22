package com.rpb.reservation.queue.application.rule;

import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.audit.domain.StateTransitionLog;
import com.rpb.reservation.queue.application.QueueSkipError;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import java.util.List;

public class QueueSkipEvidenceRule {

    private static final String TARGET_QUEUE_TICKET = "queue_ticket";
    private static final String EVENT_QUEUE_TICKET_SKIPPED = "queue_ticket.skipped";
    private static final String TRANSITION_QUEUE_TICKET_SKIP = "queue_ticket.skip";
    private static final String OPERATION_SKIP = "queue.skip";

    public QueueSkipError validateAlreadySkippedEvidence(
        QueueTicket queueTicket,
        List<BusinessEvent> businessEvents,
        List<StateTransitionLog> transitions,
        List<AuditLog> auditLogs
    ) {
        if (queueTicket.status() != QueueTicketStatus.SKIPPED || queueTicket.skippedAt() == null) {
            return QueueSkipError.QUEUE_SKIP_EVIDENCE_INCOMPLETE;
        }
        if (!hasSkipEvent(queueTicket, businessEvents)) {
            return QueueSkipError.QUEUE_SKIP_EVIDENCE_INCOMPLETE;
        }
        if (!hasSkipTransition(queueTicket, transitions)) {
            return QueueSkipError.QUEUE_SKIP_EVIDENCE_INCOMPLETE;
        }
        if (!hasSkipAudit(queueTicket, auditLogs)) {
            return QueueSkipError.QUEUE_SKIP_EVIDENCE_INCOMPLETE;
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
}
