package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TenantAdminTableManagementUiValidationTest {

    @Test
    void tenantAdminTableManagementSupportsSortOrderAndExcelImportExport() throws Exception {
        String listPage = FrontendSourceSupport.readString(Path.of("src", "pages", "TenantAdminTablesPage.vue"));
        String formPage = FrontendSourceSupport.readString(Path.of("src", "pages", "TenantAdminTableFormPage.vue"));
        String api = FrontendSourceSupport.readString(Path.of("src", "api", "tenantAdminApi.ts"));

        assertThat(listPage)
            .contains("exportTables")
            .contains("importTables")
            .contains("导出 Excel")
            .contains("导入 Excel")
            .contains("大类排序")
            .contains("桌号排序")
            .contains("areaSortOrder")
            .contains("tableSortOrder")
            .contains("updateTable")
            .contains("moveArea")
            .contains("moveTable")
            .contains("sort-icon-button")
            .contains("上移分区组")
            .contains("下移分区组")
            .contains("上移桌号")
            .contains("下移桌号")
            .contains("启用")
            .contains("停用");

        assertThat(formPage)
            .contains("areaSortOrder")
            .contains("tableSortOrder")
            .contains("大类排序")
            .contains("桌号排序")
            .contains("placeholder=\"自动\"")
            .contains("optionalSortOrder")
            .contains("enabled");

        assertThat(api)
            .contains("areaSortOrder")
            .contains("tableSortOrder")
            .contains("exportTables")
            .contains("importTables")
            .contains("FormData")
            .contains("Blob");
    }
}
