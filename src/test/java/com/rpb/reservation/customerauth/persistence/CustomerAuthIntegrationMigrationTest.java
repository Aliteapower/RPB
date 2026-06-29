package com.rpb.reservation.customerauth.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CustomerAuthIntegrationMigrationTest {

    @Test
    void migrationCreatesTenantMaintainableEmailAndOAuthSettings() throws Exception {
        String migration = Files.readString(Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V022__customer_auth_integration_settings.sql"
        ));

        assertThat(migration)
            .contains("create table if not exists store_customer_email_settings")
            .contains("tenant_id uuid not null references tenants(id)")
            .contains("store_id uuid not null")
            .contains("enabled boolean not null default false")
            .contains("smtp_password_secret text null")
            .contains("constraint fk_store_customer_email_settings_store_scope")
            .contains("foreign key (store_id, tenant_id) references stores(id, tenant_id)")
            .contains("create unique index if not exists ux_store_customer_email_settings_scope_active")
            .contains("on store_customer_email_settings (tenant_id, store_id)");

        assertThat(migration)
            .contains("create table if not exists store_customer_oauth_provider_settings")
            .contains("provider text not null")
            .contains("client_id text null")
            .contains("client_secret_secret text null")
            .contains("constraint ck_store_customer_oauth_provider")
            .contains("provider in ('google', 'facebook')")
            .contains("create unique index if not exists ux_store_customer_oauth_provider_settings_active")
            .contains("on store_customer_oauth_provider_settings (tenant_id, store_id, provider)");
    }
}
