package com.rpb.reservation.reservation.persistence.mapper;

import com.rpb.reservation.reservation.domain.ReservationPreassignment;
import com.rpb.reservation.reservation.persistence.entity.ReservationPreassignmentEntity;

public interface ReservationPreassignmentMapper {

    ReservationPreassignment toDomain(ReservationPreassignmentEntity entity);

    ReservationPreassignmentEntity toEntity(ReservationPreassignment domain);
}
