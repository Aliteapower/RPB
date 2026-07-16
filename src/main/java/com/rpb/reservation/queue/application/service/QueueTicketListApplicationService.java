package com.rpb.reservation.queue.application.service;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.scope.DefaultStoreAccessPolicy;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.queue.application.QueueTicketListError;
import com.rpb.reservation.queue.application.QueueTicketListItem;
import com.rpb.reservation.queue.application.QueueTicketListPage;
import com.rpb.reservation.queue.application.QueueTicketListResult;
import com.rpb.reservation.queue.application.QueueTicketDisplayNumbers;
import com.rpb.reservation.queue.application.port.out.QueueTicketListRow;
import com.rpb.reservation.queue.application.port.out.QueueTicketListRows;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.queue.application.query.QueueTicketListQuery;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class QueueTicketListApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueTicketListApplicationService.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_OFFSET = 0;

    private final StoreRepositoryPort storeRepository;
    private final QueueTicketRepositoryPort queueTicketRepository;
    private final Clock clock;
    private final DefaultStoreAccessPolicy storeAccessPolicy = new DefaultStoreAccessPolicy();

    @Autowired
    public QueueTicketListApplicationService(
        StoreRepositoryPort storeRepository,
        QueueTicketRepositoryPort queueTicketRepository,
        Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.queueTicketRepository = queueTicketRepository;
        this.clock = clock;
    }

    public QueueTicketListApplicationService(
        StoreRepositoryPort storeRepository,
        QueueTicketRepositoryPort queueTicketRepository
    ) {
        this(storeRepository, queueTicketRepository, Clock.systemUTC());
    }

    @Transactional(readOnly = true)
    public QueueTicketListResult listQueueTickets(QueueTicketListQuery query) {
        if (!validQuery(query)) {
            return QueueTicketListResult.failure(QueueTicketListError.INVALID_QUERY);
        }

        StatusFilter statusFilter = resolveStatus(query.status());
        if (!statusFilter.valid()) {
            return QueueTicketListResult.failure(QueueTicketListError.INVALID_STATUS);
        }

        Integer limit = resolveLimit(query.limit());
        if (limit == null) {
            return QueueTicketListResult.failure(QueueTicketListError.INVALID_LIMIT);
        }

        Integer offset = resolveOffset(query.offset());
        if (offset == null) {
            return QueueTicketListResult.failure(QueueTicketListError.INVALID_OFFSET);
        }

        Integer partySizeFilter;
        if (hasText(query.partySize())) {
            partySizeFilter = resolveOptionalPositiveInteger(query.partySize());
            if (partySizeFilter == null) {
                return QueueTicketListResult.failure(QueueTicketListError.INVALID_QUERY);
            }
        } else {
            partySizeFilter = null;
        }

        String tableAreaFilter = trimToNull(query.tableArea());
        String phoneDigitsFilter = normalizePhoneDigits(query.phone());

        StoreScope scope = new StoreScope(new TenantId(query.tenantId()), query.storeId());
        Store store = storeRepository.findById(scope).orElse(null);
        if (store == null) {
            return QueueTicketListResult.failure(QueueTicketListError.STORE_NOT_FOUND);
        }
        if (!scope.equals(store.scope())) {
            return QueueTicketListResult.failure(QueueTicketListError.STORE_SCOPE_MISMATCH);
        }

        RuleDecision storeAccess = storeAccessPolicy.decide(scope, query.actorId(), query.actorType());
        if (!storeAccess.accepted()) {
            return QueueTicketListResult.failure(QueueTicketListError.STORE_ACCESS_DENIED);
        }

        try {
            BusinessDate businessDate = new BusinessDate(LocalDate.now(clock.withZone(zoneId(store.timezone()))));
            QueueTicketListRows rows = queueTicketRepository.findQueueTicketList(
                scope,
                statusFilter.status(),
                businessDate,
                limit,
                offset,
                tableAreaFilter,
                partySizeFilter,
                phoneDigitsFilter
            );
            List<QueueTicketListItem> items = rows.rows().stream()
                .map(QueueTicketListApplicationService::toItem)
                .toList();
            return QueueTicketListResult.success(items, new QueueTicketListPage(limit, offset, rows.total()));
        } catch (RuntimeException exception) {
            LOGGER.warn("queue_ticket_list_read_failed: {}", exception.toString());
            return QueueTicketListResult.failure(QueueTicketListError.PERSISTENCE_ERROR);
        }
    }

    private static QueueTicketListItem toItem(QueueTicketListRow row) {
        return new QueueTicketListItem(
            row.queueTicketId(),
            row.queueTicketNumber(),
            QueueTicketDisplayNumbers.fromGroupCode(row.partySizeGroup(), row.queueTicketNumber()),
            row.queueTicketStatus(),
            row.partySize(),
            row.partySizeGroup(),
            row.reservationId(),
            row.reservationCode(),
            row.reservationStatus(),
            row.customerName(),
            maskPhone(row.customerPhoneE164()),
            row.assignedResourceType(),
            row.assignedResourceId(),
            row.assignedResourceCode(),
            row.assignedResourceGroupType(),
            row.assignedResourceLabel(),
            row.assignedResourceAreaName(),
            row.createdAt(),
            row.calledAt(),
            row.expiresAt(),
            row.expiresAt()
        );
    }

    private static boolean validQuery(QueueTicketListQuery query) {
        return query != null
            && query.tenantId() != null
            && query.storeId() != null
            && query.actorId() != null
            && hasText(query.actorType());
    }

    private static StatusFilter resolveStatus(String rawStatus) {
        if (!hasText(rawStatus)) {
            return new StatusFilter(null, true);
        }
        String normalized = rawStatus.trim().toLowerCase();
        for (QueueTicketStatus status : QueueTicketStatus.values()) {
            if (status.code().equals(normalized)) {
                return new StatusFilter(status, true);
            }
        }
        return new StatusFilter(null, false);
    }

    private static Integer resolveLimit(String rawLimit) {
        Integer parsed = parseOptionalInteger(rawLimit, DEFAULT_LIMIT);
        if (parsed == null || parsed <= 0 || parsed > MAX_LIMIT) {
            return null;
        }
        return parsed;
    }

    private static Integer resolveOffset(String rawOffset) {
        Integer parsed = parseOptionalInteger(rawOffset, DEFAULT_OFFSET);
        if (parsed == null || parsed < 0) {
            return null;
        }
        return parsed;
    }

    private static Integer resolveOptionalPositiveInteger(String rawValue) {
        Integer parsed = parseOptionalInteger(rawValue, 0);
        if (parsed == null || parsed <= 0) {
            return null;
        }
        return parsed;
    }

    private static Integer parseOptionalInteger(String rawValue, int defaultValue) {
        if (!hasText(rawValue)) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(rawValue.trim());
        } catch (NumberFormatException exception) {
            return null;
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

    private static String normalizePhoneDigits(String rawPhone) {
        if (!hasText(rawPhone)) {
            return null;
        }
        String digits = rawPhone.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

    private static String trimToNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static ZoneId zoneId(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            return ZoneOffset.UTC;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record StatusFilter(QueueTicketStatus status, boolean valid) {
    }
}
