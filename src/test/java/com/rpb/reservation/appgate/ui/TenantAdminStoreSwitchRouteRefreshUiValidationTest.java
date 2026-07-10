package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TenantAdminStoreSwitchRouteRefreshUiValidationTest {

    @Test
    void routeStorePathChangesRemountTenantAdminPages() throws Exception {
        String app = FrontendSourceSupport.readString(Path.of("src", "App.vue"));

        assertThat(app)
            .contains("<RouterView :key=\"$route.path\" />")
            .as("store switch rewrites /stores/:storeId paths and must remount reused tenant-admin pages");
    }
}
