package com.rpb.reservation.audit.application.port.out;

import com.rpb.reservation.audit.domain.StateTransitionLog;
import com.rpb.reservation.common.scope.PlatformScope;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StateTransitionLogRepositoryPort {

    StateTransitionLog append(StoreScope scope, StateTransitionLog transitionLog);

    StateTransitionLog append(TenantScope scope, StateTransitionLog transitionLog);

    StateTransitionLog append(PlatformScope scope, StateTransitionLog transitionLog);

    List<StateTransitionLog> findByTarget(StoreScope scope, String targetType, UUID targetId);

    Optional<StateTransitionLog> findLatest(StoreScope scope, String targetType, UUID targetId);
}
