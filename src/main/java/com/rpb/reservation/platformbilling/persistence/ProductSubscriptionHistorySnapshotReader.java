package com.rpb.reservation.platformbilling.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionHistoryEvent.StoreSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProductSubscriptionHistorySnapshotReader {
    private final ObjectMapper mapper;
    public ProductSubscriptionHistorySnapshotReader(ObjectMapper mapper) { this.mapper = mapper; }

    public Snapshot read(String payload) {
        if (payload == null || payload.isBlank()) return unavailable();
        try {
            var root = mapper.readTree(payload);
            if (root == null || !root.path("storeSnapshotVersion").isIntegralNumber()
                || root.path("storeSnapshotVersion").intValue() != 1 || !root.path("storeSnapshots").isArray()) return unavailable();
            List<StoreSnapshot> stores = new ArrayList<>();
            for (var node : root.path("storeSnapshots")) {
                var store = mapper.treeToValue(node, StoreSnapshot.class);
                if (store == null || store.storeId() == null || store.storeName() == null) return unavailable();
                stores.add(store);
            }
            return new Snapshot(true, List.copyOf(stores));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            // Legacy/unsupported evidence must never be replaced by today's store master.
            return unavailable();
        }
    }

    private static Snapshot unavailable() { return new Snapshot(false, List.of()); }
    public record Snapshot(boolean available, List<StoreSnapshot> stores) {}
}
