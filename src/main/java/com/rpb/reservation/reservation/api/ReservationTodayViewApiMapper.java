package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationTodayViewItem;
import com.rpb.reservation.reservation.application.ReservationTodayViewResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReservationTodayViewApiMapper {

    public ReservationTodayViewResponse toResponse(ReservationTodayViewResult result) {
        return new ReservationTodayViewResponse(
            true,
            result.storeId(),
            result.businessDate(),
            result.storeTimezone(),
            result.statusFilter(),
            items(result.items())
        );
    }

    private static List<ReservationTodayViewResponse.ItemResponse> items(List<ReservationTodayViewItem> items) {
        return items.stream().map(ReservationTodayViewApiMapper::item).toList();
    }

    private static ReservationTodayViewResponse.ItemResponse item(ReservationTodayViewItem item) {
        return new ReservationTodayViewResponse.ItemResponse(
            item.reservationId(),
            item.reservationCode(),
            item.status(),
            item.partySize(),
            item.reservedStartAt(),
            item.reservedEndAt(),
            item.holdUntilAt(),
            item.businessDate(),
            item.customerName(),
            item.customerNickname(),
            item.phoneMasked(),
            item.note()
        );
    }
}
