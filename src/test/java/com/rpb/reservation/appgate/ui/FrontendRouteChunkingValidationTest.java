package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FrontendRouteChunkingValidationTest {
    @Test
    void routePagesAreLazyLoadedToKeepInitialChunkSmall() throws Exception {
        String router = FrontendSourceSupport.readString(Path.of("src", "router", "index.ts"));

        assertThat(router)
            .doesNotContain("import LoginPage from '../pages/LoginPage.vue'")
            .doesNotContain("import PlatformTenantsPage from '../pages/PlatformTenantsPage.vue'")
            .doesNotContain("import ReservationTodayViewPage from '../pages/ReservationTodayViewPage.vue'")
            .contains("const LoginPage = () => import('../pages/LoginPage.vue')")
            .contains("const PlatformI18nCatalogPage = () => import('../pages/PlatformI18nCatalogPage.vue')")
            .contains("const TenantAdminI18nCatalogPage = () => import('../pages/TenantAdminI18nCatalogPage.vue')")
            .contains("const ReservationTodayViewPage = () => import('../pages/ReservationTodayViewPage.vue')");
    }
}
