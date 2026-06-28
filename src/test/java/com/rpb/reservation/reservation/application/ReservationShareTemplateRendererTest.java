package com.rpb.reservation.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.reservation.application.service.ReservationShareTemplateCatalog;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateRenderer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReservationShareTemplateRendererTest {

    @Test
    void rendersAllowedVariablesAndLeavesMissingFutureVariablesEmpty() {
        ReservationShareTemplateRenderer renderer = new ReservationShareTemplateRenderer();

        String rendered = renderer.render(
            "门店：{{ storeName }}\n编号：{{reservationNo}}\n桌位：{{tableCode}}\n后续：{{confirmInstruction}}",
            Map.of(
                "storeName",
                "食刻订位中心",
                "reservationNo",
                "R-20300620-0007",
                "tableCode",
                "A01"
            )
        );

        assertThat(rendered).isEqualTo("门店：食刻订位中心\n编号：R-20300620-0007\n桌位：A01\n后续：");
    }

    @Test
    void reportsUnknownVariablesWithoutRenderingUnsafeTemplateTokens() {
        ReservationShareTemplateRenderer renderer = new ReservationShareTemplateRenderer();

        assertThat(renderer.unknownVariables("{{storeName}}\n{{badVariable}}\n{{ googleMapUrl }}"))
            .containsExactly("badVariable");
    }

    @Test
    void reportsMalformedPlaceholderNamesAsUnknownVariables() {
        ReservationShareTemplateRenderer renderer = new ReservationShareTemplateRenderer();

        assertThat(renderer.unknownVariables("{{store_name}}\n{{ storeName }}"))
            .containsExactly("store_name");
    }

    @Test
    void defaultTemplateIsBackendOwnedPlainTextAndUsesOnlyWhitelistedVariables() {
        String defaultTemplate = ReservationShareTemplateCatalog.defaultTemplate();

        assertThat(defaultTemplate)
            .contains("尊敬的 {{contactName}} {{guestSalutation}}")
            .contains("感谢您选择 {{storeName}}")
            .contains("预订编号：{{reservationNo}}")
            .contains("日期：{{reservationDate}}")
            .contains("桌位：{{tableCode}} (已预留)")
            .contains("保留座位 {{holdMinutes}}分钟")
            .contains("{{storeName}}")
            .contains("{{googleMapUrl}}")
            .doesNotContain("whatsapp")
            .doesNotContain("maps.googleapis.com")
            .doesNotContain("google.maps");

        assertThat(new ReservationShareTemplateRenderer().unknownVariables(defaultTemplate)).isEmpty();
        assertThat(ReservationShareTemplateCatalog.allowedVariables())
            .contains(
                "storeName",
                "reservationNo",
                "reservationDate",
                "reservationTime",
                "partySize",
                "tableCode",
                "holdMinutes",
                "contactName",
                "guestSalutation",
                "maskedPhone",
                "storeAddress",
                "googleMapUrl",
                "storePhone",
                "arrivalNote",
                "confirmInstruction",
                "cancelInstruction",
                "changeInstruction",
                "replyInstruction"
            );
    }
}
