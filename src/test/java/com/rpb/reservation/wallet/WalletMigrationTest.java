package com.rpb.reservation.wallet;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WalletMigrationTest {
    @Test void walletHasAnIsolatedSchemaAndDoesNotAutoGrantSubscriptions() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V057__wallet_product_line.sql");
        assertThat(migration).exists();
        String sql = Files.readString(migration);
        assertThat(sql).contains("create table wallet_accounts", "create table wallet_entries",
            "create table wallet_topups", "create table wallet_bills", "create table wallet_authorizations",
            "create table wallet_refunds", "foreign key", "check", "'wallet'");
        assertThat(sql).doesNotContain("insert into tenant_app_entitlements", "insert into tenant_product_subscriptions");
    }
}
