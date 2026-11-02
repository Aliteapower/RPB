package com.rpb.reservation.workforce;

import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.persistence.WorkforceDirectory;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkforceAccessTest {
    UUID tenant=UUID.randomUUID(), store=UUID.randomUUID(), account=UUID.randomUUID();
    AppGateService gate=mock(AppGateService.class);
    WorkforceDirectory directory=mock(WorkforceDirectory.class);
    WorkforceAccess access=new WorkforceAccess(gate,directory);
    CurrentActor actor(Set<String> roles,Set<String> permissions) {
        return new CurrentActor(tenant,account,"staff",roles,permissions,Set.of(store));
    }
    @Test void legacyStoreManagerCannotBecomeHeadOfficeEvenWithTenantAdminRole() {
        assertThat(access.isHeadOffice(actor(Set.of("tenant_admin","store_manager"),Set.of("tenant.admin.manage","workforce.admin.manage")))).isFalse();
        assertThat(access.isHeadOffice(actor(Set.of("tenant_admin"),Set.of("tenant.admin.manage")))).isFalse();
        assertThat(access.isHeadOffice(actor(Set.of("tenant_admin"),Set.of("tenant.admin.manage","workforce.admin.manage")))).isTrue();
    }
    @Test void storeMembershipAndProductGateAreBothRequired() {
        var actor=actor(Set.of("store_staff"),Set.of("workforce.view"));
        when(gate.evaluate(any())).thenReturn(AppGateDecision.deny("workforce",tenant,store,"workforce.view",AppGateDenyReason.STORE_APP_NOT_ENABLED));
        assertThatThrownBy(()->access.enter(actor,store)).isInstanceOf(WorkforceException.class);
        verify(gate).evaluate(argThat(r->r.appKey().equals("workforce")&&r.storeId().equals(store)));
        assertThatThrownBy(()->access.enter(actor,UUID.randomUUID())).isInstanceOf(WorkforceException.class);
    }
    @Test void managerScopeCannotIncludeStoresRemovedFromExistingAccountAccess() {
        var other=UUID.randomUUID();
        when(directory.managerStoreIds(tenant,account)).thenReturn(Set.of(store,other));
        assertThat(access.managedStores(actor(Set.of("store_manager"),Set.of("workforce.view")))).containsExactly(store);
    }
    @Test void explicitHistoryDoesNotLoosenOperationalGateOrStoreScope() {
        var actor=actor(Set.of("store_staff"),Set.of("workforce.view"));
        when(gate.evaluateHistory(any())).thenReturn(AppGateDecision.allow("workforce",tenant,store,"workforce.view"));
        when(gate.evaluate(any())).thenReturn(AppGateDecision.deny("workforce",tenant,store,"workforce.view",AppGateDenyReason.STORE_APP_EXPIRED));
        access.history(actor,store);
        assertThatThrownBy(()->access.enter(actor,store)).extracting("status").isEqualTo(403);
        assertThatThrownBy(()->access.history(actor,UUID.randomUUID())).extracting("status").isEqualTo(403);
        verify(gate).evaluateHistory(argThat(r->r.appKey().equals("workforce")&&r.requiredPermission().equals("workforce.view")));
    }
    @Test void historicalGateStillRejectsUnprovisionedOrDisabledProduct() {
        for(var reason:List.of(AppGateDenyReason.TENANT_APP_NOT_ENABLED,AppGateDenyReason.STORE_APP_NOT_ENABLED,AppGateDenyReason.APP_DISABLED)){
            when(gate.evaluateHistory(any())).thenReturn(AppGateDecision.deny("workforce",tenant,store,"workforce.view",reason));
            assertThatThrownBy(()->access.history(actor(Set.of("store_staff"),Set.of("workforce.view")),store)).extracting("code").isEqualTo(reason.name());
        }
    }
}
