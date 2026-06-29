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
}
