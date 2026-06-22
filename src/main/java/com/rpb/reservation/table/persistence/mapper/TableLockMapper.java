package com.rpb.reservation.table.persistence.mapper;

import com.rpb.reservation.table.domain.TableLock;
import com.rpb.reservation.table.persistence.entity.TableLockEntity;

public interface TableLockMapper {

    TableLock toDomain(TableLockEntity entity);

    TableLockEntity toEntity(TableLock domain);
}
