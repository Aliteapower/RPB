package com.rpb.reservation.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlatformTenantStructureMigrationSourceValidationTest {
    @Test
    void migrationAddsOperatingEntitiesStoreScopeAndHostAliases() throws Exception {
        Path migrationPath = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V035__tenant_operating_entities_and_store_structure.sql"
        );

        assertThat(migrationPath).exists();

        String migration = Files.readString(migrationPath);

        assertThat(migration)
            .contains("create table operating_entities")
            .contains("tenant_id uuid not null references tenants(id)")
            .contains("constraint uq_operating_entities_id_tenant unique (id, tenant_id)")
            .contains("create unique index ux_operating_entities_tenant_code_active")
            .contains("on operating_entities (tenant_id, lower(entity_code))")
            .contains("alter table stores")
            .contains("add column operating_entity_id uuid null")
            .contains("constraint fk_stores_operating_entity_scope")
            .contains("references operating_entities(id, tenant_id)")
            .contains("create table tenant_host_aliases")
            .contains("alias_type in ('tenant', 'store')")
            .contains("constraint ck_tenant_host_aliases_default_store_required")
            .contains("foreign key (default_store_id, tenant_id) references stores(id, tenant_id)")
            .contains("create unique index ux_tenant_host_aliases_code_active")
            .contains("on tenant_host_aliases (lower(alias_code))");
    }

    @Test
    void migrationBackfillsOnlyUnambiguousStoreHostAliases() throws Exception {
        Path migrationPath = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V037__tenant_store_host_alias_backfill.sql"
        );

        assertThat(migrationPath).exists();

        String migration = Files.readString(migrationPath);

        assertThat(migration)
            .contains("globally_unique_active_store_codes")
            .contains("having count(*) = 1")
            .contains("tenant_host_aliases")
            .contains("alias_type")
            .contains("'store'")
            .contains("default_store_id")
            .contains("not exists")
            .contains("tenant_code");
    }

    @Test
    void migrationBackfillsDefaultOperatingEntityForTenantsWithoutStructure() throws Exception {
        Path migrationPath = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V038__tenant_default_operating_entity_backfill.sql"
        );

        assertThat(migrationPath).exists();

        String migration = Files.readString(migrationPath);

        assertThat(migration)
            .contains("insert into operating_entities")
            .contains("tenant_id, entity_code, display_name, status")
            .contains("from tenants tenant")
            .contains("not exists")
            .contains("existing.tenant_id = tenant.id")
            .contains("tenant.deleted_at is null")
            .contains("on conflict do nothing")
            .doesNotContain("insert into stores");
    }
}
