package com.rpb.reservation.queuedisplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queuedisplay.persistence.CallScreenAdminRepository;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CallScreenAdminServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000977");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000978");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000977");

    private FakeCallScreenAdminRepository repository;
    private CallScreenAdminService service;

    @BeforeEach
    void setUp() {
        repository = new FakeCallScreenAdminRepository();
        service = new CallScreenAdminService(repository);
    }

    @Test
    void clonesPlatformSeedIntoTenantEditableSetWhenMissing() {
        CallScreenSetting setting = service.getSettings(scope(TENANT_ID, STORE_ID));

        assertThat(setting.activeAdSetId()).isNotNull();
        CallScreenAdSet adSet = service.getAdSet(scope(TENANT_ID, STORE_ID), setting.activeAdSetId());
        assertThat(adSet.slides()).extracting(CallScreenTextSlide::title)
            .containsExactly("欢迎光临", "今日推荐", "特惠活动", "会员专享");
        assertThat(repository.platformSeedTitles).containsExactly("欢迎光临", "今日推荐", "特惠活动", "会员专享");
    }

    @Test
    void updatesTenantTextSlideWithoutChangingPlatformSeed() {
        CallScreenSetting setting = service.getSettings(scope(TENANT_ID, STORE_ID));

        CallScreenAdSet updated = service.updateAdSet(scope(TENANT_ID, STORE_ID), setting.activeAdSetId(), new CallScreenAdSetCommand(
            "门店文案",
            "text",
            "active",
            List.of(new CallScreenTextSlideCommand(null, "晚市推荐", "牛排", "今日供应", 1, "active", null)),
            0
        ));

        assertThat(updated.name()).isEqualTo("门店文案");
        assertThat(updated.slides()).extracting(CallScreenTextSlide::title).containsExactly("晚市推荐");
        assertThat(repository.platformSeedTitles).containsExactly("欢迎光临", "今日推荐", "特惠活动", "会员专享");
    }

    @Test
    void createsMediaAdSetWithImageAndVideoSlidesAndCanActivateIt() {
        UUID imageAssetId = UUID.fromString("8a000000-0000-0000-0000-000000000001");
        UUID videoAssetId = UUID.fromString("8a000000-0000-0000-0000-000000000002");

        CallScreenAdSet created = service.createAdSet(scope(TENANT_ID, STORE_ID), new CallScreenAdSetCommand(
            "大厅媒体轮播",
            "media",
            "active",
            List.of(),
            List.of(
                new CallScreenMediaSlideCommand(null, imageAssetId, "image", "新品推荐", "牛排海报", 1, "active", null),
                new CallScreenMediaSlideCommand(null, videoAssetId, "video", "午市短片", "餐厅环境视频", 2, "active", null)
            ),
            null
        ));

        assertThat(created.adType()).isEqualTo("media");
        assertThat(created.mediaSlides()).extracting(CallScreenMediaSlide::mediaKind)
            .containsExactly("image", "video");

        CallScreenSetting setting = service.getSettings(scope(TENANT_ID, STORE_ID));
        CallScreenSetting updated = service.updateSettings(scope(TENANT_ID, STORE_ID), new CallScreenSettingsCommand(
            created.id(),
            "media",
            "active",
            8,
            4,
            true,
            setting.version()
        ));

        assertThat(updated.adMode()).isEqualTo("media");
        assertThat(updated.activeAdSetId()).isEqualTo(created.id());
    }

    @Test
    void rejectsDisabledAdSetAsActiveSettingsTarget() {
        CallScreenAdSet disabled = service.createAdSet(scope(TENANT_ID, STORE_ID), new CallScreenAdSetCommand(
            "停用媒体轮播",
            "media",
            "disabled",
            List.of(),
            List.of(new CallScreenMediaSlideCommand(
                null,
                UUID.fromString("8a000000-0000-0000-0000-000000000011"),
                "image",
                "停用海报",
                "停用",
                1,
                "active",
                null
            )),
            null
        ));
        CallScreenSetting setting = service.getSettings(scope(TENANT_ID, STORE_ID));

        assertThatThrownBy(() -> service.updateSettings(scope(TENANT_ID, STORE_ID), new CallScreenSettingsCommand(
            disabled.id(),
            "media",
            "active",
            8,
            4,
            true,
            setting.version()
        ))).isInstanceOf(CallScreenAdminServiceException.class)
            .extracting("code")
            .isEqualTo(CallScreenAdminServiceErrorCode.REQUEST_INVALID);
    }

    @Test
    void rejectsOtherTenantAdSetAndVersionConflict() {
        CallScreenSetting tenantSetting = service.getSettings(scope(TENANT_ID, STORE_ID));
        CallScreenSetting otherSetting = service.getSettings(scope(OTHER_TENANT_ID, STORE_ID));

        assertThatThrownBy(() -> service.updateSettings(scope(TENANT_ID, STORE_ID), new CallScreenSettingsCommand(
            otherSetting.activeAdSetId(),
            "text",
            "active",
            5,
            3,
            true,
            tenantSetting.version()
        ))).isInstanceOf(CallScreenAdminServiceException.class)
            .extracting("code")
            .isEqualTo(CallScreenAdminServiceErrorCode.AD_SET_NOT_FOUND);

        assertThatThrownBy(() -> service.updateAdSet(scope(TENANT_ID, STORE_ID), tenantSetting.activeAdSetId(), new CallScreenAdSetCommand(
            "门店文案",
            "text",
            "active",
            List.of(new CallScreenTextSlideCommand(null, "标题", "副标题", "标语", 1, "active", null)),
            99
        ))).isInstanceOf(CallScreenAdminServiceException.class)
            .extracting("code")
            .isEqualTo(CallScreenAdminServiceErrorCode.VERSION_CONFLICT);
    }

    private static StoreScope scope(UUID tenantId, UUID storeId) {
        return new StoreScope(new TenantId(tenantId), storeId);
    }

    private static final class FakeCallScreenAdminRepository implements CallScreenAdminRepository {
        private final List<String> platformSeedTitles = new ArrayList<>(List.of("欢迎光临", "今日推荐", "特惠活动", "会员专享"));
        private final Map<UUID, Map<UUID, CallScreenAdSet>> adSets = new LinkedHashMap<>();
        private final Map<String, CallScreenSetting> settings = new LinkedHashMap<>();

        @Override
        public Optional<CallScreenSetting> findSetting(StoreScope scope) {
            return Optional.ofNullable(settings.get(key(scope)));
        }

        @Override
        public CallScreenSetting upsertDefaultSetting(StoreScope scope, UUID adSetId) {
            CallScreenSetting setting = new CallScreenSetting(adSetId, "text", "active", 5, 3, true, 0);
            settings.put(key(scope), setting);
            return setting;
        }

        @Override
        public CallScreenSetting updateSetting(
            StoreScope scope,
            UUID activeAdSetId,
            String adMode,
            String status,
            int slideDurationSeconds,
            int statePollSeconds,
            boolean showWaitingPreview
        ) {
            CallScreenSetting current = settings.get(key(scope));
            CallScreenSetting updated = new CallScreenSetting(
                activeAdSetId,
                adMode,
                status,
                slideDurationSeconds,
                statePollSeconds,
                showWaitingPreview,
                current.version() + 1
            );
            settings.put(key(scope), updated);
            return updated;
        }

        @Override
        public Optional<CallScreenAdSet> findDefaultTextSet(UUID tenantId, String seedKey) {
            return adSets.getOrDefault(tenantId, Map.of()).values().stream()
                .filter(adSet -> "默认叫号屏文案".equals(adSet.name()))
                .findFirst();
        }

        @Override
        public Optional<CallScreenAdSet> findAdSet(UUID tenantId, UUID adSetId) {
            return Optional.ofNullable(adSets.getOrDefault(tenantId, Map.of()).get(adSetId));
        }

        @Override
        public List<CallScreenAdSet> listAdSets(UUID tenantId) {
            return List.copyOf(adSets.getOrDefault(tenantId, Map.of()).values());
        }

        @Override
        public CallScreenAdSet cloneSeedTextSet(UUID tenantId, String seedKey) {
            UUID id = UUID.randomUUID();
            List<CallScreenTextSlide> slides = new ArrayList<>();
            for (int i = 0; i < platformSeedTitles.size(); i++) {
                slides.add(new CallScreenTextSlide(UUID.randomUUID(), platformSeedTitles.get(i), "副标题", "标语", i + 1, "active", 0));
            }
            CallScreenAdSet adSet = new CallScreenAdSet(id, "默认叫号屏文案", "text", "active", slides, 0);
            adSets.computeIfAbsent(tenantId, ignored -> new LinkedHashMap<>()).put(id, adSet);
            return adSet;
        }

        @Override
        public CallScreenAdSet createTextAdSet(UUID tenantId, String name, String status, List<CallScreenTextSlideCommand> slides) {
            UUID id = UUID.randomUUID();
            CallScreenAdSet adSet = new CallScreenAdSet(id, name, "text", status, toSlides(slides), 0);
            adSets.computeIfAbsent(tenantId, ignored -> new LinkedHashMap<>()).put(id, adSet);
            return adSet;
        }

        @Override
        public CallScreenAdSet createMediaAdSet(UUID tenantId, String name, String status, List<CallScreenMediaSlideCommand> slides) {
            UUID id = UUID.randomUUID();
            CallScreenAdSet adSet = new CallScreenAdSet(id, name, "media", status, List.of(), toMediaSlides(slides), 0);
            adSets.computeIfAbsent(tenantId, ignored -> new LinkedHashMap<>()).put(id, adSet);
            return adSet;
        }

        @Override
        public CallScreenAdSet updateTextAdSet(UUID tenantId, UUID adSetId, String name, String status, List<CallScreenTextSlideCommand> slides) {
            CallScreenAdSet current = adSets.get(tenantId).get(adSetId);
            CallScreenAdSet updated = new CallScreenAdSet(adSetId, name, "text", status, toSlides(slides), current.version() + 1);
            adSets.get(tenantId).put(adSetId, updated);
            return updated;
        }

        @Override
        public CallScreenAdSet updateMediaAdSet(UUID tenantId, UUID adSetId, String name, String status, List<CallScreenMediaSlideCommand> slides) {
            CallScreenAdSet current = adSets.get(tenantId).get(adSetId);
            CallScreenAdSet updated = new CallScreenAdSet(adSetId, name, "media", status, List.of(), toMediaSlides(slides), current.version() + 1);
            adSets.get(tenantId).put(adSetId, updated);
            return updated;
        }

        private static List<CallScreenTextSlide> toSlides(List<CallScreenTextSlideCommand> commands) {
            return commands.stream()
                .map(command -> new CallScreenTextSlide(
                    UUID.randomUUID(),
                    command.title(),
                    command.subtitle(),
                    command.tagline(),
                    command.sortOrder(),
                    command.status(),
                    0
                ))
                .toList();
        }

        private static List<CallScreenMediaSlide> toMediaSlides(List<CallScreenMediaSlideCommand> commands) {
            return commands.stream()
                .map(command -> new CallScreenMediaSlide(
                    UUID.randomUUID(),
                    command.mediaAssetId(),
                    command.mediaKind(),
                    "/api/v1/stores/20000000-0000-0000-0000-000000000977/queue-display/media/" + command.mediaAssetId(),
                    command.title(),
                    command.altText(),
                    command.sortOrder(),
                    command.status(),
                    0
                ))
                .toList();
        }

        private static String key(StoreScope scope) {
            return scope.tenantId().value() + ":" + scope.storeId().value();
        }
    }
}
