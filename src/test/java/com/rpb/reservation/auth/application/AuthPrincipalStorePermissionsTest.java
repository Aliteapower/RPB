package com.rpb.reservation.auth.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.auth.api.AuthUserResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthPrincipalStorePermissionsTest {
    final UUID tenant = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();

    // Reflection keeps the first red run executable before the new record component exists.
    AuthPrincipal principal(Map<UUID, Set<String>> overrides) throws Exception {
        return AuthPrincipal.class.getConstructor(UUID.class, UUID.class, String.class, String.class,
            String.class, UUID.class, Set.class, Set.class, Set.class, Map.class)
            .newInstance(UUID.randomUUID(), tenant, "staff", "Staff", "staff", a, Set.of(a,b),
                Set.of("store_staff"), Set.of("wallet.view", "wallet.topup.create"), overrides);
    }
    CurrentActor actor(AuthPrincipal principal, UUID store) throws Exception {
        return (CurrentActor) AuthPrincipal.class.getMethod("toCurrentActor", UUID.class).invoke(principal, store);
    }

    @Test void legacyConstructorKeepsAccountPermissionsAndExposesEmptyScopedMap() throws Exception {
        var old = new AuthPrincipal(UUID.randomUUID(),tenant,"staff","Staff","staff",a,
            Set.of(a),Set.of("store_staff"),Set.of("wallet.view"));
        assertThat(old.toCurrentActor().hasPermission("wallet.view")).isTrue();
        var json = new ObjectMapper().valueToTree(AuthUserResponse.from(old));
        assertThat(json.has("storePermissions")).isTrue();
        assertThat(json.path("storePermissions").size()).isZero();
    }
    @Test void explicitEmptyOverridesNeverFallBackToLegacyGrants() throws Exception {
        var p = principal(Map.of(a,Set.of()));
        assertThat(actor(p,a).permissions()).isEmpty();
        assertThat(actor(p,b).permissions()).containsExactlyInAnyOrder("wallet.view","wallet.topup.create");
    }
    @Test void storesUseSeparateSetsWithoutAccumulatingPermissions() throws Exception {
        var p = principal(Map.of(a,Set.of("wine_storage.view"),b,Set.of("daily_ledger.view")));
        assertThat(actor(p,a).permissions()).containsExactly("wine_storage.view");
        assertThat(actor(p,b).permissions()).containsExactly("daily_ledger.view");
        assertThat(actor(p,a).permissions()).doesNotContain("daily_ledger.view","wallet.topup.create");
        assertThat(p.permissions()).contains("wallet.topup.create");
    }
    @Test void authMePublishesEffectiveSetsForFrontendIncludingEmptySet() throws Exception {
        var p = principal(Map.of(a,Set.of(),b,Set.of("daily_ledger.view")));
        var json = new ObjectMapper().valueToTree(AuthUserResponse.from(p));
        assertThat(json.path("storePermissions").path(a.toString()).size()).isZero();
        assertThat(json.path("storePermissions").path(b.toString()).get(0).asText()).isEqualTo("daily_ledger.view");
    }
    @Test void scopedSnapshotsAreDefensiveCopies() throws Exception {
        var values = new java.util.HashSet<>(Set.of("wallet.view"));
        var map = new java.util.HashMap<UUID,Set<String>>(); map.put(a,values);
        var p = principal(map); values.add("wallet.refund.manage"); map.clear();
        assertThat(actor(p,a).permissions()).containsExactly("wallet.view");
    }
}
