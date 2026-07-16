package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationCalendarSummaryDay;
import com.rpb.reservation.reservation.application.ReservationCalendarSummaryResult;
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

    public ReservationCalendarSummaryResponse toResponse(ReservationCalendarSummaryResult result) {
        return new ReservationCalendarSummaryResponse(
            true,
            result.storeId(),
            result.month().toString(),
            result.storeTimezone(),
            summaryDays(result.days())
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
            item.note(),
            item.seatingId(),
            item.currentResourceType(),
            item.currentResourceId(),
            item.currentResourceCode(),
            item.assignedResourceType(),
            item.assignedResourceId(),
            item.assignedResourceCode(),
            item.queueTicketId(),
            item.queueTicketNumber(),
            item.queueTicketDisplayNumber(),
            item.queueTicketStatus()
        );
    }

    private static List<ReservationCalendarSummaryResponse.DayResponse> summaryDays(
        List<ReservationCalendarSummaryDay> days
    ) {
        return days.stream().map(ReservationTodayViewApiMapper::summaryDay).toList();
    }

    private static ReservationCalendarSummaryResponse.DayResponse summaryDay(
        ReservationCalendarSummaryDay day
    ) {
        return new ReservationCalendarSummaryResponse.DayResponse(
            day.businessDate(),
            day.reservationCount()
        );
    }
}
