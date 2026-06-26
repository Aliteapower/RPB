package com.rpb.reservation.queuedisplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.queuedisplay.persistence.PlatformCallScreenSeedRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlatformCallScreenSeedServiceTest {
    private static final UUID SEED_SET_ID = UUID.fromString("82000000-0000-0000-0000-000000000901");

    private FakePlatformCallScreenSeedRepository repository;
    private PlatformCallScreenSeedService service;

    @BeforeEach
    void setUp() {
        repository = new FakePlatformCallScreenSeedRepository();
        service = new PlatformCallScreenSeedService(repository);
    }

    @Test
    void updatesPlatformTextSeedWithoutChangingTenantCopies() {
        PlatformCallScreenSeedSet updated = service.updateTextSeed(new PlatformCallScreenSeedCommand(
            "餐厅默认叫号屏文案",
            "active",
            List.of(
                new PlatformCallScreenSeedSlideCommand(null, "欢迎光临", "食刻 · 餐厅", "新鲜食材 · 匠心烹饪 · 极致服务", 1, "active", null),
                new PlatformCallScreenSeedSlideCommand(null, "午市推荐", "招牌套餐", "工作日限定供应", 2, "active", null)
            ),
            0
        ));

        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.adType()).isEqualTo("text");
        assertThat(updated.slides()).extracting(PlatformCallScreenSeedSlide::title)
            .containsExactly("欢迎光临", "午市推荐");
        assertThat(repository.tenantCopyTitles).containsExactly("欢迎光临", "今日推荐", "特惠活动", "会员专享");
    }

    @Test
    void rejectsDuplicateSortOrderAndVersionConflict() {
        assertThatThrownBy(() -> service.updateTextSeed(new PlatformCallScreenSeedCommand(
            "餐厅默认叫号屏文案",
            "active",
            List.of(
                new PlatformCallScreenSeedSlideCommand(null, "欢迎光临", "食刻", "标语", 1, "active", null),
                new PlatformCallScreenSeedSlideCommand(null, "今日推荐", "牛排", "标语", 1, "active", null)
            ),
            0
        ))).isInstanceOf(PlatformCallScreenSeedServiceException.class)
            .extracting("code")
            .isEqualTo(PlatformCallScreenSeedServiceErrorCode.REQUEST_INVALID);

        assertThatThrownBy(() -> service.updateTextSeed(new PlatformCallScreenSeedCommand(
            "餐厅默认叫号屏文案",
            "active",
            List.of(new PlatformCallScreenSeedSlideCommand(null, "欢迎光临", "食刻", "标语", 1, "active", null)),
            99
        ))).isInstanceOf(PlatformCallScreenSeedServiceException.class)
            .extracting("code")
            .isEqualTo(PlatformCallScreenSeedServiceErrorCode.VERSION_CONFLICT);
    }

    @Test
    void rejectsEmptyOrNullSlidePayloadsAsInvalidRequests() {
        assertThatThrownBy(() -> service.updateTextSeed(new PlatformCallScreenSeedCommand(
            "餐厅默认叫号屏文案",
            "active",
            null,
            0
        ))).isInstanceOf(PlatformCallScreenSeedServiceException.class)
            .extracting("code")
            .isEqualTo(PlatformCallScreenSeedServiceErrorCode.REQUEST_INVALID);

        assertThatThrownBy(() -> service.updateTextSeed(new PlatformCallScreenSeedCommand(
            "餐厅默认叫号屏文案",
            "active",
            java.util.Collections.singletonList((PlatformCallScreenSeedSlideCommand) null),
            0
        ))).isInstanceOf(PlatformCallScreenSeedServiceException.class)
            .extracting("code")
            .isEqualTo(PlatformCallScreenSeedServiceErrorCode.REQUEST_INVALID);
    }

    @Test
    void returnsSeedNotFoundWhenDefaultTextSeedIsMissing() {
        repository.seeds.clear();

        assertThatThrownBy(service::getTextSeed)
            .isInstanceOf(PlatformCallScreenSeedServiceException.class)
            .extracting("code")
            .isEqualTo(PlatformCallScreenSeedServiceErrorCode.SEED_NOT_FOUND);
    }

    private static final class FakePlatformCallScreenSeedRepository implements PlatformCallScreenSeedRepository {
        private final Map<String, PlatformCallScreenSeedSet> seeds = new LinkedHashMap<>();
        private final List<String> tenantCopyTitles = new ArrayList<>(List.of("欢迎光临", "今日推荐", "特惠活动", "会员专享"));

        private FakePlatformCallScreenSeedRepository() {
            seeds.put("restaurant_default", new PlatformCallScreenSeedSet(
                SEED_SET_ID,
                "restaurant_default",
                "餐厅默认叫号屏文案",
                "text",
                "active",
                List.of(
                    slide("欢迎光临", 1),
                    slide("今日推荐", 2),
                    slide("特惠活动", 3),
                    slide("会员专享", 4)
                ),
                0
            ));
        }

        @Override
        public Optional<PlatformCallScreenSeedSet> findBySeedKey(String seedKey) {
            return Optional.ofNullable(seeds.get(seedKey));
        }

        @Override
        public PlatformCallScreenSeedSet updateTextSeed(
            UUID seedSetId,
            String displayName,
            String status,
            List<PlatformCallScreenSeedSlideCommand> slides
        ) {
            PlatformCallScreenSeedSet current = seeds.get("restaurant_default");
            PlatformCallScreenSeedSet updated = new PlatformCallScreenSeedSet(
                seedSetId,
                current.seedKey(),
                displayName,
                "text",
                status,
                slides.stream()
                    .map(slide -> new PlatformCallScreenSeedSlide(
                        UUID.randomUUID(),
                        slide.title(),
                        slide.subtitle(),
                        slide.tagline(),
                        slide.sortOrder(),
                        slide.status(),
                        0
                    ))
                    .toList(),
                current.version() + 1
            );
            seeds.put("restaurant_default", updated);
            return updated;
        }

        private static PlatformCallScreenSeedSlide slide(String title, int sortOrder) {
            return new PlatformCallScreenSeedSlide(
                UUID.randomUUID(),
                title,
                "副标题",
                "标语",
                sortOrder,
                "active",
                0
            );
        }
    }
}
