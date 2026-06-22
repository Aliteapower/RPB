package com.rpb.reservation.store.persistence.mapper;

import com.rpb.reservation.store.domain.Area;
import com.rpb.reservation.store.persistence.entity.StoreAreaEntity;

public interface StoreAreaMapper {

    Area toDomain(StoreAreaEntity entity);

    StoreAreaEntity toEntity(Area domain);
}
