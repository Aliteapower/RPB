package com.rpb.reservation.table.persistence.mapper;

import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.persistence.entity.TableGroupEntity;

public interface TableGroupMapper {

    TableGroup toDomain(TableGroupEntity entity);

    TableGroupEntity toEntity(TableGroup domain);
}
