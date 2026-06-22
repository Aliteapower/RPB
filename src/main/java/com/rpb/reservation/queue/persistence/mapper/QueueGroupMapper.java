package com.rpb.reservation.queue.persistence.mapper;

import com.rpb.reservation.queue.domain.QueueGroup;
import com.rpb.reservation.queue.persistence.entity.QueueGroupEntity;

public interface QueueGroupMapper {

    QueueGroup toDomain(QueueGroupEntity entity);

    QueueGroupEntity toEntity(QueueGroup domain);
}
