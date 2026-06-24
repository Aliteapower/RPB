package com.rpb.reservation.reservation.application.service;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.scope.DefaultStoreAccessPolicy;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.reservation.application.ReservationCalendarSummaryDay;
import com.rpb.reservation.reservation.application.ReservationCalendarSummaryResult;
import com.rpb.reservation.reservation.application.ReservationTodayViewError;
import com.rpb.reservation.reservation.application.ReservationTodayViewItem;
import com.rpb.reservation.reservation.application.ReservationTodayViewResult;
import com.rpb.reservation.reservation.application.port.out.ReservationCalendarSummaryRow;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationTodayViewRow;
import com.rpb.reservation.reservation.application.query.ReservationCalendarSummaryQuery;
import com.rpb.reservation.reservation.application.query.ReservationTodayViewQuery;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationTodayViewApplicationService {
    private static final String STATUS_OPERATIONAL = "operational";
    private static final String STATUS_ALL = "all";
    private static final Set<String> OPERATIONAL_STATUSES = orderedSet(
        ReservationStatus.CONFIRMED.code(),
        ReservationStatus.ARRIVED.code(),
        ReservationStatus.SEATED.code()
    );
    private static final Set<String> ALL_VIEW_STATUSES = orderedSet(
        ReservationStatus.CONFIRMED.code(),
        ReservationStatus.ARRIVED.code(),
        ReservationStatus.SEATED.code(),
        ReservationStatus.CANCELLED.code(),
        ReservationStatus.NO_SHOW.code(),
        ReservationStatus.COMPLETED.code()
    );

    private final StoreRepositoryPort storeRepository;
    private final ReservationRepositoryPort reservationRepository;
    private final Clock clock;
    private final DefaultStoreAccessPolicy storeAccessPolicy = new DefaultStoreAccessPolicy();

    @Autowired
    public ReservationTodayViewApplicationService(
        StoreRepositoryPort storeRepository,
        ReservationRepositoryPort reservationRepository
    ) {
        this(storeRepository, reservationRepository, Clock.systemUTC());
    }

    public ReservationTodayViewApplicationService(
        StoreRepositoryPort storeRepository,
        ReservationRepositoryPort reservationRepository,
        Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReservationTodayViewResult getToday(ReservationTodayViewQuery query) {
        ReservationTodayViewError validationError = validate(query);
        if (validationError != null) {
            return ReservationTodayViewResult.failure(validationError);
        }

        StoreScope scope = new StoreScope(new TenantId(query.tenantId()), query.storeId());
        Store store = storeRepository.findById(scope).orElse(null);
        if (store == null) {
            return ReservationTodayViewResult.failure(ReservationTodayViewError.STORE_NOT_FOUND);
        }
        if (!scope.equals(store.scope())) {
            return ReservationTodayViewResult.failure(ReservationTodayViewError.STORE_SCOPE_MISMATCH);
        }

        RuleDecision storeAccess = storeAccessPolicy.decide(scope, query.actorId(), query.actorType());
        if (!storeAccess.accepted()) {
            return ReservationTodayViewResult.failure(ReservationTodayViewError.STORE_ACCESS_DENIED);
        }

        ZoneId zoneId = zoneId(store.timezone());
        LocalDate businessDate = resolveBusinessDate(query.businessDate(), zoneId);
        if (businessDate == null) {
            return ReservationTodayViewResult.failure(ReservationTodayViewError.INVALID_BUSINESS_DATE);
        }

        StatusFilter statusFilter = resolveStatusFilter(query.status());
        if (statusFilter == null) {
            return ReservationTodayViewResult.failure(ReservationTodayViewError.INVALID_STATUS_FILTER);
        }

        try {
            List<ReservationTodayViewItem> items = reservationRepository
                .findTodayView(scope, new BusinessDate(businessDate), statusFilter.statuses())
                .stream()
                .map(ReservationTodayViewApplicationService::toItem)
                .toList();
            return ReservationTodayViewResult.success(
                scope.storeId().value(),
                businessDate,
                zoneId.getId(),
                statusFilter.name(),
                items
            );
        } catch (RuntimeException exception) {
            return ReservationTodayViewResult.failure(ReservationTodayViewError.PERSISTENCE_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public ReservationCalendarSummaryResult getCalendarSummary(ReservationCalendarSummaryQuery query) {
        ReservationTodayViewError validationError = validate(query);
        if (validationError != null) {
            return ReservationCalendarSummaryResult.failure(validationError);
        }

        StoreScope scope = new StoreScope(new TenantId(query.tenantId()), query.storeId());
        Store store = storeRepository.findById(scope).orElse(null);
        if (store == null) {
            return ReservationCalendarSummaryResult.failure(ReservationTodayViewError.STORE_NOT_FOUND);
        }
        if (!scope.equals(store.scope())) {
            return ReservationCalendarSummaryResult.failure(ReservationTodayViewError.STORE_SCOPE_MISMATCH);
        }

        RuleDecision storeAccess = storeAccessPolicy.decide(scope, query.actorId(), query.actorType());
        if (!storeAccess.accepted()) {
            return ReservationCalendarSummaryResult.failure(ReservationTodayViewError.STORE_ACCESS_DENIED);
        }

        ZoneId zoneId = zoneId(store.timezone());
        YearMonth month = resolveMonth(query.month(), zoneId);
        if (month == null) {
            return ReservationCalendarSummaryResult.failure(ReservationTodayViewError.INVALID_BUSINESS_DATE);
        }

        try {
            List<ReservationCalendarSummaryDay> days = reservationRepository
                .findCalendarSummary(scope, month.atDay(1), month.plusMonths(1).atDay(1), ALL_VIEW_STATUSES)
                .stream()
                .map(ReservationTodayViewApplicationService::toCalendarSummaryDay)
                .toList();
            return ReservationCalendarSummaryResult.success(
                scope.storeId().value(),
                month,
                zoneId.getId(),
                days
            );
        } catch (RuntimeException exception) {
            return ReservationCalendarSummaryResult.failure(ReservationTodayViewError.PERSISTENCE_ERROR);
        }
    }

    private static ReservationTodayViewError validate(ReservationTodayViewQuery query) {
        if (
            query == null
                || query.tenantId() == null
                || query.storeId() == null
                || query.actorId() == null
                || !hasText(query.actorType())
        ) {
            return ReservationTodayViewError.INVALID_COMMAND;
        }
        return null;
    }

    private static ReservationTodayViewError validate(ReservationCalendarSummaryQuery query) {
        if (
            query == null
                || query.tenantId() == null
                || query.storeId() == null
                || query.actorId() == null
                || !hasText(query.actorType())
        ) {
            return ReservationTodayViewError.INVALID_COMMAND;
        }
        return null;
    }

    private static ReservationTodayViewItem toItem(ReservationTodayViewRow row) {
        return new ReservationTodayViewItem(
            row.reservationId(),
            row.reservationCode(),
            row.status(),
            row.partySize(),
            row.reservedStartAt(),
            row.reservedEndAt(),
            row.holdUntilAt(),
            row.businessDate(),
            row.customerName(),
            row.customerNickname(),
            maskPhone(row.phoneE164()),
            row.note(),
            row.seatingId(),
            row.currentResourceType(),
            row.currentResourceId(),
            row.currentResourceCode(),
            row.assignedResourceType(),
            row.assignedResourceId(),
            row.assignedResourceCode(),
            row.queueTicketId(),
            row.queueTicketNumber(),
            row.queueTicketStatus()
        );
    }

    private static ReservationCalendarSummaryDay toCalendarSummaryDay(ReservationCalendarSummaryRow row) {
        return new ReservationCalendarSummaryDay(row.businessDate(), row.reservationCount());
    }

    private LocalDate resolveBusinessDate(String rawBusinessDate, ZoneId zoneId) {
        if (!hasText(rawBusinessDate)) {
            return LocalDate.now(clock.withZone(zoneId));
        }
        try {
            return LocalDate.parse(rawBusinessDate.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private YearMonth resolveMonth(String rawMonth, ZoneId zoneId) {
        if (!hasText(rawMonth)) {
            return YearMonth.from(LocalDate.now(clock.withZone(zoneId)));
        }
        try {
            return YearMonth.parse(rawMonth.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static StatusFilter resolveStatusFilter(String rawStatus) {
        String normalized = hasText(rawStatus) ? rawStatus.trim().toLowerCase() : STATUS_OPERATIONAL;
        if (STATUS_OPERATIONAL.equals(normalized)) {
            return new StatusFilter(STATUS_OPERATIONAL, OPERATIONAL_STATUSES);
        }
        if (STATUS_ALL.equals(normalized)) {
            return new StatusFilter(STATUS_ALL, ALL_VIEW_STATUSES);
        }
        if (ALL_VIEW_STATUSES.contains(normalized)) {
            return new StatusFilter(normalized, Set.of(normalized));
        }
        return null;
    }

    private static ZoneId zoneId(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            return ZoneOffset.UTC;
        }
    }

    private static String maskPhone(String phoneE164) {
        if (!hasText(phoneE164)) {
            return null;
        }
        String value = phoneE164.trim();
        int start = Math.max(0, value.length() - 4);
        return "****" + value.substring(start);
    }

    private static Set<String> orderedSet(String... values) {
        LinkedHashSet<String> statuses = new LinkedHashSet<>();
        for (String value : values) {
            statuses.add(value);
        }
        return Set.copyOf(statuses);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record StatusFilter(String name, Set<String> statuses) {
    }
}
