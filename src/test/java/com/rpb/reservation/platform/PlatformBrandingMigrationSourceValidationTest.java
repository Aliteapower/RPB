package com.rpb.reservation.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlatformBrandingMigrationSourceValidationTest {
    @Test
    void singleMigrationAddsTenantLogoPlatformProfileAndSocialLinks() throws Exception {
        Path migrationPath = Path.of("src", "main", "resources", "db", "migration", "V012__platform_branding_profile.sql");

        assertThat(migrationPath).exists();

        String migration = Files.readString(migrationPath);

        assertThat(migration)
            .contains("alter table tenants")
            .contains("logo_media_asset_id")
            .contains("fk_tenants_logo_media_asset")
            .contains("create table if not exists platform_profile")
            .contains("platform_name")
            .contains("uen")
            .contains("address")
            .contains("phone")
            .contains("email")
            .contains("website")
            .contains("logo_media_asset_id")
            .contains("create table if not exists platform_social_links")
            .contains("display_name")
            .contains("url")
            .contains("sort_order")
            .contains("ck_platform_social_links_status")
            .contains("ux_platform_social_links_active_sort")
            .contains("insert into platform_profile");
    }
}
