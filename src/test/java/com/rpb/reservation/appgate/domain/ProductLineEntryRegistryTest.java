package com.rpb.reservation.appgate.domain;

import com.rpb.reservation.common.product.ProductLineEntryRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProductLineEntryRegistryTest {
    @Test void registeredPermissionsMatchRoleDefinitionsAndUnknownProductFailsClosed() {
        assertThat(ProductLineEntryRegistry.permissions("reservation_queue")).isEqualTo(AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS);
        assertThat(ProductLineEntryRegistry.permissions("payment")).isEqualTo(AppGateRequiredPermission.PAYMENT_ENTRY_PERMISSIONS);
        assertThat(ProductLineEntryRegistry.permissions("daily_ledger")).isEqualTo(AppGateRequiredPermission.DAILY_LEDGER_ENTRY_PERMISSIONS);
        assertThat(ProductLineEntryRegistry.permissions("unknown")).isEmpty();
        assertThat(ProductLineEntryRegistry.isControlledRoute("https://example.com")).isFalse();
    }

    @Test void sharedManifestRoutesExistInVueAndAreConsumedByProductEditor() throws Exception {
        String router = Files.readString(Path.of("src/router/index.ts"));
        assertThat(ProductLineEntryRegistry.entries()).extracting(ProductLineEntryRegistry.Entry::appKey).doesNotHaveDuplicates();
        assertThat(ProductLineEntryRegistry.entries()).extracting(ProductLineEntryRegistry.Entry::value).doesNotHaveDuplicates();
        for (var entry : ProductLineEntryRegistry.entries()) {
            if (!entry.value().isBlank()) assertThat(router).contains("path: '" + entry.value() + "'");
        }
        assertThat(Files.readString(Path.of("src/components/platform/product-line/productLineCatalog.ts")))
            .contains("main/resources/product-line-entries.json");
    }
}
