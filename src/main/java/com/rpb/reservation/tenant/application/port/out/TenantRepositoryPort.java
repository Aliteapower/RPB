package com.rpb.reservation.tenant.application.port.out;

import com.rpb.reservation.common.scope.PlatformScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.tenant.domain.Tenant;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.Optional;

public interface TenantRepositoryPort {

    Optional<Tenant> findById(TenantScope scope, TenantId tenantId);

    Optional<Tenant> findByCode(PlatformScope scope, String tenantCode);

    Tenant save(PlatformScope scope, Tenant tenant);
}
