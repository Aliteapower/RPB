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
        Path zhPath = Path.of("src", "i18n", "locales", "zh-CN.ts");

        assertThat(pagePath).exists();
        assertThat(apiPath).exists();
        assertThat(typesPath).exists();
        assertThat(previewPath).exists();

        String page = FrontendSourceSupport.readString(pagePath);
        String apiClient = FrontendSourceSupport.readString(apiPath);
        String types = FrontendSourceSupport.readString(typesPath);
        String preview = FrontendSourceSupport.readString(previewPath);
        String router = FrontendSourceSupport.readString(routerPath);
        String nav = FrontendSourceSupport.readString(navPath);
        String zh = FrontendSourceSupport.readString(zhPath);

        assertThat(router)
            .contains("PlatformReservationShareTemplateSeedPage")
            .contains("path: '/platform/reservation/share-template-seed'")
            .contains("name: 'platform-reservation-share-template-seed'")
            .contains("meta: { requiresPlatformAdmin: true }");

        assertThat(nav)
            .contains("/platform/reservation/share-template-seed")
            .contains("nav.platform.shareTemplateSeed");
        assertThat(zh)
            .contains("shareTemplateSeed: '预约确认模板'");

        assertThat(apiClient)
            .contains("getPlatformReservationShareTemplateSeed")
            .contains("updatePlatformReservationShareTemplateSeed")
            .contains("/api/v1/platform/reservation/share-template-seed")
            .contains("locale?: string")
            .contains("URLSearchParams")
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
            .contains("useI18n")
            .contains("activeLocale")
            .contains("watch(activeLocale")
            .contains("const requestedLocale = activeLocale.value")
            .contains("getPlatformReservationShareTemplateSeed")
            .contains("getPlatformReservationShareTemplateSeed(requestedLocale)")
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
            .contains("generated.platform-reservation-share-template-seed.016")
            .contains("generated.platform-reservation-share-template-seed.017")
            .doesNotContain("ReservationPublicSharePage")
            .doesNotContain("getReservationPublicShare")
            .doesNotContain("/reservation-share/")
            .doesNotContain("/api/v1/public/reservation-shares")
            .doesNotContain("食刻")
            .doesNotContain("上海市")
            .doesNotContain("tenantId")
            .doesNotContain("storeId");

        assertThat(preview)
            .contains("renderReservationShareTemplatePreview")
            .contains("buildReservationShareTemplatePreviewVariables")
            .contains("reservationShareTemplatePreviewVariables")
            .contains("translate(`reservationShareTemplatePreview.variables.${key}`)")
            .contains("storeName")
            .contains("reservationDate")
            .contains("reservationTime")
            .contains("partySize")
            .contains("guestSalutation")
            .contains("tableCode")
            .contains("holdMinutes")
            .contains("storePhone")
            .contains("storeAddress")
            .contains("googleMapUrl")
            .contains("arrivalNote")
            .contains("replyInstruction")
            .doesNotContain("食刻")
            .doesNotContain("上海市")
            .doesNotContain("maps.app.goo.gl")
            .doesNotContain("fetch(")
            .doesNotContain("tenantId")
            .doesNotContain("storeId");
    }
}
