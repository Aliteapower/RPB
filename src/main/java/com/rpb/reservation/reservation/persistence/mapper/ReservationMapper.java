package com.rpb.reservation.reservation.persistence.mapper;

import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.persistence.entity.ReservationEntity;

public interface ReservationMapper {

    Reservation toDomain(ReservationEntity entity);

    ReservationEntity toEntity(Reservation domain);
}
