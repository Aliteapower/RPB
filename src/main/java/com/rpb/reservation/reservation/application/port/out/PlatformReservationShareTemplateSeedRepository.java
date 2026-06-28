package com.rpb.reservation.reservation.application.port.out;

import com.rpb.reservation.reservation.application.PlatformReservationShareTemplateSeed;
import java.util.Optional;

public interface PlatformReservationShareTemplateSeedRepository {
    Optional<PlatformReservationShareTemplateSeed> findBySeedKey(String seedKey);

    PlatformReservationShareTemplateSeed update(
        String seedKey,
        String displayName,
        String locale,
        String templateText,
        String status
    );
}
