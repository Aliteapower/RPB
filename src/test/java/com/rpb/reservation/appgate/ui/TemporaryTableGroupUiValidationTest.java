package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TemporaryTableGroupUiValidationTest {

    @Test
    void staffSeatingEntrancesSupportTemporaryTableGroups() throws Exception {
        String picker = Files.readString(Path.of("src", "components", "staff-table", "TableResourcePicker.vue"));
        String reservationPage = Files.readString(Path.of("src", "pages", "ReservationArrivedDirectSeatingPage.vue"));
        String queuePage = Files.readString(Path.of("src", "pages", "SeatingFromCalledQueuePage.vue"));
        String walkInPage = Files.readString(Path.of("src", "pages", "WalkInDirectSeatingPage.vue"));
        String seatDialog = Files.readString(Path.of("src", "components", "reservation-workbench", "ReservationSeatDialog.vue"));
        String tablePage = Files.readString(Path.of("src", "pages", "TableResourceListPage.vue"));
        String reservationApi = Files.readString(Path.of("src", "api", "reservationArrivedDirectSeatingApi.ts"));
        String queueApi = Files.readString(Path.of("src", "api", "seatingFromCalledQueueApi.ts"));
        String walkInApi = Files.readString(Path.of("src", "api", "walkInDirectSeatingApi.ts"));
        String reservationTypes = Files.readString(Path.of("src", "types", "reservationArrivedDirectSeating.ts"));
        String queueTypes = Files.readString(Path.of("src", "types", "seatingFromCalledQueue.ts"));
        String walkInTypes = Files.readString(Path.of("src", "types", "walkInDirectSeating.ts"));

        assertThat(picker)
            .contains("selectedTemporaryTableIds")
            .contains("temporarySelectionEnabled")
            .contains("'select-temporary-tables'")
            .contains("selectionMode")
            .contains("toggleTemporaryTable")
            .contains("临时组合");

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
            .contains("临时桌组")
            .contains("组合桌台")
            .contains("加入组合")
            .contains("临时入桌");

        assertThat(reservationApi + queueApi + walkInApi + reservationTypes + queueTypes + walkInTypes)
            .contains("temporaryTableIds")
            .contains("temporaryTableIdsOrNull");
    }
}
