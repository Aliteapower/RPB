package com.rpb.reservation.common.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Code-owned manifest shared with Vue. Database config cannot register permissions or executable routes. */
public final class ProductLineEntryRegistry {
    public record Entry(String appKey, String value, String labelKey, String descriptionKey, Set<String> permissions, String historyRoute, Set<String> historyPermissions) {}

    private static final List<Entry> ENTRIES = load();
    private static final Set<String> ROUTES = ENTRIES.stream().map(Entry::value).collect(Collectors.toUnmodifiableSet());

    private ProductLineEntryRegistry() {}

    public static List<Entry> entries() { return ENTRIES; }

    public static boolean isControlledRoute(String route) { return ROUTES.contains(route); }

    public static Set<String> permissions(String appKey) {
        return ENTRIES.stream().filter(entry -> entry.appKey().equals(appKey))
            .findFirst().map(Entry::permissions).orElse(Set.of());
    }

    public static List<String> managedProducts() { return ENTRIES.stream().map(Entry::appKey).filter(key -> !key.isBlank()).toList(); }
    public static Set<String> historyPermissions(String key) { return ENTRIES.stream().filter(e -> e.appKey().equals(key)).findFirst().map(Entry::historyPermissions).orElse(Set.of()); }

    private static List<Entry> load() {
        try (var input = ProductLineEntryRegistry.class.getResourceAsStream("/product-line-entries.json")) {
            if (input == null) throw new IllegalStateException("Product entry manifest missing");
            List<Entry> entries = new ObjectMapper().readValue(input, new TypeReference<>() {});
            return entries.stream().map(entry -> new Entry(entry.appKey(), entry.value(), entry.labelKey(),
                entry.descriptionKey(), Set.copyOf(entry.permissions()), entry.historyRoute(), Set.copyOf(entry.historyPermissions()))).toList();
        } catch (IOException error) {
            throw new IllegalStateException("Invalid product entry manifest", error);
        }
    }
}
