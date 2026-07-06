package com.rpb.reservation.i18n.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class I18nCatalogMigrationSourceValidationTest {
    @Test
    void catalogRegistryMigrationAddsControlledKeysAndPlatformDefaults() throws Exception {
        String migration = Files.readString(Path.of(
            "src", "main", "resources", "db", "migration", "V028__i18n_message_catalog_registry.sql"
        ));

        assertThat(migration)
            .contains("alter table i18n_message_catalog")
            .contains("add column if not exists version")
            .contains("create table if not exists i18n_message_key_registry")
            .contains("tenant_editable boolean not null default true")
            .contains("placeholder_names text[] not null default '{}'::text[]")
            .contains("ck_i18n_message_key_registry_kind")
            .contains("reservation.share.whatsapp_template")
            .contains("reservation.share.restaurant_reservation_confirmation_v1")
            .contains("public_booking.prompt.policy")
            .contains("call_screen.queue.notice")
            .contains("reservation.meal_period.' || seed.period_key || '.display_name")
            .contains("call_screen.seed.' || seed.seed_key || '.slide_' || slide.sort_order || '.title")
            .contains("status.reservation.confirmed', 'status', 'reservation'")
            .contains("'status.reservation.confirmed', 'zh-CN', '已预约'")
            .contains("'status.reservation.confirmed', 'en-SG', 'Booked'")
            .contains("from platform_reservation_share_template_seeds seed")
            .contains("store.reservation_share_template")
            .contains("from platform_reservation_meal_period_seeds seed")
            .contains("from platform_call_screen_ad_seed_slides slide")
            .contains("from tenant_call_screen_text_slides slide")
            .contains("where existing.tenant_id is null")
            .contains("where not exists");
    }

    @Test
    void catalogEscapedNewlineMigrationNormalizesTemplateMessagesOnly() throws Exception {
        String migration = Files.readString(Path.of(
            "src", "main", "resources", "db", "migration", "V029__normalize_i18n_template_escaped_newlines.sql"
        ));

        assertThat(migration)
            .contains("join i18n_message_key_registry registry")
            .contains("registry.text_kind = 'template'")
            .contains("chr(92) || 'r' || chr(92) || 'n'")
            .contains("chr(92) || 'n'")
            .contains("chr(92) || 'r'")
            .contains("version = catalog.version + 1")
            .contains("catalog.message is distinct from normalized.normalized_message");
    }
}
