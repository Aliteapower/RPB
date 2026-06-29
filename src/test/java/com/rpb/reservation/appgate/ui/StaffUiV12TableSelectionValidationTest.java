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

        String itemsSource = readSource(itemsPath);
        String componentSource = readSource(componentPath);
        String routerSource = readSource(routerPath);

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
        Path dateSwitcherPath = Path.of("src", "components", "staff", "StaffBusinessDateSwitcher.vue");
        Path pickerPath = Path.of("src", "components", "staff-table", "TableResourcePicker.vue");
        Path createDialogPath = Path.of("src", "components", "reservation-workbench", "CreateReservationDialog.vue");
        Path apiPath = Path.of("src", "api", "tableResourceApi.ts");
        Path seatingApiPath = Path.of("src", "api", "reservationArrivedDirectSeatingApi.ts");
        Path typePath = Path.of("src", "types", "tableResource.ts");

        assertThat(Files.exists(tablePagePath)).isTrue();
        assertThat(Files.exists(dateSwitcherPath)).isTrue();
        assertThat(Files.exists(pickerPath)).isTrue();
        assertThat(Files.exists(createDialogPath)).isTrue();
        assertThat(Files.exists(apiPath)).isTrue();
        assertThat(Files.exists(seatingApiPath)).isTrue();
        assertThat(Files.exists(typePath)).isTrue();

        String tablePageSource = readSource(tablePagePath);
        String dateSwitcherSource = readSource(dateSwitcherPath);
        String createDialogSource = readSource(createDialogPath);
        String source = tablePageSource
            + dateSwitcherSource
            + readSource(pickerPath)
            + createDialogSource
            + readSource(apiPath)
            + readSource(seatingApiPath)
            + readSource(typePath);

        assertThat(source)
            .contains("fetchTableResources")
            .contains("/api/v1/stores/${storeId}/tables")
            .contains("/api/v1/stores/${encodeURIComponent(storeId)}/reservations/${encodeURIComponent(reservationId)}/seating/check-in-direct")
            .contains("resourceType: 'dining_table' | 'table_group'")
            .contains("areaName")
            .contains("memberTableCodes")
            .contains("groupedAreaResources")
            .contains("areaFilterOptions")
            .contains("selectedArea")
            .contains("selectArea")
            .contains("statusFilterCount")
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
            .contains("checkInAndSeatConfirmedReservation")
            .contains("seatCalledQueueTicket")
            .contains("canSeatWalkInResource")
            .contains("getReservationCalendarSummary")
            .contains("createTableActionIdempotencyKey")
            .contains("walkInDirectSeatingRoute")
            .contains("temporaryGroupName")
            .contains("saveTemporaryTableGroup")
            .contains("dissolveTemporaryTableGroup")
            .contains("保存分组")
            .contains("解散")
            .contains("temporary_group_member")
            .contains("临时组占用")
            .contains("resourceDisplayStatusLabel")
            .contains("resourceDisplayStatusClass")
            .contains("displayableTableResources")
            .contains("isTemporaryGroupMember")
            .contains("resourceUnavailableReasonText")
            .contains("StaffBusinessDateSwitcher")
            .contains("ReservationMonthCalendar")
            .contains("calendarOpen")
            .contains("切换日期")
            .contains("回到今日")
            .contains("改日期")
            .contains("未来日期")
            .contains("营业中")
            .contains("reservationCounts")
            .contains("preassignedReservationId")
            .contains("preassignedReservationCode")
            .contains("preassignedCustomerName")
            .contains("preassignedPhoneMasked")
            .contains("preassignedCustomerText")
            .contains("preassignedQueueTicketStatus")
            .contains("预约指定")
            .contains("预约入桌")
            .contains("到店入桌")
            .contains("seatAssignedReservationActionText")
            .contains("resource.preassignedReservationStatus === 'confirmed'")
            .contains("叫号入桌")
            .contains("桌台分区")
            .contains("全部分区")
            .contains("入桌")
            .contains("换桌")
            .contains("清台")
            .contains("isCleaningWorkflowResource")
            .contains("isClearableOccupiedResource")
            .contains("完成清台")
            .contains("availableOnly")
            .contains("桌台分组")
            .contains("table-page__resource-actions")
            .contains("table-page__resource-action-pair")
            .contains("table-page__area-filter")
            .contains("table-page__area-section")
            .contains("table-page__resource-card")
            .contains("table-area-party-filter")
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
            .contains("StaffBusinessDateSwitcher")
            .contains("v-model:selected-date=\"selectedBusinessDate\"")
            .contains("calendar-label=\"桌台日历\"")
            .contains(":today-date=\"currentBusinessDate\"")
            .contains(":reservation-counts=\"reservationCounts\"")
            .contains("@visible-month-changed=\"handleVisibleMonthChanged\"")
            .contains("businessDate: selectedBusinessDate.value")
            .contains("canSaveTemporaryGroup")
            .contains("temporaryGroupName.value.trim().length > 0")
            .contains("selectedTemporaryTableIds.value = []")
            .contains("displayableTableResources.value.filter(resource => resource.selectable)")
            .contains("resource.resourceType === 'dining_table'")
            .contains("(resource.selectable || isTemporaryTableSelected(resource))")
            .contains("v-if=\"canSeatWalkInResource(resource)\"")
            .contains("isSelectedBusinessDateToday.value &&")
            .contains("@click=\"selectStatus(item.key)\"")
            .contains(":aria-pressed=\"selectedStatus === item.key\"")
            .doesNotContain("resource.resourceType === 'dining_table' &&\n    isSelectedBusinessDateToday.value &&")
            .doesNotContain(":disabled=\"!isSelectedBusinessDateToday\"")
            .doesNotContain("v-if=\"resource.selectable\"")
            .doesNotContain("状态：{{ resourceDisplayStatusLabel(resource) }}，{{ selectionReasonText(resource) }}")
            .doesNotContain("statusLabel(resource.status) }}，{{ selectionReasonText(resource)")
            .doesNotContain("class=\"status-options\"")
            .doesNotContain("business-date-field")
            .doesNotContain("name=\"businessDate\" type=\"date\"")
            .doesNotContain("<ReservationMonthCalendar");
        assertThat(dateSwitcherSource)
            .contains("ReservationMonthCalendar")
            .contains("v-if=\"calendarOpen\"")
            .contains(":min-date=\"todayDate\"")
            .contains(":selected-date=\"selectedDate\"")
            .contains("@update:selected-date=\"selectDate\"")
            .contains("emit('update:selectedDate', value)")
            .doesNotContain("fetchTableResources")
            .doesNotContain("getReservationTodayView")
            .doesNotContain("saveTemporaryTableGroup");
        assertAppearsInOrder(
            tablePageSource,
            "<StaffBusinessDateSwitcher",
            "<section class=\"summary-row\" aria-label=\"桌台概览\">",
            "<section class=\"temporary-group-panel\" aria-label=\"临时桌组\">",
            "<section v-if=\"groupedAreaResources.length\" class=\"table-page__area-list\" aria-label=\"桌台分区\">",
            "class=\"table-area-party-filter\""
        );
        assertThat(tablePageSource)
            .contains(".table-page__resource-grid {\n  display: grid;\n  gap: 10px;\n  grid-template-columns: repeat(4, minmax(0, 1fr));")
            .contains(".table-page__resource-grid--groups {\n  grid-template-columns: repeat(3, minmax(0, 1fr));")
            .doesNotContain("class=\"filter-panel\"")
            .contains(".summary-row__item--available {\n  background: #ecfdf5;\n  border-color: #86efac;")
            .contains(".summary-row__item--reserved {\n  background: #fef3c7;\n  border-color: #f59e0b;")
            .contains(".summary-row__item--occupied {\n  background: #eef5ff;\n  border-color: #93c5fd;")
            .contains(".summary-row__item--cleaning {\n  background: #fff7ed;\n  border-color: #fdba74;")
            .contains(".summary-row__item--active {\n  background: #f4f0ff;\n  border-color: #c4b5fd;");
        assertThat(createDialogSource)
            .contains("businessDate: form.businessDate")
            .contains("TableResourcePicker")
            .contains("temporary-selection-enabled")
            .contains("v-model:selection-mode=\"tablePickerSelectionMode\"")
            .contains(":show-selection-mode-controls=\"false\"")
            .contains("saveTemporaryTableGroup")
            .contains("toggleTemporaryGroupMode")
            .contains("保存分组")
            .contains("form.tablePreference = `table_group:${result.tableGroupId}`")
            .contains("tableIds: form.temporaryTableIds")
            .contains(":business-date=\"form.businessDate\"")
            .doesNotContain("temporaryTableIds: form.temporaryTableIds")
            .doesNotContain("reservation-create-table-picker__grid");
    }

    @Test
    void tablePageSupportsVisibleRangeBulkCleaningActions() throws Exception {
        String tablePageSource = readSource(Path.of("src", "pages", "TableResourceListPage.vue"));

        assertThat(tablePageSource)
            .contains("bulkStartCleaningResources")
            .contains("bulkCompleteCleaningResources")
            .contains("bulkCleaningMode")
            .contains("bulkCleaningProgressText")
            .contains("startVisibleResourcesCleaning")
            .contains("completeVisibleResourcesCleaning")
            .contains("runBulkCleaningAction")
            .contains("startCleaningForResource(currentStoreId, resource)")
            .contains("completeCleaningForResource(currentStoreId, resource)")
            .contains("createBulkCleaningLocalError")
            .contains("cleaning.bulk_partial_failed")
            .contains("批量清台")
            .contains("批量完成")
            .contains("当前筛选")
            .contains("table-page__bulk-actions")
            .contains("table-page__bulk-action")
            .contains(":disabled=\"!canRunBulkStartCleaning\"")
            .contains(":disabled=\"!canRunBulkCompleteCleaning\"");
    }

    @Test
    void seatingFormsUseTableResourcePickerWithoutChangingSubmitPayloadShape() throws Exception {
        Path walkInDirectPath = Path.of("src", "pages", "WalkInDirectSeatingPage.vue");
        Path reservationDirectPath = Path.of("src", "pages", "ReservationArrivedDirectSeatingPage.vue");
        Path queueSeatingPath = Path.of("src", "pages", "SeatingFromCalledQueuePage.vue");
        Path reservationSeatDialogPath = Path.of("src", "components", "reservation-workbench", "ReservationSeatDialog.vue");
        String source = readSources(List.of(
            walkInDirectPath,
            reservationDirectPath,
            queueSeatingPath,
            reservationSeatDialogPath
        ));
        String walkInDirectSource = readSource(walkInDirectPath);
        String reservationDirectSource = readSource(reservationDirectPath);
        String queueSeatingSource = readSource(queueSeatingPath);
        String reservationSeatDialogSource = readSource(reservationSeatDialogPath);

        assertThat(source)
            .contains("TableResourcePicker")
            .contains("function selectTable(tableId: string)")
            .contains("function selectTableGroup(tableGroupId: string)")
            .contains("tableId: optionalValue(form.tableId)")
            .contains("tableGroupId: optionalValue(form.tableGroupId)");

        assertThat(walkInDirectSource)
            .contains(":available-only=\"true\"")
            .contains(":party-size=\"null\"")
            .doesNotContain(":party-size=\"form.partySize\"");
        assertThat(reservationDirectSource)
            .contains(":available-only=\"true\"");
        assertThat(queueSeatingSource)
            .contains(":available-only=\"true\"");
        assertThat(reservationSeatDialogSource)
            .contains(":available-only=\"true\"")
            .contains(":party-size=\"null\"")
            .doesNotContain(":party-size=\"item?.partySize ?? null\"");
    }

    @Test
    void tableResourcePickerShowsSavedTemporaryGroupsInTemporaryMode() throws Exception {
        String picker = readSource(Path.of("src", "components", "staff-table", "TableResourcePicker.vue"));

        assertThat(picker)
            .contains("temporaryGroupResources")
            .contains("visibleGroupResources")
            .contains("showSelectionModeControls")
            .contains("'update:selection-mode'")
            .contains("resource.groupType === 'temporary'")
            .contains("selectionMode.value === 'temporary' && resource.resourceType === 'table_group' && resource.groupType === 'temporary'")
            .contains("emit('select-table-group', resource.resourceId)")
            .contains("v-for=\"resource in visibleGroupResources\"");
    }

    @Test
    void walkInDirectSeatingPageUsesOneTapPickerFlowWithoutLegacyManualFields() throws Exception {
        String page = readSource(Path.of("src", "pages", "WalkInDirectSeatingPage.vue"));

        assertThat(page)
            .contains("StaffHomeTopBar")
            .contains("StaffHomeWorkflowStrip")
            .contains("StaffBottomNav")
            .contains("staff-workbench-shell")
            .contains("walk-in-direct-workbench-body")
            .contains("useRouter")
            .contains("const { currentBusinessDate, currentTimeText } = useCurrentClock()")
            .contains("StaffGuestContactLookup")
            .contains("TableResourcePicker")
            .contains("const businessDate = computed(() => currentBusinessDate.value)")
            .contains(":business-date=\"businessDate\"")
            .contains("temporary-selection-enabled")
            .contains("@select-table=\"selectTable\"")
            .contains("@select-table-group=\"selectTableGroup\"")
            .contains("@select-temporary-tables=\"selectTemporaryTables\"")
            .contains("{{ isSubmitting ? '入座中...' : '确认入座' }}")
            .contains("tableResourceListRoute")
            .contains("name: 'table-resource-list'")
            .contains("router.push(tableResourceListRoute.value)");

        assertThat(page)
            .doesNotContain("手动填写资源 ID")
            .doesNotContain("调整信息")
            .doesNotContain("overrideReasonCode")
            .doesNotContain("overrideNote")
            .doesNotContain("lastIdempotencyKey")
            .doesNotContain("idempotency-key")
            .doesNotContain("business-date-badge")
            .doesNotContain("displayedBusinessDate")
            .doesNotContain("开始清台")
            .doesNotContain("cleaningRoute")
            .doesNotContain("success-panel")
            .doesNotContain("散客入座成功")
            .doesNotContain("返回员工首页")
            .doesNotContain("page-shell")
            .doesNotContain("入座记录 ID")
            .doesNotContain("散客记录 ID");
    }

    @Test
    void staffHomeTopBarKeepsDatePropCompatibilityButDoesNotRenderBusinessDateChip() throws Exception {
        String topBar = readSource(Path.of("src", "components", "staff-home", "StaffHomeTopBar.vue"));

        assertThat(topBar)
            .contains("businessDate?: string | null")
            .contains("<slot name=\"action\" />")
            .contains("aria-label=\"门店和应用状态\"")
            .doesNotContain("displayBusinessDate")
            .doesNotContain("aria-label=\"营业日期\"")
            .doesNotContain("topbar-business-date")
            .doesNotContain("<span>营业日期</span>");

        assertThat(readSource(Path.of("src", "components", "staff-home", "useCurrentClock.ts")))
            .contains("currentBusinessDate")
            .contains("formatBusinessDate")
            .contains("timeZone = 'Asia/Singapore'");

        assertThat(readSource(Path.of("src", "pages", "StoreStaffHomePage.vue")))
            .contains(":business-date=\"displayedBusinessDate\"")
            .contains("getStaffHomeOverview")
            .contains("return '首页'");
        assertThat(readSource(Path.of("src", "pages", "WalkInQueuePage.vue")))
            .contains(":business-date=\"currentBusinessDate\"");
        assertThat(readSource(Path.of("src", "pages", "QueueTicketListPage.vue")))
            .contains(":business-date=\"currentBusinessDate\"")
            .contains("营业日期 {{ currentBusinessDate }}")
            .contains("reservationQueueEntry.value ? '排队' : '暂无应用'");
        assertThat(readSource(Path.of("src", "pages", "SeatingFromCalledQueuePage.vue")))
            .contains(":business-date=\"currentBusinessDate\"");
        assertThat(readSource(Path.of("src", "pages", "WalkInDirectSeatingPage.vue")))
            .contains(":business-date=\"businessDate\"")
            .doesNotContain("business-date-badge");
        assertThat(readSource(Path.of("src", "pages", "ReservationArrivedToQueuePage.vue")))
            .contains(":business-date=\"displayedBusinessDate\"")
            .doesNotContain("business-date-badge");
        assertThat(readSource(Path.of("src", "pages", "ReservationTodayViewPage.vue")))
            .contains("StaffHomeTopBar")
            .contains(":business-date=\"displayedBusinessDate\"")
            .doesNotContain("reservation-workbench__header");
        assertThat(readSource(Path.of("src", "pages", "TableResourceListPage.vue")))
            .contains("StaffHomeTopBar")
            .contains(":business-date=\"selectedBusinessDate\"")
            .doesNotContain("class=\"top-bar\"");
    }

    @Test
    void reservationAndQueuePagesReserveBottomNavigationSpace() throws Exception {
        Path staffWorkbenchStylePath = Path.of("src", "styles", "staffWorkbench.css");

        assertThat(staffWorkbenchStylePath).exists();

        String mainSource = readSource(Path.of("src", "main.ts"));
        String staffWorkbenchStyle = readSource(staffWorkbenchStylePath);

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
            String source = readSource(path);

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
        Path dateSwitcherPath = Path.of("src", "components", "staff", "StaffBusinessDateSwitcher.vue");
        Path calendarPath = Path.of("src", "components", "reservation-workbench", "ReservationMonthCalendar.vue");
        Path createDialogPath = Path.of("src", "components", "reservation-workbench", "CreateReservationDialog.vue");
        Path seatDialogPath = Path.of("src", "components", "reservation-workbench", "ReservationSeatDialog.vue");
        Path todayListPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListPanel.vue");
        Path todayListItemPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListItem.vue");
        Path cancelApiPath = Path.of("src", "api", "reservationCancelApi.ts");
        Path calendarSummaryApiPath = Path.of("src", "api", "reservationCalendarSummaryApi.ts");
        Path calendarSummaryTypePath = Path.of("src", "types", "reservationCalendarSummary.ts");

        assertThat(Files.exists(actionsPath)).isTrue();
        assertThat(Files.exists(dateSwitcherPath)).isTrue();
        assertThat(Files.exists(calendarPath)).isTrue();
        assertThat(Files.exists(createDialogPath)).isTrue();
        assertThat(Files.exists(seatDialogPath)).isTrue();
        assertThat(Files.exists(todayListPath)).isTrue();
        assertThat(Files.exists(todayListItemPath)).isTrue();
        assertThat(Files.exists(cancelApiPath)).isTrue();
        assertThat(Files.exists(calendarSummaryApiPath)).isTrue();
        assertThat(Files.exists(calendarSummaryTypePath)).isTrue();

        String pageSource = readSource(pagePath);
        String actionPanelSource = readSource(actionsPath);
        String dateSwitcherSource = readSource(dateSwitcherPath);
        String source = pageSource
            + actionPanelSource
            + dateSwitcherSource
            + readSource(calendarPath)
            + readSource(createDialogPath)
            + readSource(seatDialogPath)
            + readSource(todayListPath)
            + readSource(todayListItemPath)
            + readSource(cancelApiPath)
            + readSource(calendarSummaryApiPath)
            + readSource(calendarSummaryTypePath);

        assertThat(source)
            .contains("ReservationQuickActionPanel")
            .contains("StaffBusinessDateSwitcher")
            .contains("ReservationMonthCalendar")
            .contains("CreateReservationDialog")
            .contains("ReservationTodayListPanel")
            .contains("ReservationTodayListItem")
            .contains("v-model:selected-date=\"businessDate\"")
            .contains("v-model:open=\"showCreateReservationDialog\"")
            .contains("v-model:open=\"showSeatDialog\"")
            .contains("v-model:selected-status=\"selectedStatus\"")
            .contains("label: '创建预约'")
            .contains("open-create-reservation")
            .contains("label: '现场取号'")
            .contains("routeName: 'walk-in-queue'")
            .contains("label: '预约转排队'")
            .contains("routeName: 'reservation-arrived-to-queue'")
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
            .contains("fetchReservationTimeSlots")
            .contains("timeSlots")
            .contains("mealPeriodFilterOptions")
            .contains("filteredTimeSlots")
            .contains("selectedMealPeriodKey")
            .contains("selectMealPeriod")
            .contains("selectedTimeSlot")
            .contains("reservation-create-meal-period-filter")
            .contains("reservation-create-time-slots")
            .contains("v-for=\"slot in filteredTimeSlots\"")
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
            .contains(":today-date=\"storeTodayDate\"")
            .contains("visibleMonthKey")
            .contains("loadCalendarSummary")
            .contains("切换日期")
            .contains("回到今日")
            .contains("改日期")
            .contains("未来日期")
            .contains("营业中")
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
            .doesNotContain("label: '预约到店'")
            .doesNotContain("label: '预约入座'")
            .doesNotContain("show-confirmed-reservations")
            .doesNotContain("show-arrived-reservations")
            .doesNotContain("name=\"reservationTime\" type=\"time\"")
            .doesNotContain("label: '已确认'")
            .doesNotContain("复制 ID")
            .doesNotContain("navigator.clipboard")
            .doesNotContain("取消预约需后端契约")
            .doesNotContain("ReservationCancellationPage")
            .doesNotContain("ReservationNoShowPage")
            .doesNotContain("seatingId: request.seatingId");

        assertThat(pageSource)
            .contains("StaffBusinessDateSwitcher")
            .contains("v-model:selected-date=\"businessDate\"")
            .contains(":today-date=\"storeTodayDate\"")
            .contains(":reservation-counts=\"reservationCounts\"")
            .contains("@visible-month-changed=\"handleVisibleMonthChanged\"")
            .doesNotContain("<ReservationMonthCalendar");
        assertThat(dateSwitcherSource)
            .contains("ReservationMonthCalendar")
            .contains("v-if=\"calendarOpen\"")
            .contains("dateTone")
            .contains("return 'future'")
            .contains("return 'today'")
            .doesNotContain("fetchTableResources")
            .doesNotContain("getReservationTodayView");
        assertAppearsInOrder(
            pageSource,
            "<StaffBusinessDateSwitcher",
            "<ReservationQuickActionPanel",
            "<ReservationTodayListPanel"
        );
        assertThat(actionPanelSource)
            .contains("grid-template-columns: repeat(3, minmax(0, 1fr))")
            .doesNotContain("<small>")
            .doesNotContain("确认预约客人已到店")
            .doesNotContain("登记新的门店预约")
            .doesNotContain("已到店预约进入排队")
            .doesNotContain("为已到店预约安排桌台");
    }

    @Test
    void reservationTodayListItemShowsTimeRangeWithoutRepeatingSelectedDate() throws Exception {
        Path todayListItemPath = Path.of("src", "components", "reservation-workbench", "ReservationTodayListItem.vue");

        assertThat(todayListItemPath).exists();

        String todayListItem = readSource(todayListItemPath);

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

        String tablePage = readSource(tablePagePath);
        String todayList = readSource(todayListPath) + readSource(todayListItemPath);
        String source = tablePage
            + readSource(pickerPath)
            + readSource(switchDialogPath)
            + readSource(switchApiPath)
            + readSource(switchTypePath)
            + readSource(tableResourceTypePath);

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
                source.append(readSource(path)).append('\n');
            }
        }

        return source.toString();
    }

    private static String readSource(Path path) throws Exception {
        return Files.readString(path)
            .replace("\r\n", "\n")
            .replace('\r', '\n');
    }
}
