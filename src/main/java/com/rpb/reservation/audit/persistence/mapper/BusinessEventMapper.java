package com.rpb.reservation.audit.persistence.mapper;

import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.audit.persistence.entity.BusinessEventEntity;
import com.rpb.reservation.common.persistence.mapper.MetadataPayload;
import com.rpb.reservation.common.persistence.mapper.SnapshotPayload;
import com.rpb.reservation.common.persistence.mapper.TargetRef;

public interface BusinessEventMapper {

    BusinessEvent toDomain(BusinessEventEntity entity);

    BusinessEventEntity toEntity(BusinessEvent domain);

    TargetRef toTargetRef(BusinessEventEntity entity);

    MetadataPayload toMetadataPayload(BusinessEventEntity entity);

    SnapshotPayload toBeforeSnapshot(BusinessEventEntity entity);

    SnapshotPayload toAfterSnapshot(BusinessEventEntity entity);
}
