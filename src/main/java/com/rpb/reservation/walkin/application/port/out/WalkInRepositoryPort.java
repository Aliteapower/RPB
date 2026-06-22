package com.rpb.reservation.walkin.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.walkin.domain.WalkIn;
import com.rpb.reservation.walkin.value.WalkInId;
import java.util.List;
import java.util.Optional;

public interface WalkInRepositoryPort {

    Optional<WalkIn> findById(StoreScope scope, WalkInId walkInId);

    Optional<WalkIn> findByCode(StoreScope scope, String walkInCode);

    List<WalkIn> findArrivals(StoreScope scope, BusinessDate businessDate, String statusCode);

    WalkIn save(StoreScope scope, WalkIn walkIn);
}
