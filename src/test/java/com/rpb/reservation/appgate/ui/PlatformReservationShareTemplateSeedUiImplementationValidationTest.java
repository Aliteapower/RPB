package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlatformReservationShareTemplateSeedUiImplementationValidationTest {
    @Test
    void platformReservationShareTemplateSeedPageUsesPlatformSeedApi() throws Exception {
        Path pagePath = Path.of("src", "pages", "PlatformReservationShareTemplateSeedPage.vue");
        Path apiPath = Path.of("src", "api", "platformReservationShareTemplateSeedApi.ts");
        Path typesPath = Path.of("src", "types", "platformReservationShareTemplateSeed.ts");
        Path previewPath = Path.of("src", "utils", "reservationShareTemplatePreview.ts");
        Path routerPath = Path.of("src", "router", "index.ts");
        Path navPath = Path.of("src", "components", "platform", "PlatformAdminNav.vue");

        assertThat(pagePath).exists();
        assertThat(apiPath).exists();
        assertThat(typesPath).exists();
        assertThat(previewPath).exists();

        String page = Files.readString(pagePath);
        String apiClient = Files.readString(apiPath);
        String types = Files.readString(typesPath);
        String preview = Files.readString(previewPath);
        String router = Files.readString(routerPath);
        String nav = Files.readString(navPath);

        assertThat(router)
            .contains("PlatformReservationShareTemplateSeedPage")
            .contains("path: '/platform/reservation/share-template-seed'")
            .contains("name: 'platform-reservation-share-template-seed'")
            .contains("meta: { requiresPlatformAdmin: true }");

        assertThat(nav)
            .contains("/platform/reservation/share-template-seed")
            .contains("预约确认模板");

        assertThat(apiClient)
            .contains("getPlatformReservationShareTemplateSeed")
            .contains("updatePlatformReservationShareTemplateSeed")
            .contains("/api/v1/platform/reservation/share-template-seed")
            .contains("method: 'GET'")
            .contains("method: 'PATCH'")
            .doesNotContain("tenantId")
            .doesNotContain("storeId")
            .doesNotContain("mock");

        assertThat(types)
            .contains("interface PlatformReservationShareTemplateSeed")
            .contains("interface PlatformReservationShareTemplateSeedMutation")
            .contains("allowedVariables")
            .contains("version")
            .doesNotContain("tenantId")
            .doesNotContain("storeId");

        assertThat(page)
            .contains("平台预约确认模板")
            .contains("getPlatformReservationShareTemplateSeed")
            .contains("updatePlatformReservationShareTemplateSeed")
            .contains("getPlatformProfile")
            .contains("applyPlatformProfilePreviewSource")
            .contains("buildReservationShareTemplatePreviewVariables")
            .contains("renderReservationShareTemplatePreview")
            .contains("previewText")
            .contains("模板预览")
            .contains("preview-panel")
            .contains("availableVariables")
            .contains("insertVariable")
            .contains("guestSalutation")
            .contains("tableCode")
            .contains("holdMinutes")
            .contains("textarea")
            .contains("templateText")
            .contains("version")
            .contains("平台种子模板")
            .doesNotContain("tenantId")
            .doesNotContain("storeId");

        assertThat(preview)
            .contains("renderReservationShareTemplatePreview")
            .contains("buildReservationShareTemplatePreviewVariables")
            .contains("reservationShareTemplatePreviewVariables")
            .contains("15-07-2026")
            .contains("A01")
            .contains("guestSalutation")
            .contains("holdMinutes")
            .contains("storePhone")
            .contains("storeAddress")
            .doesNotContain("fetch(")
            .doesNotContain("tenantId")
            .doesNotContain("storeId");
    }
}
