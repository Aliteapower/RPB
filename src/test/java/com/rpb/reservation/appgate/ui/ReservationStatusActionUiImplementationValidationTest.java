package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReservationStatusActionUiImplementationValidationTest {
    @Test
    void reservationStatusActionUiKeepsNoShowOnTodayListAndMovesCompletionToTablePage() throws Exception {
        Path apiPath = Path.of("src", "api", "reservationStatusActionApi.ts");
        Path typesPath = Path.of("src", "types", "reservationStatusAction.ts");
        Path todayPagePath = Path.of("src", "pages", "ReservationTodayViewPage.vue");
        Path todayListPanelPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListPanel.vue");
        Path todayListItemPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListItem.vue");
        Path tablePagePath = Path.of("src", "pages", "TableResourceListPage.vue");

        assertThat(apiPath).exists();
        assertThat(typesPath).exists();
        assertThat(todayPagePath).exists();
        assertThat(todayListPanelPath).exists();
        assertThat(todayListItemPath).exists();
        assertThat(tablePagePath).exists();

        String apiClient = FrontendSourceSupport.readString(apiPath);
        String types = FrontendSourceSupport.readString(typesPath);
        String todayPage = FrontendSourceSupport.readString(todayPagePath);
        String todayListPanel = FrontendSourceSupport.readString(todayListPanelPath);
        String todayListItem = FrontendSourceSupport.readString(todayListItemPath);
        String tablePage = FrontendSourceSupport.readString(tablePagePath);

        assertThat(apiClient)
            .contains("markReservationNoShow")
            .contains("completeReservation")
            .contains("'no-show'")
            .contains("'complete'")
            .contains("'Idempotency-Key': idempotencyKey")
            .contains("'reservation.no_show'")
            .contains("'reservation.complete'")
            .contains("`${messageKeyPrefix}.request_failed`");

        assertThat(types)
            .contains("interface MarkReservationNoShowRequest")
            .contains("interface CompleteReservationRequest")
            .contains("interface MarkReservationNoShowResponse")
            .contains("interface CompleteReservationResponse")
            .contains("alreadyNoShow")
            .contains("alreadyCompleted");

        assertThat(todayPage)
            .contains("reservation.no_show")
            .contains(":can-no-show-reservation=\"canNoShowReservation\"")
            .contains("@no-showed=\"handleReservationNoShowed\"")
            .doesNotContain(":can-complete-reservation=\"canCompleteReservation\"")
            .doesNotContain("@completed=\"handleReservationCompleted\"")
            .doesNotContain("ReservationTableSwitchDialog")
            .doesNotContain("openReservationTableSwitchDialog");

        assertThat(todayListPanel)
            .contains("markReservationNoShow")
            .contains("noShowingReservationId")
            .contains("emit('no-showed'")
            .doesNotContain("completeReservation")
            .doesNotContain("completingReservationId")
            .doesNotContain("emit('completed'")
            .doesNotContain("switch-table-requested");

        assertThat(todayListItem)
            .contains("noShowableStatuses")
            .contains("no-show-requested")
            .contains("reservationWorkbench.item.noShow")
            .doesNotContain("showComplete")
            .doesNotContain("complete-requested")
            .doesNotContain("结桌")
            .doesNotContain("switch-table-requested")
            .doesNotContain("换桌");

        assertThat(tablePage)
            .contains("completeReservation")
            .contains("currentReservationId")
            .contains("completeReservationForResourceIfNeeded")
            .contains("ReservationTableSwitchDialog")
            .contains("清台")
            .doesNotContain("结桌");
    }
}
