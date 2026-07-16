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

    @Test
    void migrationBackfillsTenantCodeHostAliases() throws Exception {
        Path migrationPath = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V039__tenant_host_alias_backfill.sql"
        );

        assertThat(migrationPath).exists();

        String migration = Files.readString(migrationPath);

        assertThat(migration)
            .contains("insert into tenant_host_aliases")
            .contains("tenant_id, alias_code, alias_type, default_store_id, status")
            .contains("from tenants tenant")
            .contains("'tenant'")
            .contains("tenant.deleted_at is null")
            .contains("not exists")
            .contains("lower(alias.alias_code) = lower(tenant.tenant_code)")
            .contains("on conflict do nothing")
            .doesNotContain("'store'");
    }

    @Test
    void migrationAddsPublicHostBindingsForCertificateAutomation() throws Exception {
        Path migrationPath = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V040__public_host_bindings.sql"
        );

        assertThat(migrationPath).exists();

        String migration = Files.readString(migrationPath);

        assertThat(migration)
            .contains("create table public_host_bindings")
            .contains("host_alias_id uuid not null")
            .contains("tenant_id uuid not null references tenants(id)")
            .contains("host_prefix text not null")
            .contains("hostname text not null")
            .contains("host_type text not null")
            .contains("tls_status text not null")
            .contains("certificate_name text null")
            .contains("last_error text null")
            .contains("foreign key (host_alias_id, tenant_id) references tenant_host_aliases(id, tenant_id)")
            .contains("host_type in ('tenant', 'store')")
            .contains("tls_status in ('pending', 'covered', 'failed', 'archived')")
            .contains("create unique index ux_public_host_bindings_alias_active")
            .contains("create unique index ux_public_host_bindings_hostname_active")
            .contains("create index ix_public_host_bindings_tls_status");
    }
}
