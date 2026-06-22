package com.rpb.reservation.audit.application.port.out;

import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.common.scope.PlatformScope;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.time.TimeRange;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepositoryPort {

    AuditLog append(StoreScope scope, AuditLog auditLog);

    AuditLog append(TenantScope scope, AuditLog auditLog);

    AuditLog append(PlatformScope scope, AuditLog auditLog);

    List<AuditLog> findByTarget(StoreScope scope, String targetType, UUID targetId);

    List<AuditLog> findByOperation(StoreScope scope, String operationCode, TimeRange timeRange);
}
