package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReservationTableAssignmentUiValidationTest {

    @Test
    void confirmedUnassignedReservationShowsModularAssignmentDialog() throws IOException {
        String item = source("src/components/reservation-workbench/ReservationTodayListItem.vue");
        String panel = source("src/components/reservation-workbench/ReservationTodayListPanel.vue");
        String page = source("src/pages/ReservationTodayViewPage.vue");
        String dialog = source("src/components/reservation-workbench/ReservationTableAssignmentDialog.vue");
        String api = source("src/api/reservationTableAssignmentApi.ts");

        assertThat(item).contains(
            "props.item.status === 'confirmed'",
            "!props.item.assignedResourceId?.trim()",
            "table-assignment-requested"
        );
        assertThat(panel).contains("table-assignment-requested");
        assertThat(page).contains(
            "ReservationTableAssignmentDialog",
            "canAssignReservationTable",
            "@table-assignment-requested"
        );
        assertThat(dialog).contains("getAssignableReservationTables", "assignReservationTable");
        assertThat(api).contains("assignable-tables", "table-assignment", "Idempotency-Key");
    }

    @Test
    void assignmentRefreshesTodayViewAndInvalidatesCachedShareInfo() throws IOException {
        String item = source("src/components/reservation-workbench/ReservationTodayListItem.vue");
        String page = source("src/pages/ReservationTodayViewPage.vue");

        assertThat(item).contains(
            "props.item.assignedResourceId",
            "props.item.assignedResourceCode",
            "shareInfo.value = null"
        );
        assertThat(page).contains("@assigned=\"handleReservationTableAssigned\"", "void loadTodayView()");
    }

    @Test
    void newUserFacingTextIsLocalizedInBothSupportedLocales() throws IOException {
        String zh = source("src/i18n/locales/zh-CN.ts");
        String en = source("src/i18n/locales/en-SG.ts");

        assertThat(zh).contains("tableAssignment:", "指定桌号", "暂无可指定桌号");
        assertThat(en).contains("tableAssignment:", "Assign table", "No assignable tables");
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
