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
    static final String OPERATION_OPERATING_ENTITY_CREATE = "platform.tenant.operating_entity.create";
    static final String OPERATION_OPERATING_ENTITY_UPDATE = "platform.tenant.operating_entity.update";
    static final String OPERATION_STORE_CREATE = "platform.tenant.store.create";
    static final String OPERATION_STORE_UPDATE = "platform.tenant.store.update";
    static final String OPERATION_STORE_DELETE = "platform.tenant.store.delete";

    private static final String TARGET_TENANT = "tenant";
    private static final String TARGET_OPERATING_ENTITY = "operating_entity";
    private static final String TARGET_STORE = "store";
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
        append(OPERATION_CREATE, TARGET_TENANT, tenant.id(), operator, metadata);
    }

    public void recordUpdated(PlatformTenant before, PlatformTenant after, PlatformOperator operator, boolean passwordChanged) {
        Map<String, Object> metadata = tenantMetadata(after);
        metadata.put("previous", tenantMetadata(before));
        metadata.put("changedFields", changedFields(before, after, passwordChanged));
        metadata.put("passwordChanged", passwordChanged);
        append(OPERATION_UPDATE, TARGET_TENANT, after.id(), operator, metadata);
    }

    public void recordDeleted(PlatformTenant tenant, PlatformOperator operator) {
        Map<String, Object> metadata = tenantMetadata(tenant);
        metadata.put("deleted", true);
        append(OPERATION_DELETE, TARGET_TENANT, tenant.id(), operator, metadata);
    }

    public void recordRestored(PlatformTenant tenant, PlatformOperator operator) {
        Map<String, Object> metadata = tenantMetadata(tenant);
        metadata.put("deleted", false);
        append(OPERATION_RESTORE, TARGET_TENANT, tenant.id(), operator, metadata);
    }

    public void recordOperatingEntityCreated(PlatformOperatingEntity entity, PlatformOperator operator) {
        append(
            OPERATION_OPERATING_ENTITY_CREATE,
            TARGET_OPERATING_ENTITY,
            entity.id(),
            operator,
            operatingEntityMetadata(entity)
        );
    }

    public void recordOperatingEntityUpdated(
        PlatformOperatingEntity before,
        PlatformOperatingEntity after,
        PlatformOperator operator
    ) {
        Map<String, Object> metadata = operatingEntityMetadata(after);
        metadata.put("previous", operatingEntityMetadata(before));
        metadata.put("changedFields", changedOperatingEntityFields(before, after));
        append(OPERATION_OPERATING_ENTITY_UPDATE, TARGET_OPERATING_ENTITY, after.id(), operator, metadata);
    }

    public void recordStoreCreated(PlatformStore store, PlatformOperator operator) {
        append(OPERATION_STORE_CREATE, TARGET_STORE, store.id(), operator, storeMetadata(store));
    }

    public void recordStoreUpdated(PlatformStore before, PlatformStore after, PlatformOperator operator) {
        Map<String, Object> metadata = storeMetadata(after);
        metadata.put("previous", storeMetadata(before));
        metadata.put("changedFields", changedStoreFields(before, after));
        append(OPERATION_STORE_UPDATE, TARGET_STORE, after.id(), operator, metadata);
    }

    public void recordStoreDeleted(PlatformStore before, PlatformStore after, PlatformOperator operator) {
        Map<String, Object> metadata = storeMetadata(after);
        metadata.put("previous", storeMetadata(before));
        metadata.put("deleted", true);
        append(OPERATION_STORE_DELETE, TARGET_STORE, after.id(), operator, metadata);
    }

    private void append(
        String operationCode,
        String targetType,
        UUID targetId,
        PlatformOperator operator,
        Map<String, Object> metadata
    ) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            operationCode,
            targetType,
            targetId,
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

    private static Map<String, Object> operatingEntityMetadata(PlatformOperatingEntity entity) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("operatingEntityId", entity.id());
        metadata.put("tenantId", entity.tenantId());
        metadata.put("entityCode", entity.entityCode());
        metadata.put("displayName", entity.displayName());
        metadata.put("status", entity.status());
        metadata.put("defaultLocale", entity.defaultLocale());
        metadata.put("contactPhone", entity.contactPhone());
        metadata.put("address", entity.address());
        metadata.put("principalName", entity.principalName());
        metadata.put("deleted", entity.deleted());
        return metadata;
    }

    private static Map<String, Object> storeMetadata(PlatformStore store) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("storeId", store.id());
        metadata.put("tenantId", store.tenantId());
        metadata.put("operatingEntityId", store.operatingEntityId());
        metadata.put("operatingEntityCode", store.operatingEntityCode());
        metadata.put("operatingEntityName", store.operatingEntityName());
        metadata.put("storeCode", store.storeCode());
        metadata.put("storeName", store.storeName());
        metadata.put("status", store.status());
        metadata.put("timezone", store.timezone());
        metadata.put("locale", store.locale());
        metadata.put("dateFormat", store.dateFormat());
        metadata.put("timeFormat", store.timeFormat());
        metadata.put("currency", store.currency());
        metadata.put("deleted", store.deleted());
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

    private static List<String> changedOperatingEntityFields(
        PlatformOperatingEntity before,
        PlatformOperatingEntity after
    ) {
        List<String> fields = new ArrayList<>();
        addIfChanged(fields, "entityCode", before.entityCode(), after.entityCode());
        addIfChanged(fields, "displayName", before.displayName(), after.displayName());
        addIfChanged(fields, "status", before.status(), after.status());
        addIfChanged(fields, "defaultLocale", before.defaultLocale(), after.defaultLocale());
        addIfChanged(fields, "contactPhone", before.contactPhone(), after.contactPhone());
        addIfChanged(fields, "address", before.address(), after.address());
        addIfChanged(fields, "principalName", before.principalName(), after.principalName());
        return fields;
    }

    private static List<String> changedStoreFields(PlatformStore before, PlatformStore after) {
        List<String> fields = new ArrayList<>();
        addIfChanged(fields, "operatingEntityId", before.operatingEntityId(), after.operatingEntityId());
        addIfChanged(fields, "storeCode", before.storeCode(), after.storeCode());
        addIfChanged(fields, "storeName", before.storeName(), after.storeName());
        addIfChanged(fields, "status", before.status(), after.status());
        addIfChanged(fields, "timezone", before.timezone(), after.timezone());
        addIfChanged(fields, "locale", before.locale(), after.locale());
        addIfChanged(fields, "dateFormat", before.dateFormat(), after.dateFormat());
        addIfChanged(fields, "timeFormat", before.timeFormat(), after.timeFormat());
        addIfChanged(fields, "currency", before.currency(), after.currency());
        return fields;
    }

    private static void addIfChanged(List<String> fields, String name, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            fields.add(name);
        }
    }
}
