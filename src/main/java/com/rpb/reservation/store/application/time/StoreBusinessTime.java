package com.rpb.reservation.store.application.time;

import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.store.domain.Store;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class StoreBusinessTime {

    private final Clock clock;

    public StoreBusinessTime(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock_required");
    }

    public Instant instant() {
        return Instant.now(clock);
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public BusinessDate businessDate(Store store) {
        Objects.requireNonNull(store, "store_required");
        return new BusinessDate(LocalDate.now(clock.withZone(zoneId(store.timezone()))));
    }

    private static ZoneId zoneId(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            return ZoneOffset.UTC;
        }
    }
}
