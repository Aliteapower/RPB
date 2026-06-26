package com.rpb.reservation.queuedisplay.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queue.application.QueueTicketDisplayNumbers;
import com.rpb.reservation.queuedisplay.persistence.QueueDisplayRepository;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueDisplayApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueDisplayApplicationService.class);
    private static final int WAITING_PREVIEW_LIMIT = 6;
    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("HH:mm");

    private final StoreRepositoryPort storeRepository;
    private final QueueDisplayRepository repository;
    private final Clock clock;

    public QueueDisplayApplicationService(StoreRepositoryPort storeRepository, QueueDisplayRepository repository, Clock clock) {
        this.storeRepository = storeRepository;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public QueueDisplayResult getState(QueueDisplayQuery query) {
        if (query == null || query.tenantId() == null || query.storeId() == null || query.actorId() == null) {
            return QueueDisplayResult.failure(QueueDisplayError.INVALID_QUERY);
        }
        StoreScope scope = new StoreScope(new TenantId(query.tenantId()), query.storeId());
        Store store = storeRepository.findById(scope).orElse(null);
        if (store == null) {
            return QueueDisplayResult.failure(QueueDisplayError.STORE_NOT_FOUND);
        }
        if (!scope.equals(store.scope())) {
            return QueueDisplayResult.failure(QueueDisplayError.STORE_SCOPE_MISMATCH);
        }

        try {
            Instant now = clock.instant();
            ZoneId zone = zoneId(store.timezone());
            LocalDate businessDate = LocalDate.now(clock.withZone(zone));
            QueueDisplayCurrentCall currentCall = repository.findCurrentCall(scope, businessDate, now)
                .map(this::withDisplayValues)
                .orElse(null);
            List<QueueDisplayWaitingPreviewItem> preview = repository.findWaitingPreview(scope, businessDate, WAITING_PREVIEW_LIMIT)
                .stream()
                .map(this::withDisplayValues)
                .toList();
            int waitingCount = repository.countWaiting(scope, businessDate);
            QueueDisplayAds ads = normalizeAds(repository.findActiveAds(scope));
            return QueueDisplayResult.success(
                now,
                zone.getId(),
                TIME_TEXT.format(now.atZone(zone)),
                businessDate,
                currentCall,
                waitingCount,
                preview,
                ads
            );
        } catch (RuntimeException exception) {
            LOGGER.warn("queue_display_state_read_failed: {}", exception.toString());
            return QueueDisplayResult.failure(QueueDisplayError.PERSISTENCE_ERROR);
        }
    }

    private QueueDisplayCurrentCall withDisplayValues(QueueDisplayCurrentCall call) {
        return call.withDisplayValues(
            QueueTicketDisplayNumbers.fromGroupCode(call.partySizeGroup(), call.ticketNumber()),
            safeDisplayName(call.customerName())
        );
    }

    private QueueDisplayWaitingPreviewItem withDisplayValues(QueueDisplayWaitingPreviewItem item) {
        return item.withDisplayValues(
            QueueTicketDisplayNumbers.fromGroupCode(item.partySizeGroup(), item.ticketNumber()),
            safeDisplayName(item.customerName())
        );
    }

    private static QueueDisplayAds normalizeAds(QueueDisplayAds ads) {
        if (ads == null || ads.slides().isEmpty()) {
            return defaultAds();
        }
        return ads;
    }

    private static QueueDisplayAds defaultAds() {
        return new QueueDisplayAds("text", 5, 3, List.of(
            new QueueDisplayAdSlide("platform-1", "欢迎光临", "食刻 · 餐厅", "新鲜食材 · 匠心烹饪 · 极致服务"),
            new QueueDisplayAdSlide("platform-2", "今日推荐", "招牌炭烤牛排", "精选澳洲谷饲牛肉 · 现点现烤"),
            new QueueDisplayAdSlide("platform-3", "特惠活动", "工作日午餐8折", "周一至周五 11:00-14:00 全场8折"),
            new QueueDisplayAdSlide("platform-4", "会员专享", "充值满赠", "充500送50 · 充1000送120")
        ));
    }

    private static String safeDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return "顾客";
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
}
