package com.rpb.reservation.i18n.persistence.mapper;

import com.rpb.reservation.i18n.domain.ReasonCode;
import com.rpb.reservation.i18n.persistence.entity.ReasonCodeEntity;

public interface ReasonCodeMapper {

    ReasonCode toDomain(ReasonCodeEntity entity);

    ReasonCodeEntity toEntity(ReasonCode domain);
}
