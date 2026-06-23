package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class StaffUiV12TableSelectionValidationTest {

    @Test
    void staffBottomNavContainsHomeReservationQueueAndTableTabs() throws Exception {
        Path itemsPath = Path.of("src", "components", "staff", "staffBottomNavItems.ts");
        Path componentPath = Path.of("src", "components", "staff", "StaffBottomNav.vue");
        Path routerPath = Path.of("src", "router", "index.ts");

        assertThat(Files.exists(itemsPath)).isTrue();
        assertThat(Files.exists(componentPath)).isTrue();

        String itemsSource = Files.readString(itemsPath);
        String componentSource = Files.readString(componentPath);
        String routerSource = Files.readString(routerPath);

        assertAppearsInOrder(
            itemsSource,
            "label: '首页'",
            "routeName: 'store-staff-home'",
            "label: '预约'",
            "routeName: 'reservation-today-view'",
            "label: '排队'",
            "routeName: 'queue-ticket-list'",
            "label: '桌台'",
            "routeName: 'table-resource-list'"
        );
        assertThat(componentSource)
            .contains("RouterLink")
            .contains("activeTab")
            .contains("env(safe-area-inset-bottom)");
        assertThat(routerSource)
            .contains("TableResourceListPage")
            .contains("name: 'table-resource-list'")
            .contains("path: '/stores/:storeId/tables'");
    }

    @Test
    void tablePageAndPickerReadRealConfiguredTableNumbersAndGroups() throws Exception {
        Path tablePagePath = Path.of("src", "pages", "TableResourceListPage.vue");
        Path pickerPath = Path.of("src", "components", "staff-table", "TableResourcePicker.vue");
        Path apiPath = Path.of("src", "api", "tableResourceApi.ts");
        Path typePath = Path.of("src", "types", "tableResource.ts");

        assertThat(Files.exists(tablePagePath)).isTrue();
        assertThat(Files.exists(pickerPath)).isTrue();
        assertThat(Files.exists(apiPath)).isTrue();
        assertThat(Files.exists(typePath)).isTrue();

        String source = Files.readString(tablePagePath)
            + Files.readString(pickerPath)
            + Files.readString(apiPath)
            + Files.readString(typePath);

        assertThat(source)
            .contains("fetchTableResources")
            .contains("/api/v1/stores/${storeId}/tables")
            .contains("resourceType: 'dining_table' | 'table_group'")
            .contains("memberTableCodes")
            .contains("select-table")
            .contains("select-table-group")
            .contains("暂无桌台，请先在后台配置桌台。")
            .doesNotContain("mock")
            .doesNotContain("fake");
    }

    @Test
    void seatingFormsUseTableResourcePickerWithoutChangingSubmitPayloadShape() throws Exception {
        String source = readSources(List.of(
            Path.of("src", "pages", "WalkInDirectSeatingPage.vue"),
            Path.of("src", "pages", "ReservationArrivedDirectSeatingPage.vue"),
            Path.of("src", "pages", "SeatingFromCalledQueuePage.vue")
        ));

        assertThat(source)
            .contains("TableResourcePicker")
            .contains("function selectTable(tableId: string)")
            .contains("function selectTableGroup(tableGroupId: string)")
            .contains("tableId: optionalValue(form.tableId)")
            .contains("tableGroupId: optionalValue(form.tableGroupId)");
    }

    @Test
    void reservationAndQueuePagesReserveBottomNavigationSpace() throws Exception {
        Path staffWorkbenchStylePath = Path.of("src", "styles", "staffWorkbench.css");

        assertThat(staffWorkbenchStylePath).exists();

        String mainSource = Files.readString(Path.of("src", "main.ts"));
        String staffWorkbenchStyle = Files.readString(staffWorkbenchStylePath);

        assertThat(mainSource)
            .contains("import './styles/staffWorkbench.css'");
        assertThat(staffWorkbenchStyle)
            .contains(".staff-workbench-shell")
            .contains("background: linear-gradient(180deg, #f8fafc 0%, #eef4f8 46%, #e8eef4 100%);")
            .contains("max-width: 520px;")
            .contains("min-height: 100dvh;")
            .contains(".staff-workbench-shell--padded")
            .contains("padding: 20px 14px calc(128px + env(safe-area-inset-bottom));")
            .contains("scroll-padding-bottom: calc(128px + env(safe-area-inset-bottom));")
            .contains("border-left: 1px solid #dbe3ee;")
            .contains("border-right: 1px solid #dbe3ee;");

        List<Path> paths = List.of(
            Path.of("src", "pages", "StoreStaffHomePage.vue"),
            Path.of("src", "pages", "ReservationTodayViewPage.vue"),
            Path.of("src", "pages", "ReservationCheckInPage.vue"),
            Path.of("src", "pages", "QueueTicketListPage.vue"),
            Path.of("src", "pages", "TableResourceListPage.vue")
        );

        for (Path path : paths) {
            String source = Files.readString(path);

            assertThat(source)
                .as("%s should render staff bottom navigation", path)
                .contains("StaffBottomNav")
                .contains("active-tab=");
            assertThat(source)
                .as("%s should use the shared lightweight workbench shell", path)
                .contains("staff-workbench-shell")
                .doesNotContain("background: linear-gradient(180deg, #f8fafc 0%, #eef4f8 46%, #e8eef4 100%);")
                .doesNotContain("border-left: 1px solid #dbe3ee;")
                .doesNotContain("border-right: 1px solid #dbe3ee;")
                .doesNotContain("max-width: 680px;");
        }
    }

    @Test
    void reservationTodayPageUsesCalendarRealNavigationAndPermissionGatedCancellation() throws Exception {
        Path pagePath = Path.of("src", "pages", "ReservationTodayViewPage.vue");
        Path actionsPath = Path.of("src", "components", "reservation-workbench", "ReservationQuickActionPanel.vue");
        Path calendarPath = Path.of("src", "components", "reservation-workbench", "ReservationMonthCalendar.vue");
        Path createDialogPath = Path.of("src", "components", "reservation-workbench", "CreateReservationDialog.vue");
        Path todayListPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListPanel.vue");
        Path todayListItemPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListItem.vue");
        Path timePickerPath = Path.of("src", "components", "staff", "StaffTimeWheelPicker.vue");
        Path cancelApiPath = Path.of("src", "api", "reservationCancelApi.ts");

        assertThat(Files.exists(actionsPath)).isTrue();
        assertThat(Files.exists(calendarPath)).isTrue();
        assertThat(Files.exists(createDialogPath)).isTrue();
        assertThat(Files.exists(todayListPath)).isTrue();
        assertThat(Files.exists(todayListItemPath)).isTrue();
        assertThat(Files.exists(timePickerPath)).isTrue();
        assertThat(Files.exists(cancelApiPath)).isTrue();

        String source = Files.readString(pagePath)
            + Files.readString(actionsPath)
            + Files.readString(calendarPath)
            + Files.readString(createDialogPath)
            + Files.readString(todayListPath)
            + Files.readString(todayListItemPath)
            + Files.readString(timePickerPath)
            + Files.readString(cancelApiPath);

        assertThat(source)
            .contains("ReservationQuickActionPanel")
            .contains("ReservationMonthCalendar")
            .contains("CreateReservationDialog")
            .contains("ReservationTodayListPanel")
            .contains("ReservationTodayListItem")
            .contains("v-model:selected-date=\"businessDate\"")
            .contains("v-model:open=\"showCreateReservationDialog\"")
            .contains("v-model:selected-status=\"selectedStatus\"")
            .contains("label: '预约到店'")
            .contains("routeName: 'reservation-check-in'")
            .contains("label: '创建预约'")
            .contains("open-create-reservation")
            .contains("label: '预约排队'")
            .contains("routeName: 'reservation-arrived-to-queue'")
            .contains("label: '预约入座'")
            .contains("routeName: 'reservation-arrived-direct-seating'")
            .contains("createReservation")
            .contains("新增预约")
            .contains("StaffTimeWheelPicker")
            .contains("v-model=\"form.time\"")
            .contains("name=\"reservationTime\"")
            .contains("24小时制时间选择")
            .contains("HOUR_OPTIONS = Array.from({ length: 24 }")
            .contains("MINUTE_OPTIONS = Array.from({ length: 60 }")
            .contains("time-wheel-picker__frame")
            .contains("time-wheel-picker__column")
            .contains("桌号（可选）")
            .contains("今日预约")
            .contains("当日预约")
            .contains("reservation-today-list")
            .contains("placeholder=\"手机号\"")
            .contains("全部人数")
            .contains("重置")
            .contains("routeName: 'reservation-check-in'")
            .contains("routeName: 'reservation-arrived-direct-seating'")
            .contains("fetchMeApps")
            .contains("reservation.cancel")
            .contains("canCancelReservation")
            .contains(":can-cancel-reservation=\"canCancelReservation\"")
            .contains("cancelReservation")
            .contains("/cancel")
            .contains("cancel-requested")
            .contains("selectedDate")
            .contains("monthDays")
            .doesNotContain("name=\"reservationTime\" type=\"time\"")
            .doesNotContain("复制 ID")
            .doesNotContain("navigator.clipboard")
            .doesNotContain("取消预约需后端契约")
            .doesNotContain("ReservationCancellationPage")
            .doesNotContain("ReservationNoShowPage")
            .doesNotContain("queueTicketId")
            .doesNotContain("seatingId: request.seatingId")
            .doesNotContain("tableId: request.tableId");
    }

    @Test
    void staffUiV12DoesNotIntroduceForbiddenArtifacts() throws Exception {
        String source = readSources(List.of(
            Path.of("src", "components", "staff", "StaffBottomNav.vue"),
            Path.of("src", "components", "staff", "staffBottomNavItems.ts"),
            Path.of("src", "components", "staff-table", "TableResourcePicker.vue"),
            Path.of("src", "pages", "TableResourceListPage.vue"),
            Path.of("src", "api", "tableResourceApi.ts")
        ));

        assertThat(source)
            .doesNotContain("Queue Display")
            .doesNotContain("大屏")
            .doesNotContain("Reservation Calendar")
            .doesNotContain("ActionSheet")
            .doesNotContain("screen-overlay")
            .doesNotContain("Font Awesome")
            .doesNotContain("queue.skip")
            .doesNotContain("queue.rejoin")
            .doesNotContain("drag")
            .doesNotContain("drop");
    }

    private static void assertAppearsInOrder(String source, String... expectedFragments) {
        int cursor = -1;

        for (String expectedFragment : expectedFragments) {
            int nextIndex = source.indexOf(expectedFragment, cursor + 1);

            assertThat(nextIndex)
                .as("Expected `%s` to appear after index %s", expectedFragment, cursor)
                .isGreaterThan(cursor);
            cursor = nextIndex;
        }
    }

    private static String readSources(List<Path> paths) throws Exception {
        StringBuilder source = new StringBuilder();

        for (Path path : paths) {
            if (Files.exists(path)) {
                source.append(Files.readString(path)).append('\n');
            }
        }

        return source.toString();
    }
}
