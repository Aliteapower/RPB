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
        Path createDialogPath = Path.of("src", "components", "reservation-workbench", "CreateReservationDialog.vue");
        Path apiPath = Path.of("src", "api", "tableResourceApi.ts");
        Path typePath = Path.of("src", "types", "tableResource.ts");

        assertThat(Files.exists(tablePagePath)).isTrue();
        assertThat(Files.exists(pickerPath)).isTrue();
        assertThat(Files.exists(createDialogPath)).isTrue();
        assertThat(Files.exists(apiPath)).isTrue();
        assertThat(Files.exists(typePath)).isTrue();

        String tablePageSource = Files.readString(tablePagePath);
        String createDialogSource = Files.readString(createDialogPath);
        String source = tablePageSource
            + Files.readString(pickerPath)
            + createDialogSource
            + Files.readString(apiPath)
            + Files.readString(typePath);

        assertThat(source)
            .contains("fetchTableResources")
            .contains("/api/v1/stores/${storeId}/tables")
            .contains("resourceType: 'dining_table' | 'table_group'")
            .contains("areaName")
            .contains("memberTableCodes")
            .contains("groupedAreaResources")
            .contains("areaFilterOptions")
            .contains("selectedArea")
            .contains("selectArea")
            .contains("statusFilterCount")
            .contains("selectionReasonText")
            .contains("currentSeatingId")
            .contains("currentCleaningId")
            .contains("currentReservationId")
            .contains("businessDate")
            .contains("selectedBusinessDate")
            .contains("todayDateInput")
            .contains("预留")
            .contains("reserved")
            .contains("reservation_preassigned")
            .contains("startCleaning")
            .contains("completeCleaning")
            .contains("completeReservation")
            .contains("seatArrivedReservation")
            .contains("seatCalledQueueTicket")
            .contains("canSeatWalkInResource")
            .contains("getReservationCalendarSummary")
            .contains("createTableActionIdempotencyKey")
            .contains("walkInDirectSeatingRoute")
            .contains("ReservationMonthCalendar")
            .contains("reservationCounts")
            .contains("preassignedReservationId")
            .contains("preassignedReservationCode")
            .contains("preassignedCustomerName")
            .contains("preassignedPhoneMasked")
            .contains("preassignedCustomerText")
            .contains("preassignedQueueTicketStatus")
            .contains("预约指定")
            .contains("预约入桌")
            .contains("叫号入桌")
            .contains("桌台分区")
            .contains("全部分区")
            .contains("状态：")
            .contains("入桌")
            .contains("换桌")
            .contains("清台")
            .contains("isCleaningWorkflowResource")
            .contains("isClearableOccupiedResource")
            .contains("完成清台")
            .contains("availableOnly")
            .contains("桌台分组")
            .contains("table-page__resource-actions")
            .contains("table-page__area-filter")
            .contains("table-page__area-section")
            .contains("table-page__resource-card")
            .contains("table-page__resource-badge")
            .contains("table-page__resource-meta")
            .contains("groupedTableResources")
            .contains("areaTitle")
            .contains("select-table")
            .contains("select-table-group")
            .contains("暂无桌台，请先在后台配置桌台。")
            .doesNotContain("resource.selectionDisabledReason || '当前不可选'")
            .doesNotContain("结桌")
            .doesNotContain("resource.status = '")
            .doesNotContain("resource.status = \"")
            .doesNotContain("mock")
            .doesNotContain("fake");

        assertThat(tablePageSource)
            .contains("v-model:selected-date=\"selectedBusinessDate\"")
            .contains("calendar-label=\"桌台日历\"")
            .contains(":reservation-counts=\"reservationCounts\"")
            .contains("@visible-month-changed=\"handleVisibleMonthChanged\"")
            .contains("businessDate: selectedBusinessDate.value")
            .contains("isSelectedBusinessDateToday.value && resource.selectable")
            .contains("v-if=\"canSeatWalkInResource(resource)\"")
            .contains("isSelectedBusinessDateToday.value &&")
            .contains("@click=\"selectStatus(item.key)\"")
            .contains(":aria-pressed=\"selectedStatus === item.key\"")
            .doesNotContain("v-if=\"resource.selectable\"")
            .doesNotContain("class=\"status-options\"")
            .doesNotContain("business-date-field")
            .doesNotContain("name=\"businessDate\" type=\"date\"");
        assertThat(tablePageSource)
            .contains(".summary-row__item--available {\n  background: #ecfdf5;\n  border-color: #86efac;")
            .contains(".summary-row__item--reserved {\n  background: #fef3c7;\n  border-color: #f59e0b;")
            .contains(".summary-row__item--occupied {\n  background: #eef5ff;\n  border-color: #93c5fd;")
            .contains(".summary-row__item--cleaning {\n  background: #fff7ed;\n  border-color: #fdba74;")
            .contains(".summary-row__item--active {\n  background: #f4f0ff;\n  border-color: #c4b5fd;");
        assertThat(createDialogSource)
            .contains("businessDate: form.businessDate");
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
        Path seatDialogPath = Path.of("src", "components", "reservation-workbench", "ReservationSeatDialog.vue");
        Path todayListPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListPanel.vue");
        Path todayListItemPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListItem.vue");
        Path timePickerPath = Path.of("src", "components", "staff", "StaffTimeWheelPicker.vue");
        Path cancelApiPath = Path.of("src", "api", "reservationCancelApi.ts");
        Path calendarSummaryApiPath = Path.of("src", "api", "reservationCalendarSummaryApi.ts");
        Path calendarSummaryTypePath = Path.of("src", "types", "reservationCalendarSummary.ts");

        assertThat(Files.exists(actionsPath)).isTrue();
        assertThat(Files.exists(calendarPath)).isTrue();
        assertThat(Files.exists(createDialogPath)).isTrue();
        assertThat(Files.exists(seatDialogPath)).isTrue();
        assertThat(Files.exists(todayListPath)).isTrue();
        assertThat(Files.exists(todayListItemPath)).isTrue();
        assertThat(Files.exists(timePickerPath)).isTrue();
        assertThat(Files.exists(cancelApiPath)).isTrue();
        assertThat(Files.exists(calendarSummaryApiPath)).isTrue();
        assertThat(Files.exists(calendarSummaryTypePath)).isTrue();

        String source = Files.readString(pagePath)
            + Files.readString(actionsPath)
            + Files.readString(calendarPath)
            + Files.readString(createDialogPath)
            + Files.readString(seatDialogPath)
            + Files.readString(todayListPath)
            + Files.readString(todayListItemPath)
            + Files.readString(timePickerPath)
            + Files.readString(cancelApiPath)
            + Files.readString(calendarSummaryApiPath)
            + Files.readString(calendarSummaryTypePath);

        assertThat(source)
            .contains("ReservationQuickActionPanel")
            .contains("ReservationMonthCalendar")
            .contains("CreateReservationDialog")
            .contains("ReservationTodayListPanel")
            .contains("ReservationTodayListItem")
            .contains("v-model:selected-date=\"businessDate\"")
            .contains("v-model:open=\"showCreateReservationDialog\"")
            .contains("v-model:open=\"showSeatDialog\"")
            .contains("v-model:selected-status=\"selectedStatus\"")
            .contains("label: '预约到店'")
            .contains("action: 'show-confirmed-reservations'")
            .contains("label: '创建预约'")
            .contains("open-create-reservation")
            .contains("label: '预约排队'")
            .contains("routeName: 'reservation-arrived-to-queue'")
            .contains("label: '预约入座'")
            .contains("action: 'show-arrived-reservations'")
            .contains("show-confirmed-reservations")
            .contains("show-arrived-reservations")
            .contains("createReservation")
            .contains("新增预约")
            .contains("ReservationSeatDialog")
            .contains("选择桌号（入桌）")
            .contains("TableResourcePicker")
            .contains("seatArrivedReservation")
            .contains("select-table")
            .contains("select-table-group")
            .contains("checkInReservation")
            .contains("check-in-requested")
            .contains("seat-requested")
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
            .contains("label: '已预约'")
            .contains("reservation-today-list")
            .contains("placeholder=\"手机号\"")
            .contains("全部人数")
            .contains("重置")
            .contains("fetchMeApps")
            .contains("reservation.cancel")
            .contains("canCancelReservation")
            .contains(":can-cancel-reservation=\"canCancelReservation\"")
            .contains("cancelReservation")
            .contains("/cancel")
            .contains("cancel-requested")
            .contains("selectedDate")
            .contains("monthDays")
            .contains("getReservationCalendarSummary")
            .contains("/api/v1/stores/${storeId}/reservations/calendar-summary")
            .contains("ReservationCalendarSummaryResponse")
            .contains("reservationCounts")
            .contains(":reservation-counts=\"reservationCounts\"")
            .contains("visibleMonthKey")
            .contains("loadCalendarSummary")
            .contains("reservation-calendar__reservation-count")
            .contains("fetchTableResources")
            .contains("tableResourceOptions")
            .contains("loadTableResources")
            .contains("tableId:")
            .contains("tableGroupId:")
            .contains("storeTodayDate")
            .contains("canCreateReservationForSelectedDate")
            .contains("canRunCurrentDayActions")
            .contains(":min-date=\"storeTodayDate\"")
            .contains(":can-create-reservation-for-selected-date=\"canCreateReservationForSelectedDate\"")
            .contains(":can-run-current-day-actions=\"canRunCurrentDayActions\"")
            .contains("assignedResourceCode")
            .contains("queueTicketId")
            .contains("queueTicketNumber")
            .contains("queueTicketStatus")
            .contains("isBeforeMinDate")
            .contains(":min=\"minDate\"")
            .contains("is-past")
            .contains("aria-disabled")
            .contains("仅当日预约可以操作")
            .contains("aria-label")
            .doesNotContain("routeName: 'reservation-check-in'")
            .doesNotContain("routeName: 'reservation-arrived-direct-seating'")
            .doesNotContain("name=\"reservationTime\" type=\"time\"")
            .doesNotContain("label: '已确认'")
            .doesNotContain("复制 ID")
            .doesNotContain("navigator.clipboard")
            .doesNotContain("取消预约需后端契约")
            .doesNotContain("ReservationCancellationPage")
            .doesNotContain("ReservationNoShowPage")
            .doesNotContain("seatingId: request.seatingId");
    }

    @Test
    void reservationTodayListItemShowsTimeRangeWithoutRepeatingSelectedDate() throws Exception {
        Path todayListItemPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListItem.vue");

        assertThat(todayListItemPath).exists();

        String todayListItem = Files.readString(todayListItemPath);

        assertThat(todayListItem)
            .contains("function formatStoreTime")
            .contains("hour: '2-digit'")
            .contains("minute: '2-digit'")
            .doesNotContain("month: '2-digit'")
            .doesNotContain("day: '2-digit'")
            .doesNotContain("part('month')")
            .doesNotContain("part('day')");
    }

    @Test
    void tablePageConnectsSwitchTableToTableSwitchApi() throws Exception {
        Path tablePagePath = Path.of("src", "pages", "TableResourceListPage.vue");
        Path pickerPath = Path.of("src", "components", "staff-table", "TableResourcePicker.vue");
        Path todayListPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListPanel.vue");
        Path todayListItemPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListItem.vue");
        Path switchDialogPath = Path.of("src", "components", "reservation-workbench", "ReservationTableSwitchDialog.vue");
        Path switchApiPath = Path.of("src", "api", "tableSwitchApi.ts");
        Path switchTypePath = Path.of("src", "types", "tableSwitch.ts");
        Path tableResourceTypePath = Path.of("src", "types", "tableResource.ts");

        assertThat(tablePagePath).exists();
        assertThat(pickerPath).exists();
        assertThat(todayListPath).exists();
        assertThat(todayListItemPath).exists();
        assertThat(switchDialogPath).exists();
        assertThat(switchApiPath).exists();
        assertThat(switchTypePath).exists();
        assertThat(tableResourceTypePath).exists();

        String tablePage = Files.readString(tablePagePath);
        String todayList = Files.readString(todayListPath) + Files.readString(todayListItemPath);
        String source = tablePage
            + Files.readString(pickerPath)
            + Files.readString(switchDialogPath)
            + Files.readString(switchApiPath)
            + Files.readString(switchTypePath)
            + Files.readString(tableResourceTypePath);

        assertThat(source)
            .contains("ReservationTableSwitchDialog")
            .contains("v-model:open=\"showTableSwitchDialog\"")
            .contains("@switched=\"handleTableSwitched\"")
            .contains("canSwitchResource")
            .contains("openTableSwitchDialog")
            .contains("currentResourceCode")
            .contains("currentSeatingId")
            .contains("currentReservationId")
            .contains(":available-only=\"true\"")
            .contains(":party-size=\"null\"")
            .contains(":business-date=\"pickerBusinessDate\"")
            .contains("换桌")
            .contains("选择桌号（换桌）")
            .contains("TableResourcePicker")
            .contains("switchTable")
            .contains("/api/v1/stores/${encodeURIComponent(storeId)}/seatings/${encodeURIComponent(seatingId)}/table-switch")
            .contains("'Idempotency-Key': idempotencyKey")
            .contains("createTableSwitchIdempotencyKey")
            .contains("seatingId?: string | null")
            .contains("loadCalendarSummary()")
            .doesNotContain("换桌需后端换桌契约")
            .doesNotContain("navigator.clipboard")
            .doesNotContain("routeName: 'reservation-arrived-direct-seating'")
            .doesNotContain("mock")
            .doesNotContain("fake");

        assertThat(todayList)
            .doesNotContain("switch-table-requested")
            .doesNotContain("showSwitchTable")
            .doesNotContain("canSwitchTable");
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
