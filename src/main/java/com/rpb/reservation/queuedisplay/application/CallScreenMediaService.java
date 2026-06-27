package com.rpb.reservation.queuedisplay.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queuedisplay.persistence.CallScreenMediaRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CallScreenMediaService {
    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_VIDEO_BYTES = 80L * 1024L * 1024L;
    private static final Map<String, MediaTypeSpec> SUPPORTED_TYPES = Map.of(
        "image/jpeg", new MediaTypeSpec("image", ".jpg", MAX_IMAGE_BYTES),
        "image/png", new MediaTypeSpec("image", ".png", MAX_IMAGE_BYTES),
        "image/webp", new MediaTypeSpec("image", ".webp", MAX_IMAGE_BYTES),
        "video/mp4", new MediaTypeSpec("video", ".mp4", MAX_VIDEO_BYTES),
        "video/webm", new MediaTypeSpec("video", ".webm", MAX_VIDEO_BYTES)
    );

    private final CallScreenMediaRepository repository;
    private final CallScreenMediaStorage storage;

    public CallScreenMediaService(CallScreenMediaRepository repository, CallScreenMediaStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Transactional
    public CallScreenMediaAsset uploadTenantMedia(StoreScope scope, MultipartFile file) {
        StoredUpload upload = storeUpload("tenant/" + scope.tenantId().value(), file, false);
        CallScreenMediaAsset asset = repository.createAsset(
            "tenant",
            scope.tenantId().value(),
            upload.mediaKind(),
            upload.contentType(),
            upload.byteSize(),
            upload.originalFilename(),
            upload.storageKey()
        );
        return withMediaUrl(asset, tenantAdminMediaUrl(scope, asset.id()));
    }

    @Transactional
    public CallScreenMediaAsset uploadPlatformMedia(MultipartFile file) {
        StoredUpload upload = storeUpload("platform", file, false);
        CallScreenMediaAsset asset = repository.createAsset(
            "platform",
            null,
            upload.mediaKind(),
            upload.contentType(),
            upload.byteSize(),
            upload.originalFilename(),
            upload.storageKey()
        );
        return withMediaUrl(asset, platformMediaUrl(asset.id()));
    }

    @Transactional
    public CallScreenMediaAsset uploadTenantLogoMedia(UUID tenantId, MultipartFile file) {
        StoredUpload upload = storeUpload("tenant/" + tenantId + "/logo", file, true);
        CallScreenMediaAsset asset = repository.createAsset(
            "tenant",
            tenantId,
            upload.mediaKind(),
            upload.contentType(),
            upload.byteSize(),
            upload.originalFilename(),
            upload.storageKey()
        );
        return withMediaUrl(asset, tenantLogoMediaUrl(tenantId, asset.id()));
    }

    @Transactional
    public CallScreenMediaAsset uploadPlatformLogoMedia(MultipartFile file) {
        StoredUpload upload = storeUpload("platform/logo", file, true);
        CallScreenMediaAsset asset = repository.createAsset(
            "platform",
            null,
            upload.mediaKind(),
            upload.contentType(),
            upload.byteSize(),
            upload.originalFilename(),
            upload.storageKey()
        );
        return withMediaUrl(asset, platformMediaUrl(asset.id()));
    }

    @Transactional(readOnly = true)
    public CallScreenMediaContent readTenantMedia(StoreScope scope, UUID assetId) {
        return content(repository.findTenantAsset(scope.tenantId().value(), assetId)
            .orElseThrow(() -> new CallScreenMediaServiceException(CallScreenMediaServiceErrorCode.MEDIA_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public CallScreenMediaContent readTenantLogoMedia(UUID tenantId, UUID assetId) {
        return content(repository.findTenantLogoAsset(tenantId, assetId)
            .orElseThrow(() -> new CallScreenMediaServiceException(CallScreenMediaServiceErrorCode.MEDIA_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public CallScreenMediaContent readPlatformMedia(UUID assetId) {
        return content(repository.findPlatformAsset(assetId)
            .orElseThrow(() -> new CallScreenMediaServiceException(CallScreenMediaServiceErrorCode.MEDIA_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public CallScreenMediaContent readQueueDisplayMedia(StoreScope scope, UUID assetId) {
        return content(repository.findQueueDisplayAsset(scope, assetId)
            .orElseThrow(() -> new CallScreenMediaServiceException(CallScreenMediaServiceErrorCode.MEDIA_NOT_FOUND)));
    }

    private StoredUpload storeUpload(String keyPrefix, MultipartFile file, boolean imageOnly) {
        MediaTypeSpec spec = validate(file);
        if (imageOnly && !"image".equals(spec.mediaKind())) {
            throw new CallScreenMediaServiceException(CallScreenMediaServiceErrorCode.REQUEST_INVALID);
        }
        String storageKey = keyPrefix + "/" + UUID.randomUUID() + spec.extension();
        try (InputStream input = file.getInputStream()) {
            storage.store(storageKey, input);
        } catch (IOException | RuntimeException exception) {
            throw new CallScreenMediaServiceException(CallScreenMediaServiceErrorCode.PERSISTENCE_ERROR);
        }
        return new StoredUpload(
            spec.mediaKind(),
            normalizeContentType(file.getContentType()),
            file.getSize(),
            originalFilename(file.getOriginalFilename()),
            storageKey
        );
    }

    private static MediaTypeSpec validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new CallScreenMediaServiceException(CallScreenMediaServiceErrorCode.REQUEST_INVALID);
        }
        String contentType = normalizeContentType(file.getContentType());
        MediaTypeSpec spec = SUPPORTED_TYPES.get(contentType);
        if (spec == null || file.getSize() > spec.maxBytes()) {
            throw new CallScreenMediaServiceException(CallScreenMediaServiceErrorCode.REQUEST_INVALID);
        }
        try (InputStream input = file.getInputStream()) {
            if (!hasExpectedSignature(contentType, input.readNBytes(16))) {
                throw new CallScreenMediaServiceException(CallScreenMediaServiceErrorCode.REQUEST_INVALID);
            }
        } catch (IOException exception) {
            throw new CallScreenMediaServiceException(CallScreenMediaServiceErrorCode.PERSISTENCE_ERROR);
        }
        return spec;
    }

    private static boolean hasExpectedSignature(String contentType, byte[] header) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> asciiAt(header, 0, "RIFF") && asciiAt(header, 8, "WEBP");
            case "video/mp4" -> asciiAt(header, 4, "ftyp");
            case "video/webm" -> startsWith(header, 0x1A, 0x45, 0xDF, 0xA3);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean asciiAt(byte[] bytes, int offset, String expected) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + expectedBytes.length) {
            return false;
        }
        for (int i = 0; i < expectedBytes.length; i++) {
            if (bytes[offset + i] != expectedBytes[i]) {
                return false;
            }
        }
        return true;
    }

    private CallScreenMediaContent content(CallScreenMediaAsset asset) {
        Resource resource = storage.load(asset.storageKey());
        if (!resource.exists() || !resource.isReadable()) {
            throw new CallScreenMediaServiceException(CallScreenMediaServiceErrorCode.MEDIA_NOT_FOUND);
        }
        return new CallScreenMediaContent(resource, asset.contentType(), asset.byteSize());
    }

    public static String tenantAdminMediaUrl(StoreScope scope, UUID assetId) {
        return "/api/v1/stores/" + scope.storeId().value() + "/tenant-admin/call-screen/media/" + assetId;
    }

    public static String queueDisplayMediaUrl(StoreScope scope, UUID assetId) {
        return "/api/v1/stores/" + scope.storeId().value() + "/queue-display/media/" + assetId;
    }

    public static String platformMediaUrl(UUID assetId) {
        return "/api/v1/platform/call-screen/media/" + assetId;
    }

    public static String tenantLogoMediaUrl(UUID tenantId, UUID assetId) {
        return "/api/v1/platform/tenants/" + tenantId + "/logo/media/" + assetId;
    }

    private static CallScreenMediaAsset withMediaUrl(CallScreenMediaAsset asset, String mediaUrl) {
        return new CallScreenMediaAsset(
            asset.id(),
            asset.ownerScope(),
            asset.tenantId(),
            asset.mediaKind(),
            asset.contentType(),
            asset.byteSize(),
            asset.originalFilename(),
            asset.storageKey(),
            mediaUrl,
            asset.version()
        );
    }

    private static String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
    }

    private static String originalFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "call-screen-media";
        }
        return filename.replace("\\", "/").substring(filename.replace("\\", "/").lastIndexOf('/') + 1);
    }

    private record MediaTypeSpec(String mediaKind, String extension, long maxBytes) {
    }

    private record StoredUpload(
        String mediaKind,
        String contentType,
        long byteSize,
        String originalFilename,
        String storageKey
    ) {
    }
}
