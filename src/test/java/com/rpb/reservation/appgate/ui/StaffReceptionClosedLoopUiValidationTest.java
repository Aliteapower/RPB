package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffReceptionClosedLoopUiValidationTest {

    @Test
    void staffHomeExposesThreeReceptionActionsIncludingWalkInQueue() throws Exception {
        String staffHome = Files.readString(Path.of("src", "pages", "StoreStaffHomePage.vue"));
        String actionGroup = Files.readString(Path.of("src", "components", "staff-home", "StaffHomeActionGroup.vue"));

        assertThat(staffHome)
            .contains("hasPermission('walkin.queue.create')")
            .contains("walkInQueueRoute")
            .contains("id: 'walkin-queue'")
            .contains("label: '现场取号'")
            .contains("接待")
            .contains(":layout=\"'three'\"");

        assertThat(actionGroup)
            .contains("layout?: 'two' | 'three'")
            .contains("three-action-grid")
            .contains("grid-template-columns: repeat(3, minmax(0, 1fr))");
    }

    @Test
    void queueListProvidesOneTapCallSkipCancelAndSeatWithoutCopyIdWorkflow() throws Exception {
        String queueList = Files.readString(Path.of("src", "pages", "QueueTicketListPage.vue"));

        assertThat(queueList)
            .contains("callQueueTicket")
            .contains("cancelQueueTicket")
            .contains("queue.cancel")
            .contains("label: '入桌'")
            .contains("name: 'seating-from-called-queue'")
            .contains("queueTicketId: item.queueTicketId")
            .contains("叫号")
            .contains("过号")
            .contains("取消");

        assertThat(queueList)
            .doesNotContain("navigator.clipboard")
            .doesNotContain("复制 ID")
            .doesNotContain("复制id");
    }

    @Test
    void routesUseNewWalkInQueuePageAndOldReservationCreatePageIsRemoved() throws Exception {
        String router = Files.readString(Path.of("src", "router", "index.ts"));

        assertThat(router)
            .contains("WalkInQueuePage")
            .contains("path: '/stores/:storeId/walk-ins/queue'")
            .contains("name: 'walk-in-queue'")
            .doesNotContain("ReservationCreatePage");

        assertThat(Files.exists(Path.of("src", "pages", "ReservationCreatePage.vue"))).isFalse();
    }
}
