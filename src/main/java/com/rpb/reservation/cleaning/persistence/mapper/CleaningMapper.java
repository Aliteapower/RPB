package com.rpb.reservation.cleaning.persistence.mapper;

import com.rpb.reservation.cleaning.domain.Cleaning;
import com.rpb.reservation.cleaning.persistence.entity.CleaningEntity;
import com.rpb.reservation.common.persistence.mapper.CleaningResourceTargetMapping;

public interface CleaningMapper {

    Cleaning toDomain(CleaningEntity entity);

    CleaningEntity toEntity(Cleaning domain);

    CleaningResourceTargetMapping toTargetMapping(CleaningEntity entity);
}
