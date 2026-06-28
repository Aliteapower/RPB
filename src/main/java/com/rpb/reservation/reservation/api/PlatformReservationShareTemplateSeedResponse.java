package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.PlatformReservationShareTemplateSeed;
import java.util.List;

public record PlatformReservationShareTemplateSeedResponse(boolean success, SeedResponse seed) {
    public static PlatformReservationShareTemplateSeedResponse from(PlatformReservationShareTemplateSeed seed) {
        return new PlatformReservationShareTemplateSeedResponse(true, SeedResponse.from(seed));
    }

    public record SeedResponse(
        String seedKey,
        String displayName,
        String locale,
        String templateText,
        String status,
        int version,
        List<String> allowedVariables
    ) {
        private static SeedResponse from(PlatformReservationShareTemplateSeed seed) {
            return new SeedResponse(
                seed.seedKey(),
                seed.displayName(),
                seed.locale(),
                seed.templateText(),
                seed.status(),
                seed.version(),
                seed.allowedVariables()
            );
        }
    }
}
