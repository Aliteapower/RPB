package com.rpb.reservation.seating.persistence.mapper;

import com.rpb.reservation.common.persistence.mapper.SeatingSourceMapping;
import com.rpb.reservation.seating.domain.Seating;
import com.rpb.reservation.seating.persistence.entity.SeatingEntity;

public interface SeatingMapper {

    Seating toDomain(SeatingEntity entity);

    SeatingEntity toEntity(Seating domain);

    SeatingSourceMapping toSourceMapping(SeatingEntity entity);
}
