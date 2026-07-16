package com.rpb.reservation.reservation.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReservationShareTemplateDefaultMigrationTest {

    @Test
    void migrationUpdatesPlatformSeedAndOnlyStoresUsingOldDefaultTemplate() throws Exception {
        Path migrationPath = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V018__dedupe_reservation_share_template.sql"
        );

        assertThat(migrationPath).exists();
        String migration = Files.readString(migrationPath);

        assertThat(migration)
            .contains("template_md5")
            .contains("'0330ca8282c4ce3c608ebf3fa23c9c4a'")
            .contains("'470d74c4357fd9e699ad6556e125d7a7'")
            .contains("convert_from(decode(")
            .contains("), 'UTF8') as new_template")
            .contains("update platform_reservation_share_template_seeds seed")
            .contains("md5(convert_to(replace(replace(btrim(seed.template_text)")
            .contains("in (select template_md5 from old_default_templates)")
            .contains("update stores")
            .contains("md5(convert_to(replace(replace(btrim(stores.reservation_share_template)")
            .contains("version = stores.version + 1")
            .contains("seed.template_text is distinct from templates.new_template")
            .contains("stores.reservation_share_template is distinct from templates.new_template")
            .doesNotContain("stores.reservation_share_template is not null")
            .doesNotContain("stores.reservation_share_template <> templates.new_template")
            .doesNotContain("Google Map：")
            .doesNotContain("{{storePhone}} | {{storeAddress}}");
    }

    @Test
    void catchUpMigrationRefreshesLateLegacyDefaultsFromActiveSeedOnly() throws Exception {
        Path migrationPath = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V027__refresh_legacy_reservation_share_defaults.sql"
        );

        assertThat(migrationPath).exists();
        String migration = Files.readString(migrationPath);

        assertThat(migration)
            .contains("old_default_templates(template_md5)")
            .contains("'0330ca8282c4ce3c608ebf3fa23c9c4a'")
            .contains("'470d74c4357fd9e699ad6556e125d7a7'")
            .contains("active_seed as")
            .contains("platform_reservation_share_template_seeds")
            .contains("seed_key = 'restaurant_reservation_confirmation_v1'")
            .contains("status = 'active'")
            .contains("deleted_at is null")
            .contains("update stores")
            .contains("reservation_share_template = active_seed.template_text")
            .contains("version = stores.version + 1")
            .contains("md5(convert_to(replace(replace(btrim(stores.reservation_share_template)")
            .contains("in (select template_md5 from old_default_templates)")
            .contains("stores.reservation_share_template is distinct from active_seed.template_text")
            .doesNotContain("stores.reservation_share_template is not null")
            .doesNotContain("stores.reservation_share_template <> active_seed.template_text");
    }

    @Test
    void englishDefaultMigrationUpdatesOnlyKnownSystemDefaults() throws Exception {
        Path migrationPath = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V031__english_reservation_share_default_template.sql"
        );

        assertThat(migrationPath).exists();
        String migration = Files.readString(migrationPath);

        assertThat(migration)
            .contains("known_platform_defaults(template_md5)")
            .contains("'0330ca8282c4ce3c608ebf3fa23c9c4a'")
            .contains("'470d74c4357fd9e699ad6556e125d7a7'")
            .contains("'2a5f65d472c69f8fb8eb60a64c6b4e1c'")
            .contains("known_i18n_english_defaults(template_md5)")
            .contains("'cca07e5143a0d5e1a59040c6bd0aa77a'")
            .contains("Restaurant reservation confirmation template V1")
            .contains("locale = 'en-SG'")
            .contains("Dear {{contactName}} {{guestSalutation}}")
            .contains("Booking no.: {{reservationNo}}")
            .contains("Hold time: To protect every guest's dining experience")
            .contains("update platform_reservation_share_template_seeds seed")
            .contains("update stores")
            .contains("update i18n_message_catalog catalog")
            .contains("insert into i18n_message_catalog")
            .contains("md5(convert_to(replace(replace(btrim(seed.template_text)")
            .contains("in (select template_md5 from known_platform_defaults)")
            .contains("catalog.locale = 'en-SG'")
            .contains("version = seed.version + 1")
            .contains("version = stores.version + 1")
            .contains("version = catalog.version + 1")
            .doesNotContain("where stores.reservation_share_template is not null")
            .doesNotContain("catalog.locale = 'zh-CN'");
    }

    @Test
    void englishCatchUpMigrationTargetsVerifiedProductionDefaultHash() throws Exception {
        Path migrationPath = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V032__catch_up_english_reservation_share_seed.sql"
        );

        assertThat(migrationPath).exists();
        String migration = Files.readString(migrationPath);

        assertThat(migration)
            .contains("V014 default template")
            .contains("'912f67b410744e46ca37fdff6bf47466'")
            .contains("Restaurant reservation confirmation template V1")
            .contains("locale = 'en-SG'")
            .contains("Dear {{contactName}} {{guestSalutation}}")
            .contains("update platform_reservation_share_template_seeds seed")
            .contains("update stores")
            .contains("md5(convert_to(replace(replace(btrim(seed.template_text)")
            .contains("in (select template_md5 from known_platform_defaults)")
            .contains("version = seed.version + 1")
            .contains("version = stores.version + 1")
            .doesNotContain("i18n_message_catalog")
            .doesNotContain("catalog.locale = 'zh-CN'");
    }

    @Test
    void chinesePlatformTemplateCatchUpMigrationBackfillsOnlyMissingPlatformCatalogMessage() throws Exception {
        Path migrationPath = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V033__ensure_chinese_reservation_share_platform_template.sql"
        );

        assertThat(migrationPath).exists();
        String migration = Files.readString(migrationPath);

        assertThat(migration)
            .contains("reservation.share.restaurant_reservation_confirmation_v1")
            .contains("locale = 'zh-CN'")
            .contains("'zh-CN', chinese_default.template_text, 'active'")
            .contains("where not exists")
            .contains("tenant_id is null")
            .contains("store_id is null")
            .contains("感谢您选择 {{storeName}}")
            .contains("预订编号：{{reservationNo}}")
            .doesNotContain("update i18n_message_catalog")
            .doesNotContain("update stores")
            .doesNotContain("locale = 'en-SG'");
    }
}
