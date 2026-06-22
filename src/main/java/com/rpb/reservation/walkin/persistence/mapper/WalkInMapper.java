package com.rpb.reservation.walkin.persistence.mapper;

import com.rpb.reservation.walkin.domain.WalkIn;
import com.rpb.reservation.walkin.persistence.entity.WalkInEntity;

public interface WalkInMapper {

    WalkIn toDomain(WalkInEntity entity);

    WalkInEntity toEntity(WalkIn domain);
}
