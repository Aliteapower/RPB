package com.rpb.reservation.appgate.application;

import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.appgate.persistence.entity.AppGateAuditLogEntity;
import com.rpb.reservation.appgate.persistence.repository.AppGateAuditLogJpaRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppGateDenialAuditService {
    public static final String ACTION = "APP_GATE_DENIED";

    private final AppGateAuditLogJpaRepository auditLogs;

    public AppGateDenialAuditService(AppGateAuditLogJpaRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Transactional
    public void recordDenial(AppGateDecision decision, CurrentActor actor) {
        if (decision == null || decision.allowed() || decision.tenantId() == null || decision.storeId() == null) {
            return;
        }

        // A requested foreign/nonexistent store is not a valid FK in the actor's tenant.
        // Keep the attempted ID in the snapshot and record this as a tenant-level denial.
        UUID auditStoreId = actor != null && decision.tenantId().equals(actor.tenantId())
            && actor.canAccessStore(decision.storeId()) ? decision.storeId() : null;
        auditLogs.save(AppGateAuditLogEntity.of(
            UUID.randomUUID(),
            decision.tenantId(),
            auditStoreId,
            decision.appKey(),
            ACTION,
            actor == null ? null : actor.actorId(),
            actor == null ? null : actor.actorType(),
            null,
            denialSnapshot(decision),
            OffsetDateTime.now()
        ));
    }

    private static String denialSnapshot(AppGateDecision decision) {
        return """
            {"decision":"denied","denyReason":"%s","messageKey":"%s","requiredPermission":"%s","requestedStoreId":"%s"}
            """.formatted(
                decision.denyReason() == null ? "PERMISSION_DENIED" : decision.denyReason().name(),
                escape(decision.messageKey() == null ? "appgate.permission_denied" : decision.messageKey()),
                escape(decision.requiredPermission()),
                decision.storeId()
            ).trim();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
