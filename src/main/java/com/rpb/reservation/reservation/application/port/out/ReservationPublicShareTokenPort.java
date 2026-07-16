package com.rpb.reservation.reservation.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import java.util.UUID;

public interface ReservationPublicShareTokenPort {
    String ensureActiveToken(StoreScope scope, UUID reservationId, String tokenCandidate);
}
