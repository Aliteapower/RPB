package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminResponsiveUiValidationTest {

    @Test
    void platformAndTenantAdminNavStayCompactOnMobile() throws Exception {
        Path platformNavPath = Path.of("src", "components", "platform", "PlatformAdminNav.vue");
        Path tenantNavPath = Path.of("src", "components", "tenant-admin", "TenantAdminNav.vue");

        String platformNav = Files.readString(platformNavPath);
        String tenantNav = Files.readString(tenantNavPath);

        assertCompactMobileNav(platformNav);
        assertCompactMobileNav(tenantNav);
    }

    @Test
    void erpQueryToolbarKeepsSearchAndResetGroupedOnMobile() throws Exception {
        Path toolbarPath = Path.of("src", "components", "erp", "ErpQueryToolbar.vue");

        String toolbar = Files.readString(toolbarPath);

        assertThat(toolbar)
            .contains("class=\"query-buttons\"")
            .contains("grid-template-columns: repeat(2, minmax(0, 1fr));")
            .contains(".query-box {")
            .contains("input {")
            .doesNotContain(".erp-query-toolbar,\n  .query-box,\n  .toolbar-actions,\n  input,\n  .secondary-button {\n    width: 100%;\n  }");
    }

    private static void assertCompactMobileNav(String navSource) {
        assertThat(navSource)
            .contains("@media (max-width: 700px)")
            .contains("grid-template-columns: minmax(0, 1fr) auto;")
            .contains("grid-column: 1 / -1;")
            .contains("overflow-x: auto;")
            .contains("white-space: nowrap;")
            .doesNotContain("grid-auto-flow: row;");
    }
}
