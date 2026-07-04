package com.rpb.reservation.publicbooking.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PublicBookingMigrationTest {

    @Test
    void migrationCreatesCustomerAuthAndPublicBookingTables() throws Exception {
        String migration = Files.readString(Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V024__public_booking_customer_auth.sql"
        ));

        assertThat(migration)
            .contains("create table if not exists customer_auth_accounts")
            .contains("tenant_id uuid not null references tenants(id)")
            .contains("customer_id uuid not null")
            .contains("email text not null")
            .contains("constraint fk_customer_auth_accounts_customer_scope")
            .contains("references customers(id, tenant_id)")
            .contains("constraint ck_customer_auth_accounts_status")
            .contains("status in ('active', 'disabled', 'archived')")
            .contains("create unique index if not exists ux_customer_auth_accounts_email_active")
            .contains("on customer_auth_accounts (tenant_id, lower(email))");

        assertThat(migration)
            .contains("create table if not exists customer_auth_identities")
            .contains("provider text not null")
            .contains("provider_subject text not null")
            .contains("constraint ck_customer_auth_identities_provider")
            .contains("provider in ('email', 'google', 'facebook')")
            .contains("create unique index if not exists ux_customer_auth_identities_provider_subject_active")
            .contains("on customer_auth_identities (tenant_id, provider, provider_subject)");

        assertThat(migration)
            .contains("create table if not exists customer_auth_sessions")
            .contains("session_hash text not null")
            .contains("expires_at timestamptz not null")
            .contains("constraint ck_customer_auth_sessions_status")
            .contains("status in ('active', 'revoked', 'expired')")
            .contains("create unique index if not exists ux_customer_auth_sessions_hash_active");

        assertThat(migration)
            .contains("create table if not exists customer_email_login_codes")
            .contains("code_hash text not null")
            .contains("constraint ck_customer_email_login_codes_status")
            .contains("status in ('created', 'consumed', 'expired', 'failed')")
            .contains("create index if not exists ix_customer_email_login_codes_lookup");

        assertThat(migration)
            .contains("create table if not exists store_public_booking_settings")
            .contains("tenant_id uuid not null references tenants(id)")
            .contains("store_id uuid not null")
            .contains("enabled boolean not null default false")
            .contains("default_quota_mode text not null default 'percentage'")
            .contains("default_quota_percent integer not null default 20")
            .contains("constraint fk_store_public_booking_settings_store_scope")
            .contains("foreign key (store_id, tenant_id) references stores(id, tenant_id)")
            .contains("constraint ck_store_public_booking_settings_quota_mode")
            .contains("default_quota_mode in ('percentage', 'table_count', 'guest_count')");

        assertThat(migration)
            .contains("create table if not exists store_public_booking_quota_overrides")
            .contains("business_date date not null")
            .contains("period_key text null")
            .contains("quota_mode text not null")
            .contains("quota_mode in ('percentage', 'table_count', 'guest_count', 'closed')")
            .contains("create unique index if not exists ux_store_public_booking_quota_override_active")
            .contains("on store_public_booking_quota_overrides (tenant_id, store_id, business_date, coalesce(period_key, ''))");
    }

    @Test
    void migrationCreatesPublicBookingAvailabilityRulesForWeeklyAndDateException() throws Exception {
        String migration = Files.readString(Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V026__public_booking_availability_rules.sql"
        ));

        assertThat(migration)
            .contains("create table if not exists store_public_booking_availability_rules")
            .contains("tenant_id uuid not null references tenants(id)")
            .contains("store_id uuid not null")
            .contains("rule_type text not null")
            .contains("business_date date null")
            .contains("day_of_week integer null")
            .contains("period_key text null")
            .contains("quota_mode text not null")
            .contains("constraint fk_store_public_booking_availability_rules_store_scope")
            .contains("foreign key (store_id, tenant_id) references stores(id, tenant_id)")
            .contains("constraint ck_store_public_booking_availability_rules_type")
            .contains("rule_type in ('weekly', 'date_exception')")
            .contains("constraint ck_store_public_booking_availability_rules_target")
            .contains("rule_type = 'weekly' and day_of_week between 1 and 7 and business_date is null")
            .contains("rule_type = 'date_exception' and business_date is not null and day_of_week is null")
            .contains("constraint ck_store_public_booking_availability_rules_mode")
            .contains("quota_mode in ('percentage', 'table_count', 'guest_count', 'closed')")
            .contains("create unique index if not exists ux_store_public_booking_availability_rules_weekly_active")
            .contains("on store_public_booking_availability_rules (tenant_id, store_id, day_of_week, coalesce(period_key, ''))")
            .contains("where rule_type = 'weekly' and deleted_at is null")
            .contains("create unique index if not exists ux_store_public_booking_availability_rules_date_active")
            .contains("on store_public_booking_availability_rules (tenant_id, store_id, business_date, coalesce(period_key, ''))")
            .contains("where rule_type = 'date_exception' and deleted_at is null")
            .contains("insert into store_public_booking_availability_rules")
            .contains("select tenant_id, store_id, 'date_exception', business_date, null, period_key, quota_mode")
            .contains("from store_public_booking_quota_overrides");
    }
}
