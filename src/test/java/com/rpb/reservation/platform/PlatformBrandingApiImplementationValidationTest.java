package com.rpb.reservation.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlatformBrandingApiImplementationValidationTest {
    @Test
    void tenantApiExposesLogoUploadClearAndPreviewEndpoints() throws Exception {
        Path controllerPath = Path.of("src", "main", "java", "com", "rpb", "reservation", "platform", "api", "PlatformTenantController.java");
        Path servicePath = Path.of("src", "main", "java", "com", "rpb", "reservation", "platform", "application", "PlatformTenantService.java");
        Path repositoryPath = Path.of("src", "main", "java", "com", "rpb", "reservation", "platform", "persistence", "PlatformTenantRepository.java");
        Path responsePath = Path.of("src", "main", "java", "com", "rpb", "reservation", "platform", "api", "PlatformTenantItemResponse.java");

        String controller = Files.readString(controllerPath);
        String service = Files.readString(servicePath);
        String repository = Files.readString(repositoryPath);
        String response = Files.readString(responsePath);

        assertThat(controller)
            .contains("@PostMapping(value = \"/{tenantId}/logo\", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)")
            .contains("@DeleteMapping(\"/{tenantId}/logo\")")
            .contains("@GetMapping(\"/{tenantId}/logo/media/{assetId}\")")
            .contains("uploadTenantLogo")
            .contains("clearTenantLogo")
            .contains("readTenantLogoMedia");

        assertThat(service)
            .contains("uploadTenantLogo")
            .contains("clearTenantLogo")
            .contains("CallScreenMediaService")
            .contains("uploadTenantLogoMedia");

        assertThat(repository)
            .contains("logo_media_asset_id")
            .contains("updateLogoMediaAsset");

        assertThat(response)
            .contains("logoMediaUrl")
            .contains("tenantLogoMediaUrl");
    }

    @Test
    void platformProfileApiMaintainsProfileAndSocialLinksWithLogoUploads() throws Exception {
        Path controllerPath = Path.of("src", "main", "java", "com", "rpb", "reservation", "platform", "api", "PlatformProfileController.java");
        Path servicePath = Path.of("src", "main", "java", "com", "rpb", "reservation", "platform", "application", "PlatformProfileService.java");
        Path repositoryPath = Path.of("src", "main", "java", "com", "rpb", "reservation", "platform", "persistence", "PlatformProfileRepository.java");

        assertThat(controllerPath).exists();
        assertThat(servicePath).exists();
        assertThat(repositoryPath).exists();

        String controller = Files.readString(controllerPath);
        String service = Files.readString(servicePath);
        String repository = Files.readString(repositoryPath);

        assertThat(controller)
            .contains("@RequestMapping(\"/api/v1/platform/profile\")")
            .contains("@GetMapping")
            .contains("@PatchMapping")
            .contains("@PostMapping(value = \"/logo\", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)")
            .contains("@DeleteMapping(\"/logo\")")
            .contains("@PostMapping(\"/social-links\")")
            .contains("@PatchMapping(\"/social-links/{linkId}\")")
            .contains("@DeleteMapping(\"/social-links/{linkId}\")")
            .contains("@PostMapping(value = \"/social-links/{linkId}/logo\", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)")
            .contains("@DeleteMapping(\"/social-links/{linkId}/logo\")")
            .contains("platform.tenant.manage");

        assertThat(service)
            .contains("updateProfile")
            .contains("uploadProfileLogo")
            .contains("clearProfileLogo")
            .contains("createSocialLink")
            .contains("updateSocialLink")
            .contains("deleteSocialLink")
            .contains("uploadSocialLinkLogo")
            .contains("clearSocialLinkLogo")
            .contains("uploadPlatformLogoMedia");

        assertThat(repository)
            .contains("platform_profile")
            .contains("platform_social_links")
            .contains("logo_media_asset_id")
            .contains("upsertProfile")
            .contains("createSocialLink")
            .contains("updateSocialLink");
    }
}
