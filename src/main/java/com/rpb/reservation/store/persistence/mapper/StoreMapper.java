package com.rpb.reservation.store.persistence.mapper;

import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.persistence.entity.StoreEntity;

public interface StoreMapper {

    Store toDomain(StoreEntity entity);

    StoreEntity toEntity(Store domain);
}
