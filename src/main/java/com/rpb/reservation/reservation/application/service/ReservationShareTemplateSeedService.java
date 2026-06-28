package com.rpb.reservation.reservation.application.service;

import com.rpb.reservation.reservation.application.port.out.ReservationShareTemplateSeedPort;
import org.springframework.stereotype.Service;

@Service
public class ReservationShareTemplateSeedService {
    private final ReservationShareTemplateSeedPort seedPort;

    public ReservationShareTemplateSeedService(ReservationShareTemplateSeedPort seedPort) {
        this.seedPort = seedPort;
    }

    public String defaultTemplate() {
        return seedPort.findActiveBySeedKey(ReservationShareTemplateCatalog.defaultSeedKey())
            .map(seed -> clean(seed.templateText()))
            .filter(ReservationShareTemplateSeedService::hasText)
            .orElseGet(ReservationShareTemplateCatalog::defaultTemplate);
    }

    private static String clean(String value) {
        return hasText(value) ? value.trim() : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
