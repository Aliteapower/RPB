package com.rpb.reservation.tenantadmin.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiErrorCode;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class StaffPermissionsAuditService {
    private final AuditLogRepositoryPort audit;
    private final ObjectMapper mapper;
    public StaffPermissionsAuditService(AuditLogRepositoryPort audit, ObjectMapper mapper) { this.audit = audit; this.mapper = mapper; }
    public void changed(StoreScope scope, UUID staffId, UUID actorId, Set<String> before, Set<String> after, long version) {
        try {
            var metadata = mapper.writeValueAsString(Map.of("storeId", scope.storeId().value(), "staffId", staffId,
                "before", before.stream().sorted().toList(), "after", after.stream().sorted().toList(), "version", version));
            audit.append(scope, new AuditLog(UUID.randomUUID(), "tenant_admin.staff.permissions.update", "auth_account",
                staffId, "staff", "tenant_admin", actorId, metadata));
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new TenantAdminApiException(TenantAdminApiErrorCode.PERSISTENCE_ERROR);
        }
    }
}
