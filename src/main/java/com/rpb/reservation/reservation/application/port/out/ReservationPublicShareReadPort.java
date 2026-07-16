package com.rpb.reservation.reservation.application.port.out;

import java.util.Optional;

public interface ReservationPublicShareReadPort {
    Optional<ReservationPublicShareRow> findByToken(String token);
}
