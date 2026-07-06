package com.rpb.reservation.queuedisplay.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.i18n.application.I18nMessageResolver;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final I18nMessageResolver i18nMessageResolver;
    private final Clock clock;

    public QueueDisplayApplicationService(
        StoreRepositoryPort storeRepository,
        QueueDisplayRepository repository,
        I18nMessageResolver i18nMessageResolver,
        Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.repository = repository;
        this.i18nMessageResolver = i18nMessageResolver;
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
            QueueDisplayAds ads = localizeTextAds(normalizeAds(repository.findActiveAds(scope)), scope, query.locale());
            String tenantLogoUrl = repository.findTenantLogoMediaAssetId(scope)
                .map(assetId -> CallScreenMediaService.queueDisplayMediaUrl(scope, assetId))
                .orElse(null);
            return QueueDisplayResult.success(
                now,
                zone.getId(),
                TIME_TEXT.format(now.atZone(zone)),
                businessDate,
                currentCall,
                waitingCount,
                preview,
                ads,
                tenantLogoUrl
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

    private QueueDisplayAds localizeTextAds(QueueDisplayAds ads, StoreScope scope, String locale) {
        if (!"text".equals(ads.mode()) || ads.slides().isEmpty()) {
            return ads;
        }

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (QueueDisplayAdSlide slide : ads.slides()) {
            addKey(keys, slide.titleI18nKey());
            addKey(keys, slide.subtitleI18nKey());
            addKey(keys, slide.taglineI18nKey());
        }
        if (keys.isEmpty()) {
            return ads;
        }

        Map<String, String> messages = i18nMessageResolver.resolve(scope, keys, locale);
        List<QueueDisplayAdSlide> localizedSlides = ads.slides().stream()
            .map(slide -> slide.withText(
                resolved(messages, slide.titleI18nKey(), slide.title()),
                resolved(messages, slide.subtitleI18nKey(), slide.subtitle()),
                resolved(messages, slide.taglineI18nKey(), slide.tagline())
            ))
            .toList();
        return new QueueDisplayAds(ads.mode(), ads.slideDurationSeconds(), ads.statePollSeconds(), localizedSlides);
    }

    private static QueueDisplayAds defaultAds() {
        return new QueueDisplayAds("text", 5, 3, List.of(
            defaultSlide("platform-1", 1, "欢迎光临", "食刻 · 餐厅", "新鲜食材 · 匠心烹饪 · 极致服务"),
            defaultSlide("platform-2", 2, "今日推荐", "招牌炭烤牛排", "精选澳洲谷饲牛肉 · 现点现烤"),
            defaultSlide("platform-3", 3, "特惠活动", "工作日午餐8折", "周一至周五 11:00-14:00 全场8折"),
            defaultSlide("platform-4", 4, "会员专享", "充值满赠", "充500送50 · 充1000送120")
        ));
    }

    private static QueueDisplayAdSlide defaultSlide(String slideId, int sortOrder, String title, String subtitle, String tagline) {
        return QueueDisplayAdSlide.text(
            slideId,
            title,
            subtitle,
            tagline,
            callScreenSeedKey(sortOrder, "title"),
            callScreenSeedKey(sortOrder, "subtitle"),
            callScreenSeedKey(sortOrder, "tagline")
        );
    }

    private static String callScreenSeedKey(int sortOrder, String field) {
        return "call_screen.seed.restaurant_default.slide_" + sortOrder + "." + field;
    }

    private static void addKey(LinkedHashSet<String> keys, String key) {
        if (key != null && !key.isBlank()) {
            keys.add(key.trim());
        }
    }

    private static String resolved(Map<String, String> messages, String key, String fallback) {
        if (key == null || key.isBlank()) {
            return fallback;
        }
        String message = messages.get(key.trim());
        return message == null || message.isBlank() ? fallback : message;
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
