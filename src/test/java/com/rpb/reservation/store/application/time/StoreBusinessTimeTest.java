package com.rpb.reservation.store.application.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoreBusinessTimeTest {

    private static final Instant NOW = Instant.parse("2026-07-16T16:30:00Z");
    private final StoreBusinessTime businessTime = new StoreBusinessTime(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void exposesTheInjectedCurrentTime() {
        assertThat(businessTime.instant()).isEqualTo(NOW);
        assertThat(businessTime.now()).isEqualTo(OffsetDateTime.parse("2026-07-16T16:30:00Z"));
    }

    @Test
    void derivesBusinessDateFromStoreTimezoneAcrossUtcMidnightBoundary() {
        Store singaporeStore = store("Asia/Singapore");

        BusinessDate result = businessTime.businessDate(singaporeStore);

        assertThat(result.value()).isEqualTo(LocalDate.of(2026, 7, 17));
    }

    @Test
    void preservesTheCurrentUtcFallbackForAnInvalidTimezone() {
        Store invalidTimezoneStore = store("invalid/timezone");

        BusinessDate result = businessTime.businessDate(invalidTimezoneStore);

        assertThat(result.value()).isEqualTo(LocalDate.of(2026, 7, 16));
    }

    private static Store store(String timezone) {
        TenantId tenantId = new TenantId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        return new Store(
            new StoreId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
            tenantId,
            "STORE-1",
            timezone,
            "zh-CN",
            "active"
        );
    }
}
