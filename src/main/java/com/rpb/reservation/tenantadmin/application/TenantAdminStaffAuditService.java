package com.rpb.reservation.tenantadmin.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.rule.DefaultAuditRule;
import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.scope.StoreScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TenantAdminStaffAuditService {
    static final String OPERATION_SELF_UPDATE = "tenant_admin.account.self_update";

    private static final String TARGET_AUTH_ACCOUNT = "auth_account";
    private static final String SOURCE_STAFF = "staff";
    private static final String ACTOR_TYPE_TENANT_ADMIN = "tenant_admin";

    private final AuditLogRepositoryPort auditLogRepository;
    private final ObjectMapper objectMapper;
    private final DefaultAuditRule auditRule = new DefaultAuditRule();

    public TenantAdminStaffAuditService(
        AuditLogRepositoryPort auditLogRepository,
        ObjectMapper objectMapper
    ) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void recordSelfUpdated(
        StoreScope scope,
        TenantAdminStaff before,
        TenantAdminStaff after,
        UUID actorId,
        boolean passwordChanged
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("accountId", after.id());
        metadata.put("username", after.employeeNo());
        metadata.put("accountType", after.accountType());
        metadata.put("changedFields", changedFields(before, after, passwordChanged));
        metadata.put("passwordChanged", passwordChanged);
        append(scope, OPERATION_SELF_UPDATE, after.id(), actorId, metadata);
    }

    private void append(
        StoreScope scope,
        String operationCode,
        UUID targetId,
        UUID actorId,
        Map<String, Object> metadata
    ) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            operationCode,
            TARGET_AUTH_ACCOUNT,
            targetId,
            SOURCE_STAFF,
            ACTOR_TYPE_TENANT_ADMIN,
            actorId,
            serialize(metadata)
        );
        RuleDecision decision = auditRule.evaluate(
            auditLog.operationCode(),
            auditLog.targetType(),
            auditLog.targetId(),
            auditLog.actorType()
        );
        if (!decision.accepted()) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.PERSISTENCE_ERROR);
        }
        try {
            auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.PERSISTENCE_ERROR);
        }
    }

    private String serialize(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.PERSISTENCE_ERROR);
        }
    }

    private static List<String> changedFields(
        TenantAdminStaff before,
        TenantAdminStaff after,
        boolean passwordChanged
    ) {
        List<String> fields = new ArrayList<>();
        addIfChanged(fields, "name", before.name(), after.name());
        addIfChanged(fields, "phone", before.phone(), after.phone());
        addIfChanged(fields, "email", before.email(), after.email());
        if (passwordChanged) {
            fields.add("password");
        }
        return fields;
    }

    private static void addIfChanged(List<String> fields, String name, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            fields.add(name);
        }
    }
}
