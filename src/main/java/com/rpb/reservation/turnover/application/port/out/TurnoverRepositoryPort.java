package com.rpb.reservation.turnover.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.turnover.domain.Turnover;
import java.util.List;
import java.util.Optional;

public interface TurnoverRepositoryPort {

    Optional<Turnover> findBySeating(StoreScope scope, SeatingId seatingId);

    List<Turnover> findByBusinessDate(StoreScope scope, BusinessDate businessDate);

    Turnover save(StoreScope scope, Turnover turnover);
}
