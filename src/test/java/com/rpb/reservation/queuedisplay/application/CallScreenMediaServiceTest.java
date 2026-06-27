package com.rpb.reservation.queuedisplay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queuedisplay.persistence.CallScreenMediaRepository;
import com.rpb.reservation.tenant.value.TenantId;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

class CallScreenMediaServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000971");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000971");

    private FakeCallScreenMediaRepository repository;
    private FakeCallScreenMediaStorage storage;
    private CallScreenMediaService service;

    @BeforeEach
    void setUp() {
        repository = new FakeCallScreenMediaRepository();
        storage = new FakeCallScreenMediaStorage();
        service = new CallScreenMediaService(repository, storage);
    }

    @Test
    void acceptsTenantImageWhenDeclaredTypeMatchesFileSignature() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "poster.png",
            "image/png",
            new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00}
        );

        CallScreenMediaAsset asset = service.uploadTenantMedia(scope(), file);

        assertThat(asset.mediaKind()).isEqualTo("image");
        assertThat(asset.contentType()).isEqualTo("image/png");
        assertThat(asset.originalFilename()).isEqualTo("poster.png");
        assertThat(asset.storageKey()).startsWith("tenant/" + TENANT_ID + "/");
        assertThat(asset.mediaUrl()).isEqualTo("/api/v1/stores/" + STORE_ID + "/tenant-admin/call-screen/media/" + asset.id());
        assertThat(storage.bytes).containsKey(asset.storageKey());
    }

    @Test
    void acceptsPlatformVideoWhenDeclaredTypeMatchesFileSignature() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "intro.mp4",
            "video/mp4",
            new byte[] {0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x6D, 0, 0, 0, 0}
        );

        CallScreenMediaAsset asset = service.uploadPlatformMedia(file);

        assertThat(asset.ownerScope()).isEqualTo("platform");
        assertThat(asset.mediaKind()).isEqualTo("video");
        assertThat(asset.contentType()).isEqualTo("video/mp4");
        assertThat(asset.storageKey()).startsWith("platform/");
        assertThat(asset.mediaUrl()).isEqualTo("/api/v1/platform/call-screen/media/" + asset.id());
        assertThat(storage.bytes).containsKey(asset.storageKey());
    }

    @Test
    void acceptsTenantLogoImageWithTenantScopedPreviewUrl() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "brand.webp",
            "image/webp",
            new byte[] {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50}
        );

        CallScreenMediaAsset asset = service.uploadTenantLogoMedia(TENANT_ID, file);

        assertThat(asset.ownerScope()).isEqualTo("tenant");
        assertThat(asset.tenantId()).isEqualTo(TENANT_ID);
        assertThat(asset.mediaKind()).isEqualTo("image");
        assertThat(asset.storageKey()).startsWith("tenant/" + TENANT_ID + "/logo/");
        assertThat(asset.mediaUrl()).isEqualTo("/api/v1/platform/tenants/" + TENANT_ID + "/logo/media/" + asset.id());
    }

    @Test
    void rejectsVideoForLogoUploads() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "intro.mp4",
            "video/mp4",
            new byte[] {0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x6D, 0, 0, 0, 0}
        );

        assertThatThrownBy(() -> service.uploadPlatformLogoMedia(file))
            .isInstanceOf(CallScreenMediaServiceException.class)
            .extracting("code")
            .isEqualTo(CallScreenMediaServiceErrorCode.REQUEST_INVALID);
        assertThat(storage.bytes).isEmpty();
        assertThat(repository.assets).isEmpty();
    }

    @Test
    void rejectsForgedContentTypeBeforeStoringBytes() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "fake.mp4",
            "video/mp4",
            "not a video".getBytes()
        );

        assertThatThrownBy(() -> service.uploadTenantMedia(scope(), file))
            .isInstanceOf(CallScreenMediaServiceException.class)
            .extracting("code")
            .isEqualTo(CallScreenMediaServiceErrorCode.REQUEST_INVALID);
        assertThat(storage.bytes).isEmpty();
        assertThat(repository.assets).isEmpty();
    }

    private static StoreScope scope() {
        return new StoreScope(new TenantId(TENANT_ID), STORE_ID);
    }

    private static final class FakeCallScreenMediaRepository implements CallScreenMediaRepository {
        private final Map<UUID, CallScreenMediaAsset> assets = new LinkedHashMap<>();

        @Override
        public CallScreenMediaAsset createAsset(
            String ownerScope,
            UUID tenantId,
            String mediaKind,
            String contentType,
            long byteSize,
            String originalFilename,
            String storageKey
        ) {
            UUID assetId = UUID.randomUUID();
            CallScreenMediaAsset asset = new CallScreenMediaAsset(
                assetId,
                ownerScope,
                tenantId,
                mediaKind,
                contentType,
                byteSize,
                originalFilename,
                storageKey,
                null,
                0
            );
            assets.put(assetId, asset);
            return asset;
        }

        @Override
        public Optional<CallScreenMediaAsset> findTenantAsset(UUID tenantId, UUID assetId) {
            return Optional.ofNullable(assets.get(assetId))
                .filter(asset -> "tenant".equals(asset.ownerScope()) && tenantId.equals(asset.tenantId()));
        }

        @Override
        public Optional<CallScreenMediaAsset> findTenantLogoAsset(UUID tenantId, UUID assetId) {
            return findTenantAsset(tenantId, assetId)
                .filter(asset -> "image".equals(asset.mediaKind()));
        }

        @Override
        public Optional<CallScreenMediaAsset> findPlatformAsset(UUID assetId) {
            return Optional.ofNullable(assets.get(assetId))
                .filter(asset -> "platform".equals(asset.ownerScope()) && asset.tenantId() == null);
        }

        @Override
        public Optional<CallScreenMediaAsset> findQueueDisplayAsset(StoreScope scope, UUID assetId) {
            return findTenantAsset(scope.tenantId().value(), assetId);
        }
    }

    private static final class FakeCallScreenMediaStorage implements CallScreenMediaStorage {
        private final Map<String, byte[]> bytes = new LinkedHashMap<>();

        @Override
        public void store(String storageKey, InputStream input) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            input.transferTo(output);
            bytes.put(storageKey, output.toByteArray());
        }

        @Override
        public Resource load(String storageKey) {
            return new ByteArrayResource(bytes.getOrDefault(storageKey, new byte[0]));
        }
    }
}
