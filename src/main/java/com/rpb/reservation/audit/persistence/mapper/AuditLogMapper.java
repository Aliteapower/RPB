package com.rpb.reservation.audit.persistence.mapper;

import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.persistence.entity.AuditLogEntity;
import com.rpb.reservation.common.persistence.mapper.MetadataPayload;
import com.rpb.reservation.common.persistence.mapper.SnapshotPayload;
import com.rpb.reservation.common.persistence.mapper.TargetRef;

public interface AuditLogMapper {

    AuditLog toDomain(AuditLogEntity entity);

    AuditLogEntity toEntity(AuditLog domain);

    TargetRef toTargetRef(AuditLogEntity entity);

    MetadataPayload toMetadataPayload(AuditLogEntity entity);

    SnapshotPayload toBeforeSnapshot(AuditLogEntity entity);

    SnapshotPayload toAfterSnapshot(AuditLogEntity entity);
}
