package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TemporaryTableGroupUiValidationTest {

    @Test
    void staffSeatingEntrancesSupportTemporaryTableGroups() throws Exception {
        String picker = FrontendSourceSupport.readString(Path.of("src", "components", "staff-table", "TableResourcePicker.vue"));
        String reservationPage = FrontendSourceSupport.readString(Path.of("src", "pages", "ReservationArrivedDirectSeatingPage.vue"));
        String queuePage = FrontendSourceSupport.readString(Path.of("src", "pages", "SeatingFromCalledQueuePage.vue"));
        String walkInPage = FrontendSourceSupport.readString(Path.of("src", "pages", "WalkInDirectSeatingPage.vue"));
        String seatDialog = FrontendSourceSupport.readString(Path.of("src", "components", "reservation-workbench", "ReservationSeatDialog.vue"));
        String tablePage = FrontendSourceSupport.readString(Path.of("src", "pages", "TableResourceListPage.vue"));
        String reservationApi = FrontendSourceSupport.readString(Path.of("src", "api", "reservationArrivedDirectSeatingApi.ts"));
        String queueApi = FrontendSourceSupport.readString(Path.of("src", "api", "seatingFromCalledQueueApi.ts"));
        String walkInApi = FrontendSourceSupport.readString(Path.of("src", "api", "walkInDirectSeatingApi.ts"));
        String reservationTypes = FrontendSourceSupport.readString(Path.of("src", "types", "reservationArrivedDirectSeating.ts"));
        String queueTypes = FrontendSourceSupport.readString(Path.of("src", "types", "seatingFromCalledQueue.ts"));
        String walkInTypes = FrontendSourceSupport.readString(Path.of("src", "types", "walkInDirectSeating.ts"));
        String zhSource = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "zh-CN.ts"));

        assertThat(picker)
            .contains("selectedTemporaryTableIds")
            .contains("temporarySelectionEnabled")
            .contains("'select-temporary-tables'")
            .contains("'update:selection-mode'")
            .contains("showSelectionModeControls")
            .contains("selectionMode")
            .contains("toggleTemporaryTable")
            .contains("staffControls.tablePicker.temporaryMode")
            .contains("isTemporaryGroupMember")
            .contains("resourceUnavailableReasonText")
            .contains("staffControls.tablePicker.unavailable.temporaryGroupMember")
            .doesNotContain("`${statusLabel(resource.status)}，当前不可选`");

        assertThat(zhSource)
            .contains("temporaryMode: '临时组合'")
            .contains("temporaryGroupMember: '临时组占用'");

        assertThat(reservationPage + queuePage + walkInPage + seatDialog)
            .contains("temporaryTableIds: [] as string[]")
            .contains(":selected-temporary-table-ids")
            .contains("temporary-selection-enabled")
            .contains("@select-temporary-tables=\"selectTemporaryTables\"")
            .contains("TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED")
            .contains("temporaryTableIds:");

        assertThat(tablePage)
            .contains("temporaryGroupMode")
            .contains("selectedTemporaryTableIds")
            .contains("temporaryGroupRoute")
            .contains("temporaryTableIds: selectedTemporaryTableIds.value")
            .contains("displayableTableResources")
            .contains("isTemporaryGroupMember")
            .contains("resourceDisplayStatusLabel")
            .contains("resourceDisplayStatusClass")
            .contains("resource.selectable")
            .contains("临时组占用")
            .contains("临时桌组")
            .contains("组合桌台")
            .contains("加入组合")
            .contains("临时入桌")
            .doesNotContain("后台开台")
            .doesNotContain("员工选择")
            .doesNotContain("table-page-breadcrumb")
            .doesNotContain("statusLabel(resource.status) }}，{{ selectionReasonText(resource)");

        assertThat(reservationApi + queueApi + walkInApi + reservationTypes + queueTypes + walkInTypes)
            .contains("temporaryTableIds")
            .contains("temporaryTableIdsOrNull");
    }
}
