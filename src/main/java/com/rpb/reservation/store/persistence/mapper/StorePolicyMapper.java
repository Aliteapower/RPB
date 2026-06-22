package com.rpb.reservation.store.persistence.mapper;

import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.store.persistence.entity.StorePolicyEntity;

public interface StorePolicyMapper {

    StorePolicy toDomain(StorePolicyEntity entity);

    StorePolicyEntity toEntity(StorePolicy domain);
}
