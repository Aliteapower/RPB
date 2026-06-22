package com.rpb.reservation.table.persistence.mapper;

import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.persistence.entity.DiningTableEntity;

public interface DiningTableMapper {

    DiningTable toDomain(DiningTableEntity entity);

    DiningTableEntity toEntity(DiningTable domain);
}
