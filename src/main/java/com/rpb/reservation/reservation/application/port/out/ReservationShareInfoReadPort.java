package com.rpb.reservation.reservation.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import java.util.Optional;
import java.util.UUID;

public interface ReservationShareInfoReadPort {
    Optional<ReservationShareInfoRow> findByReservationId(StoreScope scope, UUID reservationId);
}
