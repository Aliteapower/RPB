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
    void acceptsLegacyReservationCodeAndReservedStartAtAliases() {
        ReservationShareTemplateRenderer renderer = new ReservationShareTemplateRenderer();

        String template = "编号：{{reservationCode}}\n到店时间：{{reservedStartAt}}\n人数：{{partySize}}";

        assertThat(renderer.unknownVariables(template)).isEmpty();
        assertThat(renderer.render(
            template,
            Map.of(
                "reservationCode",
                "R-20300620-0007",
                "reservedStartAt",
                "20-06-2030 11:30",
                "partySize",
                "4"
            )
        )).isEqualTo("编号：R-20300620-0007\n到店时间：20-06-2030 11:30\n人数：4");
    }

    @Test
    void defaultTemplateIsBackendOwnedPlainTextAndUsesOnlyWhitelistedVariables() {
        String defaultTemplate = ReservationShareTemplateCatalog.defaultTemplate();

        assertThat(defaultTemplate)
            .contains("Dear {{contactName}} {{guestSalutation}}")
            .contains("Thank you for choosing {{storeName}}")
            .contains("Booking no.: {{reservationNo}}")
            .contains("Date: {{reservationDate}}")
            .contains("Table: {{tableCode}} (reserved)")
            .contains("hold your table for {{holdMinutes}} minutes")
            .contains("Arrival note: {{arrivalNote}}")
            .contains("Store address: {{storeAddress}}")
            .contains("Contact phone: {{storePhone}}")
            .contains("To change or cancel, please contact the store at least 2 hours ahead.")
            .contains("{{storeName}}")
            .doesNotContain("Google Map")
            .doesNotContain("{{googleMapUrl}}")
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
