package com.rpb.reservation.idempotency.persistence.mapper;

import com.rpb.reservation.common.persistence.mapper.SnapshotPayload;
import com.rpb.reservation.common.persistence.mapper.TargetRef;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.persistence.entity.IdempotencyRecordEntity;

public interface IdempotencyMapper {

    IdempotencyRecord toDomain(IdempotencyRecordEntity entity);

    IdempotencyRecordEntity toEntity(IdempotencyRecord domain);

    TargetRef toTargetRef(IdempotencyRecordEntity entity);

    SnapshotPayload toResponseSnapshot(IdempotencyRecordEntity entity);
}
