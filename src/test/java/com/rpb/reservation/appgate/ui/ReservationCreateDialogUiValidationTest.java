package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReservationCreateDialogUiValidationTest {

    @Test
    void createReservationDialogUsesFutureDefaultTimeAndHumanErrorMessage() throws Exception {
        Path dialogPath = Path.of("src", "components", "reservation-workbench", "CreateReservationDialog.vue");
        Path timePath = Path.of("src", "components", "reservation-workbench", "reservationCreateTime.ts");
        Path messagePath = Path.of("src", "utils", "reservationCreateMessages.ts");
        Path zhPath = Path.of("src", "i18n", "locales", "zh-CN.ts");

        assertThat(dialogPath).exists();
        assertThat(timePath).exists();
        assertThat(messagePath).exists();

        String dialogSource = readSource(dialogPath);
        String pickerSource = readSource(Path.of("src", "components", "staff-table", "TableResourcePicker.vue"));
        String timeSource = readSource(timePath);
        String messageSource = readSource(messagePath);
        String zhSource = readSource(zhPath);

        assertThat(dialogSource)
            .contains("fetchTableResources")
            .contains("fetchReservationTimeSlots")
            .contains("timeSlots")
            .contains("loadTimeSlots")
            .contains("availableTimeSlots")
            .contains("mealPeriodFilterOptions")
            .contains("filteredTimeSlots")
            .contains("selectedMealPeriodKey")
            .contains("selectMealPeriod")
            .contains("selectedTimeSlot")
            .contains("tableResourceOptions")
            .contains("loadTableResources")
            .contains("TableResourcePicker")
            .contains("temporary-selection-enabled")
            .contains("@select-temporary-tables=\"selectTemporaryTables\"")
            .contains("v-model:selection-mode=\"tablePickerSelectionMode\"")
            .contains(":show-selection-mode-controls=\"false\"")
            .contains(":available-only=\"true\"")
            .contains("saveTemporaryTableGroup")
            .contains("saveTemporaryGroupForReservation")
            .contains("toggleTemporaryGroupMode")
            .contains("tableIds: form.temporaryTableIds")
            .contains("businessDate: form.businessDate")
            .contains("fetchTableResources(props.storeId, {\n      includeGroups: true,")
            .contains("reservationWorkbench.createDialog.composeTables")
            .contains("reservationWorkbench.createDialog.saveGroup")
            .contains("tableId:")
            .contains("tableGroupId:")
            .contains("resource.resourceType === 'dining_table'")
            .contains("resource.resourceType === 'table_group'")
            .contains("form.tablePreference = `table_group:${result.tableGroupId}`")
            .contains("defaultFutureReservationDateTime")
            .contains("businessDate: form.businessDate")
            .contains("selectedTimeSlot.value?.startAt")
            .contains("timeSlots.value.filter(slot => slot.selectable)")
            .contains("v-for=\"option in mealPeriodFilterOptions\"")
            .contains("@click=\"selectMealPeriod(option.periodKey)\"")
            .contains("v-for=\"slot in filteredTimeSlots\"")
            .contains("reservationWorkbench.createDialog.allMealPeriods")
            .contains(":disabled=\"isSubmitting\"")
            .contains("formatReservationCreateErrorMessage")
            .contains("RESERVATION_START_IN_PAST")
            .contains("reservation.start_in_past")
            .doesNotContain("fetchTableResources(props.storeId, {\n      partySize: form.partySize")
            .doesNotContain(":party-size=\"form.partySize\"")
            .doesNotContain("temporaryTableIds: form.temporaryTableIds")
            .doesNotContain("v-for=\"slot in timeSlots\"")
            .doesNotContain(":disabled=\"!slot.selectable || isSubmitting\"")
            .doesNotContain("StaffTimeWheelPicker")
            .doesNotContain("toIsoInstant")
            .doesNotContain("{{ apiError.error.messageKey }}");
        assertAppearsInOrder(
            dialogSource,
            "reservation-create-table-picker__temporary-panel",
            "<TableResourcePicker"
        );

        assertThat(pickerSource)
            .contains("selectedArea")
            .contains("areaFilterOptions")
            .contains("selectArea")
            .contains("staffControls.tablePicker.areaFilter")
            .contains("staffControls.tablePicker.allAreas")
            .contains("areaName")
            .contains("resource.selectable || matchesRequiredResource(resource)")
            .contains("filterGroupResourceByArea")
            .contains("resourceAreaMatches")
            .contains("@click=\"selectArea(option.value)\"");

        assertThat(timeSource)
            .contains("export function defaultFutureReservationDateTime")
            .contains("export function isReservationStartInPast")
            .contains("roundUpToQuarterHour")
            .contains("30 * 60 * 1000");

        assertThat(messageSource)
            .contains("translate(")
            .contains("export function formatReservationCreateErrorMessage")
            .contains("reservation.start_in_past")
            .contains("reservation.time_slot_unavailable")
            .contains("reservation.invalid_phone_e164");
        assertThat(zhSource)
            .contains("startInPast: '预约开始时间不能早于当前时间")
            .contains("timeSlotUnavailable: '请选择门店餐段内的可预约时间")
            .contains("invalidPhoneE164: '手机号必须是 8 位新加坡号码")
            .contains("areaFilter: '桌台分区'")
            .contains("allAreas: '全部分区'");
    }

    @Test
    void oldReservationCreateRouteRedirectsToNewReservationWorkbenchEntry() throws Exception {
        Path routerPath = Path.of("src", "router", "index.ts");
        Path todayPagePath = Path.of("src", "pages", "ReservationTodayViewPage.vue");

        assertThat(routerPath).exists();
        assertThat(todayPagePath).exists();

        String routerSource = readSource(routerPath);
        String todayPageSource = readSource(todayPagePath);

        assertThat(routerSource)
            .contains("path: '/stores/:storeId/reservations/create'")
            .contains("name: 'reservation-create'")
            .contains("name: 'reservation-today-view'")
            .contains("query: { create: '1' }")
            .doesNotContain("component: ReservationCreatePage");

        assertThat(todayPageSource)
            .contains("route.query.create")
            .contains("openCreateReservationDialog")
            .contains("showCreateReservationDialog.value = true");
    }

    private static void assertAppearsInOrder(String source, String... expectedFragments) {
        int cursor = 0;
        for (String fragment : expectedFragments) {
            int index = source.indexOf(fragment, cursor);
            assertThat(index)
                .as("Expected fragment <%s> after offset %s", fragment, cursor)
                .isGreaterThanOrEqualTo(0);
            cursor = index + fragment.length();
        }
    }

    private static String readSource(Path path) throws Exception {
        return FrontendSourceSupport.readString(path)
            .replace("\r\n", "\n")
            .replace('\r', '\n');
    }
}
