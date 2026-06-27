package com.rpb.reservation.platform.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.rule.DefaultAuditRule;
import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.scope.PlatformScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PlatformTenantAuditService {
    static final String OPERATION_CREATE = "platform.tenant.create";
    static final String OPERATION_UPDATE = "platform.tenant.update";
    static final String OPERATION_DELETE = "platform.tenant.delete";
    static final String OPERATION_RESTORE = "platform.tenant.restore";

    private static final String TARGET_TENANT = "tenant";
    private static final String SOURCE_STAFF = "staff";

    private final AuditLogRepositoryPort auditLogRepository;
    private final ObjectMapper objectMapper;
    private final DefaultAuditRule auditRule = new DefaultAuditRule();

    public PlatformTenantAuditService(
        AuditLogRepositoryPort auditLogRepository,
        ObjectMapper objectMapper
    ) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void recordCreated(PlatformTenant tenant, PlatformOperator operator) {
        Map<String, Object> metadata = tenantMetadata(tenant);
        metadata.put("passwordChanged", false);
        metadata.put("initialPasswordSet", true);
        append(OPERATION_CREATE, tenant.id(), operator, metadata);
    }

    public void recordUpdated(PlatformTenant before, PlatformTenant after, PlatformOperator operator, boolean passwordChanged) {
        Map<String, Object> metadata = tenantMetadata(after);
        metadata.put("previous", tenantMetadata(before));
        metadata.put("changedFields", changedFields(before, after, passwordChanged));
        metadata.put("passwordChanged", passwordChanged);
        append(OPERATION_UPDATE, after.id(), operator, metadata);
    }

    public void recordDeleted(PlatformTenant tenant, PlatformOperator operator) {
        Map<String, Object> metadata = tenantMetadata(tenant);
        metadata.put("deleted", true);
        append(OPERATION_DELETE, tenant.id(), operator, metadata);
    }

    public void recordRestored(PlatformTenant tenant, PlatformOperator operator) {
        Map<String, Object> metadata = tenantMetadata(tenant);
        metadata.put("deleted", false);
        append(OPERATION_RESTORE, tenant.id(), operator, metadata);
    }

    private void append(String operationCode, UUID tenantId, PlatformOperator operator, Map<String, Object> metadata) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            operationCode,
            TARGET_TENANT,
            tenantId,
            SOURCE_STAFF,
            operator.actorType(),
            operator.actorId(),
            serialize(metadata)
        );
        RuleDecision decision = auditRule.evaluate(
            auditLog.operationCode(),
            auditLog.targetType(),
            auditLog.targetId(),
            auditLog.actorType()
        );
        if (!decision.accepted()) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.AUDIT_WRITE_FAILED);
        }
        try {
            auditLogRepository.append(PlatformScope.reservationPlatform(), auditLog);
        } catch (RuntimeException exception) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.AUDIT_WRITE_FAILED);
        }
    }

    private String serialize(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.AUDIT_WRITE_FAILED);
        }
    }

    private static Map<String, Object> tenantMetadata(PlatformTenant tenant) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", tenant.id());
        metadata.put("tenantCode", tenant.tenantCode());
        metadata.put("displayName", tenant.displayName());
        metadata.put("status", tenant.status());
        metadata.put("defaultLocale", tenant.defaultLocale());
        metadata.put("contactPhone", tenant.contactPhone());
        metadata.put("address", tenant.address());
        metadata.put("principalName", tenant.principalName());
        metadata.put("logoMediaAssetId", tenant.logoMediaAssetId());
        metadata.put("deleted", tenant.deleted());
        return metadata;
    }

    private static List<String> changedFields(PlatformTenant before, PlatformTenant after, boolean passwordChanged) {
        List<String> fields = new ArrayList<>();
        addIfChanged(fields, "displayName", before.displayName(), after.displayName());
        addIfChanged(fields, "status", before.status(), after.status());
        addIfChanged(fields, "defaultLocale", before.defaultLocale(), after.defaultLocale());
        addIfChanged(fields, "contactPhone", before.contactPhone(), after.contactPhone());
        addIfChanged(fields, "address", before.address(), after.address());
        addIfChanged(fields, "principalName", before.principalName(), after.principalName());
        addIfChanged(fields, "logoMediaAssetId", before.logoMediaAssetId(), after.logoMediaAssetId());
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
