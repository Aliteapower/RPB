package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PasswordVisibilityUiValidationTest {

    @Test
    void passwordFieldsUseSharedVisibilityToggleAcrossFrontend() throws Exception {
        Path componentPath = Path.of("src", "components", "common", "PasswordInput.vue");
        Path loginPagePath = Path.of("src", "pages", "LoginPage.vue");
        Path platformTenantFormPath = Path.of("src", "components", "platform", "PlatformTenantForm.vue");
        Path tenantStaffFormPath = Path.of("src", "pages", "TenantAdminStaffFormPage.vue");
        Path publicBookingSettingsPath = Path.of("src", "pages", "TenantAdminPublicBookingPage.vue");

        assertThat(componentPath).exists();

        String componentSource = Files.readString(componentPath);
        String formSource = Files.readString(loginPagePath)
            + Files.readString(platformTenantFormPath)
            + Files.readString(tenantStaffFormPath)
            + Files.readString(publicBookingSettingsPath);

        assertThat(componentSource)
            .contains("passwordVisible")
            .contains("inputType")
            .contains("显示密码")
            .contains("隐藏密码")
            .contains("password-eye-icon")
            .contains("password-eye-off-icon")
            .contains("type=\"button\"")
            .contains("aria-pressed")
            .contains("@click=\"togglePasswordVisibility\"");

        assertThat(formSource)
            .contains("import PasswordInput")
            .contains("autocomplete=\"current-password\"")
            .contains("autocomplete=\"new-password\"")
            .doesNotContain("type=\"password\"");

        assertThat(countOccurrences(formSource, "<PasswordInput")).isEqualTo(7);
    }

    private static int countOccurrences(String source, String token) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
