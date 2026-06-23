package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReservationCancelUiImplementationValidationTest {
    @Test
    void reservationCancelUiWiresTodayListToBackendContract() throws Exception {
        Path apiPath = Path.of("src", "api", "reservationCancelApi.ts");
        Path typesPath = Path.of("src", "types", "reservationCancel.ts");
        Path todayPagePath = Path.of("src", "pages", "ReservationTodayViewPage.vue");
        Path todayListPanelPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListPanel.vue");
        Path todayListItemPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListItem.vue");

        assertThat(apiPath).exists();
        assertThat(typesPath).exists();
        assertThat(todayPagePath).exists();
        assertThat(todayListPanelPath).exists();
        assertThat(todayListItemPath).exists();

        String apiClient = Files.readString(apiPath);
        String types = Files.readString(typesPath);
        String todayPage = Files.readString(todayPagePath);
        String todayListPanel = Files.readString(todayListPanelPath);
        String todayListItem = Files.readString(todayListItemPath);

        assertThat(apiClient)
            .contains("cancelReservation")
            .contains("/api/v1/stores/${encodeURIComponent(storeId)}/reservations/${encodeURIComponent(reservationId)}/cancel")
            .contains("'Idempotency-Key': idempotencyKey")
            .contains("body: JSON.stringify(toApiBody(request))")
            .contains("reasonCode")
            .contains("note")
            .contains("ReservationCancelApiError")
            .contains("NETWORK_FAILURE")
            .contains("FORBIDDEN")
            .contains("reservation.forbidden");
        assertForbiddenCancelApiSideEffectsAbsent(apiClient);

        assertThat(types)
            .contains("interface CancelReservationRequest")
            .contains("interface CancelReservationResponse")
            .contains("interface ReservationCancelApiErrorResponse")
            .contains("interface ReservationCancelIdempotency")
            .contains("alreadyCancelled")
            .contains("events")
            .contains("idempotency");

        assertThat(todayListItem)
            .contains("canCancelReservation")
            .contains("canCancel")
            .contains("cancellableStatuses")
            .contains("cancel-requested")
            .contains("取消")
            .doesNotContain("取消预约需后端契约");

        assertThat(todayListPanel)
            .contains("canCancelReservation")
            .contains("cancelReservation")
            .contains("ReservationCancelApiError")
            .contains("@cancel-requested")
            .contains("cancellingReservationId")
            .contains("createReservationCancelIdempotencyKey")
            .contains("reservation.cancel.api_error")
            .contains("emit('cancelled'");

        assertThat(todayPage)
            .contains("fetchMeApps")
            .contains("reservation.cancel")
            .contains("canCancelReservation")
            .contains(":can-cancel-reservation=\"canCancelReservation\"")
            .contains("@cancelled=\"handleReservationCancelled\"")
            .contains("function handleReservationCancelled")
            .contains("void loadTodayView()");
    }

    @Test
    void reservationCancelUiKeepsSafetyV1Boundary() throws Exception {
        List<String> vueFiles = Files.walk(Path.of("src", "pages"))
            .filter(Files::isRegularFile)
            .map(path -> path.toString().replace('\\', '/'))
            .filter(path -> path.endsWith(".vue"))
            .toList();

        assertThat(vueFiles)
            .doesNotContain(
                "src/pages/ReservationCancellationPage.vue",
                "src/pages/ReservationNoShowPage.vue"
            );

        Path apiPath = Path.of("src", "api", "reservationCancelApi.ts");

        assertThat(apiPath).exists();

        String apiClient = Files.readString(apiPath);
        assertForbiddenCancelApiSideEffectsAbsent(apiClient);
    }

    private static void assertForbiddenCancelApiSideEffectsAbsent(String source) {
        assertThat(source)
            .doesNotContain("queueTicketId")
            .doesNotContain("seatingId")
            .doesNotContain("tableId")
            .doesNotContain("tableGroupId")
            .doesNotContain("tableLock")
            .doesNotContain("noShowAt")
            .doesNotContain("/queue")
            .doesNotContain("/seat");
    }
}
