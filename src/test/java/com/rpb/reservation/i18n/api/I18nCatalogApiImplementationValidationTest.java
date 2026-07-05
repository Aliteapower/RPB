package com.rpb.reservation.i18n.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class I18nCatalogApiImplementationValidationTest {
    @Test
    void apiKeepsPlatformAndTenantCatalogBoundariesExplicit() throws Exception {
        String platformController = Files.readString(Path.of(
            "src", "main", "java", "com", "rpb", "reservation", "i18n", "api", "PlatformI18nCatalogController.java"
        ));
        String tenantController = Files.readString(Path.of(
            "src", "main", "java", "com", "rpb", "reservation", "i18n", "api", "TenantAdminI18nCatalogController.java"
        ));
        String service = Files.readString(Path.of(
            "src", "main", "java", "com", "rpb", "reservation", "i18n", "application", "I18nCatalogService.java"
        ));
        String repository = Files.readString(Path.of(
            "src", "main", "java", "com", "rpb", "reservation", "i18n", "persistence", "JdbcI18nCatalogRepository.java"
        ));
        String localSecurity = Files.readString(Path.of(
            "src", "main", "java", "com", "rpb", "reservation", "walkin", "auth", "LocalRuntimeSecurityConfiguration.java"
        ));

        assertThat(platformController)
            .contains("@RequestMapping(\"/api/v1/platform/i18n/catalog\")")
            .contains("@GetMapping")
            .contains("@PatchMapping")
            .contains("CurrentActorProvider")
            .contains("platform.tenant.manage")
            .doesNotContain("JdbcTemplate")
            .doesNotContain("i18n_message_catalog");

        assertThat(tenantController)
            .contains("@RequestMapping(\"/api/v1/stores/{storeId}/tenant-admin/i18n/catalog\")")
            .contains("TenantAdminScopeResolver")
            .contains("StoreScope scope")
            .contains("updateTenantCatalog(scope")
            .doesNotContain("JdbcTemplate")
            .doesNotContain("i18n_message_catalog");

        assertThat(service)
            .contains("I18nCatalogRepository")
            .contains("firstActive(store, tenant, platform)")
            .contains("frontend_fallback")
            .contains("unknownPlaceholders")
            .doesNotContain("JdbcTemplate")
            .doesNotContain("@RequestMapping");

        assertThat(repository)
            .contains("i18n_message_key_registry")
            .contains("i18n_message_catalog")
            .contains("tenant_id = ? and store_id = ?")
            .contains("tenant_id is null and store_id is null")
            .contains("version = version + 1");

        assertThat(localSecurity)
            .contains("/api/v1/platform/i18n/catalog")
            .contains("HttpMethod.GET")
            .contains("HttpMethod.PATCH");
    }
}
