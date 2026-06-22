package com.rpb.reservation.turnover.persistence.mapper;

import com.rpb.reservation.turnover.domain.Turnover;
import com.rpb.reservation.turnover.persistence.entity.TurnoverEntity;

public interface TurnoverMapper {

    Turnover toDomain(TurnoverEntity entity);

    TurnoverEntity toEntity(Turnover domain);
}
