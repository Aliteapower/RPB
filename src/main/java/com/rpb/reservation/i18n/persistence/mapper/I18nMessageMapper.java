package com.rpb.reservation.i18n.persistence.mapper;

import com.rpb.reservation.i18n.domain.I18nMessage;
import com.rpb.reservation.i18n.persistence.entity.I18nMessageEntity;

public interface I18nMessageMapper {

    I18nMessage toDomain(I18nMessageEntity entity);

    I18nMessageEntity toEntity(I18nMessage domain);
}
