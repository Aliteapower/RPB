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

        assertThat(dialogPath).exists();
        assertThat(timePath).exists();
        assertThat(messagePath).exists();

        String dialogSource = Files.readString(dialogPath);
        String timeSource = Files.readString(timePath);
        String messageSource = Files.readString(messagePath);

        assertThat(dialogSource)
            .contains("fetchTableResources")
            .contains("tableResourceOptions")
            .contains("loadTableResources")
            .contains("tableId:")
            .contains("tableGroupId:")
            .contains("resource.resourceType === 'dining_table'")
            .contains("resource.resourceType === 'table_group'")
            .contains("defaultFutureReservationDateTime")
            .contains("isReservationStartInPast")
            .contains("formatReservationCreateErrorMessage")
            .contains("RESERVATION_START_IN_PAST")
            .contains("reservation.start_in_past")
            .doesNotContain("{{ apiError.error.messageKey }}");

        assertThat(timeSource)
            .contains("export function defaultFutureReservationDateTime")
            .contains("export function isReservationStartInPast")
            .contains("roundUpToQuarterHour")
            .contains("30 * 60 * 1000");

        assertThat(messageSource)
            .contains("export function formatReservationCreateErrorMessage")
            .contains("reservation.start_in_past")
            .contains("预约开始时间不能早于当前时间")
            .contains("reservation.invalid_phone_e164")
            .contains("手机号必须是 8 位新加坡号码");
    }

    @Test
    void oldReservationCreateRouteRedirectsToNewReservationWorkbenchEntry() throws Exception {
        Path routerPath = Path.of("src", "router", "index.ts");
        Path todayPagePath = Path.of("src", "pages", "ReservationTodayViewPage.vue");

        assertThat(routerPath).exists();
        assertThat(todayPagePath).exists();

        String routerSource = Files.readString(routerPath);
        String todayPageSource = Files.readString(todayPagePath);

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
}
