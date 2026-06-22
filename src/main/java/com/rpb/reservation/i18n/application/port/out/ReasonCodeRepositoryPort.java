package com.rpb.reservation.i18n.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.i18n.domain.ReasonCode;
import java.util.List;
import java.util.Optional;

public interface ReasonCodeRepositoryPort {

    List<ReasonCode> findActiveByType(TenantScope scope, String reasonType);

    List<ReasonCode> findActiveByType(StoreScope scope, String reasonType);

    Optional<ReasonCode> findByCode(TenantScope scope, String reasonType, String code);

    Optional<ReasonCode> findByCode(StoreScope scope, String reasonType, String code);

    ReasonCode save(TenantScope scope, ReasonCode reasonCode);

    ReasonCode save(StoreScope scope, ReasonCode reasonCode);
}
