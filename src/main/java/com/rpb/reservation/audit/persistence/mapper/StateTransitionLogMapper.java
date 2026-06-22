package com.rpb.reservation.audit.persistence.mapper;

import com.rpb.reservation.audit.domain.StateTransitionLog;
import com.rpb.reservation.audit.persistence.entity.StateTransitionLogEntity;
import com.rpb.reservation.common.persistence.mapper.MetadataPayload;
import com.rpb.reservation.common.persistence.mapper.SnapshotPayload;
import com.rpb.reservation.common.persistence.mapper.TargetRef;

public interface StateTransitionLogMapper {

    StateTransitionLog toDomain(StateTransitionLogEntity entity);

    StateTransitionLogEntity toEntity(StateTransitionLog domain);

    TargetRef toTargetRef(StateTransitionLogEntity entity);

    MetadataPayload toMetadataPayload(StateTransitionLogEntity entity);

    SnapshotPayload toBeforeSnapshot(StateTransitionLogEntity entity);

    SnapshotPayload toAfterSnapshot(StateTransitionLogEntity entity);
}
