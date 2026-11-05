package com.rpb.reservation.platformbilling.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ProductSubscriptionHistorySnapshotReaderTest {
    private final ProductSubscriptionHistorySnapshotReader reader = new ProductSubscriptionHistorySnapshotReader(new ObjectMapper().findAndRegisterModules());

    @Test void legacyOrUnknownPayloadNeverInventsCurrentStoreNames() {
        for (String payload : new String[]{"{}", "null", "{\"storeSnapshots\":[]}", "{\"storeSnapshotVersion\":2,\"storeSnapshots\":[]}", "invalid"}) {
            assertThat(reader.read(payload).available()).isFalse();
            assertThat(reader.read(payload).stores()).isEmpty();
        }
    }

    @Test void readsHistoricalIdentityAmountsAndPeriodsOnlyFromPayload() {
        var result = reader.read("""
            {"storeSnapshotVersion":1,"storeSnapshots":[{
              "storeId":"20000000-0000-0000-0000-000000000001","storeCode":"old-code","storeName":"历史店名",
              "operatingEntityId":null,"operatingEntityName":"历史主体","storeStatus":"inactive","itemStatus":"active",
              "quantity":1,"unitAmount":128.25,"amount":128.25,"currency":"SGD",
              "periodStart":"2026-01-01T00:00:00Z","periodEnd":"2026-02-01T00:00:00Z"}]}
            """);
        assertThat(result.available()).isTrue();
        assertThat(result.stores().getFirst().storeName()).isEqualTo("历史店名");
        assertThat(result.stores().getFirst().operatingEntityName()).isEqualTo("历史主体");
        assertThat(result.stores().getFirst().amount()).isEqualByComparingTo("128.25");
        assertThat(result.stores().getFirst().periodEnd().getMonthValue()).isEqualTo(2);
    }

    @Test void explicitEmptySnapshotIsAvailableButBrokenSnapshotIsNot() {
        assertThat(reader.read("{\"storeSnapshotVersion\":1,\"storeSnapshots\":[]}").available()).isTrue();
        assertThat(reader.read("{\"storeSnapshotVersion\":1,\"storeSnapshots\":[null]}").available()).isFalse();
        assertThat(reader.read("{\"storeSnapshotVersion\":1,\"storeSnapshots\":[{}]}").available()).isFalse();
    }
}
