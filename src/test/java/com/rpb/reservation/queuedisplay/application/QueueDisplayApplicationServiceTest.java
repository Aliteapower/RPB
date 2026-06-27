package com.rpb.reservation.queuedisplay.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.queuedisplay.persistence.QueueDisplayRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueueDisplayApplicationServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000971");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000971");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000972");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000971");
    private static final UUID TICKET_1 = UUID.fromString("91000000-0000-0000-0000-000000000971");
    private static final UUID TICKET_2 = UUID.fromString("91000000-0000-0000-0000-000000000972");
    private static final Instant NOW = Instant.parse("2030-06-20T02:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private FakeStoreRepository storeRepository;
    private FakeQueueDisplayRepository repository;
    private QueueDisplayApplicationService service;

    @BeforeEach
    void setUp() {
        storeRepository = new FakeStoreRepository();
        repository = new FakeQueueDisplayRepository();
        service = new QueueDisplayApplicationService(storeRepository, repository, CLOCK);
    }

    @Test
    void selectsLatestNonExpiredCalledTicketAndComputesStoreTime() {
        repository.currentCall = Optional.of(new QueueDisplayCurrentCall(
            TICKET_2,
            2,
            "3-4",
            "  孙女士  ",
            2,
            Instant.parse("2030-06-20T02:20:00Z"),
            Instant.parse("2030-06-20T02:33:00Z")
        ));
        repository.waitingPreview = List.of(new QueueDisplayWaitingPreviewItem(1, "1-2", "", 3));
        repository.ads = null;

        QueueDisplayResult result = service.getState(query(STORE_ID));

        assertThat(result.success()).isTrue();
        assertThat(result.serverNow()).isEqualTo(NOW);
        assertThat(result.storeTimezone()).isEqualTo("Asia/Singapore");
        assertThat(result.storeTimeText()).isEqualTo("10:30");
        assertThat(result.businessDate()).isEqualTo(LocalDate.of(2030, 6, 20));
        assertThat(result.currentCall().queueTicketId()).isEqualTo(TICKET_2);
        assertThat(result.currentCall().displayNumber()).isEqualTo("B2");
        assertThat(result.currentCall().customerDisplayName()).isEqualTo("孙女士");
        assertThat(result.waitingCount()).isEqualTo(1);
        assertThat(result.waitingPreview().get(0).displayNumber()).isEqualTo("A1");
        assertThat(result.waitingPreview().get(0).customerDisplayName()).isEqualTo("顾客");
        assertThat(result.ads().slideDurationSeconds()).isEqualTo(5);
        assertThat(result.ads().statePollSeconds()).isEqualTo(3);
        assertThat(result.ads().slides()).extracting(QueueDisplayAdSlide::title)
            .containsExactly("欢迎光临", "今日推荐", "特惠活动", "会员专享");
        assertThat(repository.lastBusinessDate).isEqualTo(LocalDate.of(2030, 6, 20));
        assertThat(repository.lastNow).isEqualTo(NOW);
    }

    @Test
    void expiredOrMissingCurrentCallReturnsNullCurrentCallAndAds() {
        repository.currentCall = Optional.empty();
        repository.ads = new QueueDisplayAds("text", 8, 4, List.of(
            new QueueDisplayAdSlide("slide-1", "自定义", "副标题", "标语")
        ));

        QueueDisplayResult result = service.getState(query(STORE_ID));

        assertThat(result.success()).isTrue();
        assertThat(result.currentCall()).isNull();
        assertThat(result.ads().slideDurationSeconds()).isEqualTo(8);
        assertThat(result.ads().statePollSeconds()).isEqualTo(4);
        assertThat(result.ads().slides()).extracting(QueueDisplayAdSlide::title).containsExactly("自定义");
    }

    @Test
    void returnsConfiguredMediaSlidesWithoutFallingBackToTextSeeds() {
        repository.currentCall = Optional.empty();
        repository.ads = new QueueDisplayAds("media", 9, 5, List.of(
            QueueDisplayAdSlide.media("media-1", "image", "/api/v1/stores/" + STORE_ID + "/queue-display/media/asset-image", "新品海报", "新品推荐"),
            QueueDisplayAdSlide.media("media-2", "video", "/api/v1/stores/" + STORE_ID + "/queue-display/media/asset-video", "餐厅短片", "环境展示")
        ));

        QueueDisplayResult result = service.getState(query(STORE_ID));

        assertThat(result.success()).isTrue();
        assertThat(result.ads().mode()).isEqualTo("media");
        assertThat(result.ads().slides()).extracting(QueueDisplayAdSlide::mediaKind)
            .containsExactly("image", "video");
        assertThat(result.ads().slides()).extracting(QueueDisplayAdSlide::mediaUrl)
            .containsExactly(
                "/api/v1/stores/" + STORE_ID + "/queue-display/media/asset-image",
                "/api/v1/stores/" + STORE_ID + "/queue-display/media/asset-video"
            );
    }

    @Test
    void returnsTenantLogoQueueDisplayMediaUrlWhenTenantHasLogoAsset() {
        UUID logoAssetId = UUID.fromString("8a000000-0000-0000-0000-000000000971");
        repository.tenantLogoMediaAssetId = Optional.of(logoAssetId);

        QueueDisplayResult result = service.getState(query(STORE_ID));

        assertThat(result.success()).isTrue();
        assertThat(result.tenantLogoUrl())
            .isEqualTo("/api/v1/stores/" + STORE_ID + "/queue-display/media/" + logoAssetId);
    }

    @Test
    void storeNotFoundScopeMismatchAndPersistenceErrorReturnStableErrors() {
        storeRepository.store = Optional.empty();
        assertThat(service.getState(query(STORE_ID)).error()).isEqualTo(QueueDisplayError.STORE_NOT_FOUND);

        storeRepository.store = Optional.of(Store.skeleton(
            new StoreId(OTHER_STORE_ID),
            new TenantId(TENANT_ID),
            "other",
            "Asia/Singapore",
            "zh-CN",
            "active"
        ));
        assertThat(service.getState(query(STORE_ID)).error()).isEqualTo(QueueDisplayError.STORE_SCOPE_MISMATCH);

        storeRepository.store = Optional.of(store(STORE_ID));
        repository.throwPersistence = true;
        assertThat(service.getState(query(STORE_ID)).error()).isEqualTo(QueueDisplayError.PERSISTENCE_ERROR);
    }

    private static QueueDisplayQuery query(UUID storeId) {
        return new QueueDisplayQuery(TENANT_ID, storeId, ACTOR_ID, "staff");
    }

    private static Store store(UUID storeId) {
        return Store.skeleton(new StoreId(storeId), new TenantId(TENANT_ID), "display-store", "Asia/Singapore", "zh-CN", "active");
    }

    private static final class FakeStoreRepository implements StoreRepositoryPort {
        private Optional<Store> store = Optional.of(store(STORE_ID));

        @Override
        public Optional<Store> findById(StoreScope scope) {
            return store;
        }

        @Override
        public Optional<StorePolicy> findCurrentPolicy(StoreScope scope, OffsetDateTime at) {
            return Optional.empty();
        }

        @Override
        public Store save(StoreScope scope, Store store) {
            throw new UnsupportedOperationException("read_only_test");
        }

        @Override
        public StorePolicy savePolicy(StoreScope scope, StorePolicy policy) {
            throw new UnsupportedOperationException("read_only_test");
        }
    }

    private static final class FakeQueueDisplayRepository implements QueueDisplayRepository {
        private Optional<QueueDisplayCurrentCall> currentCall = Optional.empty();
        private List<QueueDisplayWaitingPreviewItem> waitingPreview = List.of();
        private QueueDisplayAds ads = new QueueDisplayAds("text", 5, 3, List.of());
        private Optional<UUID> tenantLogoMediaAssetId = Optional.empty();
        private LocalDate lastBusinessDate;
        private Instant lastNow;
        private boolean throwPersistence;

        @Override
        public Optional<QueueDisplayCurrentCall> findCurrentCall(StoreScope scope, LocalDate businessDate, Instant now) {
            this.lastBusinessDate = businessDate;
            this.lastNow = now;
            if (throwPersistence) {
                throw new IllegalStateException("db_down");
            }
            return currentCall;
        }

        @Override
        public List<QueueDisplayWaitingPreviewItem> findWaitingPreview(StoreScope scope, LocalDate businessDate, int limit) {
            return waitingPreview;
        }

        @Override
        public int countWaiting(StoreScope scope, LocalDate businessDate) {
            return waitingPreview.size();
        }

        @Override
        public QueueDisplayAds findActiveAds(StoreScope scope) {
            return ads;
        }

        @Override
        public Optional<UUID> findTenantLogoMediaAssetId(StoreScope scope) {
            return tenantLogoMediaAssetId;
        }
    }
}
