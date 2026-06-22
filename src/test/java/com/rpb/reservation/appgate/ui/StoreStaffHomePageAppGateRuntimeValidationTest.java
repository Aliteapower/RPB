package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StoreStaffHomePageAppGateRuntimeValidationTest {
    private static final String LOCAL_VALIDATION_TENANT_ID = "10000000-0000-0000-0000-000000000983";
    private static final String LOCAL_VALIDATION_STORE_ID = "20000000-0000-0000-0000-000000000983";

    @Test
    void staffHomeRendersReservationQueueOperationsOnlyBehindMeAppsEntry() throws Exception {
        String source = Files.readString(Path.of("src", "pages", "StoreStaffHomePage.vue"));

        assertThat(source)
            .contains("fetchMeApps")
            .contains("reservation_queue")
            .contains("const hasReservationQueue")
            .contains("const hasVisibleOperation")
            .contains("<nav v-if=\"hasVisibleOperation\" class=\"operation-list\"")
            .contains("<section v-if=\"hasVisibleOperation\" class=\"handoff-notes\"")
            .contains("reservation.check_in")
            .contains("reservation.seat");
        assertThat(source)
            .doesNotContain("<nav class=\"operation-list\"");
    }

    @Test
    void localValidationDefaultsUseSingleStoreBaseline() throws Exception {
        String routerSource = Files.readString(Path.of("src", "router", "index.ts"));
        String storeContextSource = Files.readString(Path.of("src", "stores", "storeContext.ts"));
        String handoffSource = Files.readString(Path.of("docs", "frontend", "STORE_STAFF_OPERATIONAL_HANDOFF.md"));

        assertThat(routerSource)
            .contains(LOCAL_VALIDATION_STORE_ID)
            .doesNotContain("00000000-0000-0000-0000-000000000000");
        assertThat(storeContextSource)
            .contains(LOCAL_VALIDATION_STORE_ID);
        assertThat(handoffSource)
            .contains(LOCAL_VALIDATION_TENANT_ID)
            .contains(LOCAL_VALIDATION_STORE_ID)
            .contains("queue.view");
    }
}
