package com.rpb.reservation.walkin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class LocalRuntimeCurrentActorProviderTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000101");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000101");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000101");
    private static final UUID HEADER_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000202");
    private static final UUID HEADER_ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000202");
    private static final UUID HEADER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000202");

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsConfiguredActorWhenNoHeaderOverridesArePresent() {
        LocalAuthProperties properties = new LocalAuthProperties();
        properties.setEnabled(true);
        properties.setTenantId(TENANT_ID);
        properties.setActorId(ACTOR_ID);
        properties.setActorType("staff");
        properties.setRoles(List.of("store_staff"));
        properties.setPermissions(List.of("walkin.direct_seating.create"));
        properties.setStoreIds(List.of(STORE_ID));
        LocalRuntimeCurrentActorProvider provider = new LocalRuntimeCurrentActorProvider(properties);

        CurrentActor actor = provider.currentActor().orElseThrow();

        assertThat(actor.tenantId()).isEqualTo(TENANT_ID);
        assertThat(actor.actorId()).isEqualTo(ACTOR_ID);
        assertThat(actor.actorType()).isEqualTo("staff");
        assertThat(actor.roles()).containsExactlyInAnyOrder("store_staff");
        assertThat(actor.permissions()).containsExactlyInAnyOrder("walkin.direct_seating.create");
        assertThat(actor.storeIds()).containsExactlyInAnyOrder(STORE_ID);
    }

    @Test
    void requestHeadersOverrideConfiguredActorForLocalValidation() {
        LocalAuthProperties properties = new LocalAuthProperties();
        properties.setEnabled(true);
        properties.setTenantId(TENANT_ID);
        properties.setActorId(ACTOR_ID);
        properties.setActorType("staff");
        properties.setRoles(List.of("store_staff"));
        properties.setPermissions(List.of("walkin.direct_seating.create"));
        properties.setStoreIds(List.of(STORE_ID));
        LocalRuntimeCurrentActorProvider provider = new LocalRuntimeCurrentActorProvider(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Test-Tenant-Id", HEADER_TENANT_ID.toString());
        request.addHeader("X-Test-Actor-Id", HEADER_ACTOR_ID.toString());
        request.addHeader("X-Test-Actor-Role", "store_manager");
        request.addHeader("X-Test-Permissions", "walkin.direct_seating.create,other.permission");
        request.addHeader("X-Test-Store-Ids", HEADER_STORE_ID.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        CurrentActor actor = provider.currentActor().orElseThrow();

        assertThat(actor.tenantId()).isEqualTo(HEADER_TENANT_ID);
        assertThat(actor.actorId()).isEqualTo(HEADER_ACTOR_ID);
        assertThat(actor.roles()).containsExactlyInAnyOrder("store_manager");
        assertThat(actor.permissions()).containsExactlyInAnyOrder("walkin.direct_seating.create", "other.permission");
        assertThat(actor.storeIds()).containsExactlyInAnyOrder(HEADER_STORE_ID);
    }

    @Test
    void prefersSessionActorFromSecurityContextBeforeLocalRuntimeFallback() {
        LocalAuthProperties properties = new LocalAuthProperties();
        properties.setEnabled(true);
        properties.setTenantId(TENANT_ID);
        properties.setActorId(ACTOR_ID);
        properties.setActorType("staff");
        properties.setRoles(List.of("store_staff"));
        properties.setPermissions(List.of("walkin.direct_seating.create"));
        properties.setStoreIds(List.of(STORE_ID));
        LocalRuntimeCurrentActorProvider provider = new LocalRuntimeCurrentActorProvider(properties);
        CurrentActor sessionActor = CurrentActor.storeStaff(
            HEADER_TENANT_ID,
            HEADER_ACTOR_ID,
            "staff",
            java.util.Set.of("tenant_admin"),
            java.util.Set.of("table.view"),
            java.util.Set.of(HEADER_STORE_ID)
        );
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            sessionActor,
            null,
            List.of()
        ));

        CurrentActor actor = provider.currentActor().orElseThrow();

        assertThat(actor.tenantId()).isEqualTo(HEADER_TENANT_ID);
        assertThat(actor.actorId()).isEqualTo(HEADER_ACTOR_ID);
        assertThat(actor.roles()).containsExactlyInAnyOrder("tenant_admin");
        assertThat(actor.permissions()).containsExactlyInAnyOrder("table.view");
        assertThat(actor.storeIds()).containsExactlyInAnyOrder(HEADER_STORE_ID);
    }
}
