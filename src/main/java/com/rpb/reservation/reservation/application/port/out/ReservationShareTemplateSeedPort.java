package com.rpb.reservation.reservation.application.port.out;

import com.rpb.reservation.reservation.application.ReservationShareTemplateSeed;
import java.util.Optional;

public interface ReservationShareTemplateSeedPort {
    Optional<ReservationShareTemplateSeed> findActiveBySeedKey(String seedKey);
}
