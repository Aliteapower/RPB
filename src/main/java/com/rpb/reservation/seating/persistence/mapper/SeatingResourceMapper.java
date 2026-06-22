package com.rpb.reservation.seating.persistence.mapper;

import com.rpb.reservation.common.persistence.mapper.SeatingResourceTargetMapping;
import com.rpb.reservation.seating.domain.SeatingResource;
import com.rpb.reservation.seating.persistence.entity.SeatingResourceEntity;

public interface SeatingResourceMapper {

    SeatingResource toDomain(SeatingResourceEntity entity);

    SeatingResourceEntity toEntity(SeatingResource domain);

    SeatingResourceTargetMapping toTargetMapping(SeatingResourceEntity entity);
}
