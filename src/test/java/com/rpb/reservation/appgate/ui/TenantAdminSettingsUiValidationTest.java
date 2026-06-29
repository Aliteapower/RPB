package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TenantAdminSettingsUiValidationTest {

    @Test
    void tenantAdminSettingsUsesBusinessDateFormatRule() throws Exception {
        String page = Files.readString(Path.of("src", "pages", "TenantAdminSettingsPage.vue"));

        assertThat(page)
            .contains("dateFormat: 'DD-MM-YYYY'")
            .contains("placeholder=\"DD-MM-YYYY\"")
            .contains("getStoreReservationMealPeriods")
            .contains("updateStoreReservationMealPeriods")
            .contains("mealPeriodForm.usePlatformSeed")
            .contains("copyPlatformSeed")
            .contains("预约餐段")
            .doesNotContain("dateFormat: 'yyyy-MM-dd'");
    }
}
