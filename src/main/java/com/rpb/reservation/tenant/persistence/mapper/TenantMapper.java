package com.rpb.reservation.tenant.persistence.mapper;

import com.rpb.reservation.tenant.domain.Tenant;
import com.rpb.reservation.tenant.persistence.entity.TenantEntity;

public interface TenantMapper {

    Tenant toDomain(TenantEntity entity);

    TenantEntity toEntity(Tenant domain);
}
