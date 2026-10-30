package com.rpb.reservation.platformbilling.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rpb.reservation.platformbilling.application.*;
import com.rpb.reservation.platformbilling.persistence.*;
import com.rpb.reservation.walkin.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlatformSubscriptionHistoryControllerTest {
    private final UUID tenant = UUID.randomUUID(), subscription = UUID.randomUUID();
    private final ProductSubscriptionRepository subscriptions = mock(ProductSubscriptionRepository.class);
    private final ProductSubscriptionHistoryRepository events = mock(ProductSubscriptionHistoryRepository.class);
    private final CurrentActorProvider actors = mock(CurrentActorProvider.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlatformSubscriptionHistoryController(
        new ProductSubscriptionHistoryService(subscriptions, events), actors)).build();

    @Test void platformAdminCanReadAnotherTenantWithPrivateNoStore() throws Exception {
        actor(Set.of("platform_admin"), Set.of("platform.billing.manage"));
        when(subscriptions.findByTenantIdAndId(tenant, subscription)).thenReturn(Optional.of(mock(ProductSubscription.class)));
        when(events.list(tenant, subscription)).thenReturn(List.of());
        mvc.perform(get(path())).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(jsonPath("$.events").isEmpty());
        verify(events).list(tenant, subscription);
    }

    @Test void wrongTenantCannotReadSubscriptionEvents() throws Exception {
        actor(Set.of("platform_admin"), Set.of("platform.tenant.manage"));
        when(subscriptions.findByTenantIdAndId(tenant, subscription)).thenReturn(Optional.empty());
        mvc.perform(get(path())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("SUBSCRIPTION_NOT_FOUND"));
        verifyNoInteractions(events);
    }

    @Test void missingRoleOrBillingPermissionIsForbiddenBeforeAnyRead() throws Exception {
        actor(Set.of("tenant_admin"), Set.of("platform.billing.manage"));
        mvc.perform(get(path())).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        actor(Set.of("platform_admin"), Set.of("employee_manage"));
        mvc.perform(get(path())).andExpect(status().isForbidden());
        verifyNoInteractions(subscriptions, events);
    }

    @Test void unauthenticatedIsRejected() throws Exception {
        when(actors.currentActor()).thenReturn(Optional.empty());
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        verifyNoInteractions(subscriptions, events);
    }

    private void actor(Set<String> roles, Set<String> permissions) {
        when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(), UUID.randomUUID(), "platform_admin", roles, permissions, Set.of())));
    }
    private String path() { return "/api/v1/platform/tenants/" + tenant + "/product-subscriptions/" + subscription + "/events"; }
}
