package com.rpb.reservation.platformbilling.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JdbcTenantProductEntitlementSyncGatewaySourceValidationTest {

    @Test
    void enableTenantAppSyncsActiveStoreSubscriptionItemsIntoStoreAppSettings() throws Exception {
        String source = java.nio.file.Files.readString(Path.of(
            "src",
            "main",
            "java",
            "com",
            "rpb",
            "reservation",
            "platformbilling",
            "persistence",
            "JdbcTenantProductEntitlementSyncGateway.java"
        ));

        assertThat(source)
            .contains("from tenant_product_subscription_items item")
            .contains("item.scope_type = 'store'")
            .contains("item.status = 'active'")
            .contains("on conflict (tenant_id, store_id, app_key) do update")
            .contains("is_enabled = true")
            .contains("entry_visible = true");
    }
}
