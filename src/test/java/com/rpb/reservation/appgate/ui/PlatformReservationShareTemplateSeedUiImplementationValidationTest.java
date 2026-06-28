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
            .contains("点击字段会把 &#123;&#123;字段名&#125;&#125; 加入到模板内容中")
            .contains("保存后的平台种子模板会用于租户默认订位分享模板")
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
            .contains("示例门店")
            .contains("示例地址 1 号")
            .contains("https://example.com/map")
            .contains("0000 0000")
            .contains("15-07-2026")
            .contains("A01")
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
