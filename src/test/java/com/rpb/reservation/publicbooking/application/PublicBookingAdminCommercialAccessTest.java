package com.rpb.reservation.publicbooking.application;

import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthIntegrationAdminService;
import com.rpb.reservation.publicbooking.api.TenantAdminPublicBookingController;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.tenantadmin.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicBookingAdminCommercialAccessTest {
    final UUID tenant=UUID.randomUUID(), store=UUID.randomUUID();
    final StoreScope scope=new StoreScope(new TenantId(tenant),store);
    final PublicBookingAdminService service=mock(PublicBookingAdminService.class);
    final CustomerAuthIntegrationAdminService auth=mock(CustomerAuthIntegrationAdminService.class);
    final TenantAdminScopeResolver resolver=mock(TenantAdminScopeResolver.class);
    final AppGateService gate=mock(AppGateService.class);
    final PublicBookingStoreRepositoryPort stores=mock(PublicBookingStoreRepositoryPort.class);
    final TenantAdminPublicBookingController controller=new TenantAdminPublicBookingController(service,auth,resolver,new PublicBookingManagementAccess(gate,stores));
    void scoped() {
        when(resolver.requireTenantAdminScope(store)).thenReturn(scope);
        when(stores.findActiveStoreProfile(scope)).thenReturn(Optional.of(new PublicBookingStoreProfile(scope,"Store","UTC",null,null,null,null,null)));
    }
    @ParameterizedTest
    @EnumSource(value=AppGateDenyReason.class,names={"TENANT_APP_NOT_ENABLED","TENANT_APP_EXPIRED","STORE_APP_NOT_ENABLED","APP_DISABLED"})
    void closedProductCannotReadWorkspaceOrEnableSettings(AppGateDenyReason reason) {
        scoped();
        when(gate.evaluateCommercial("reservation_queue",tenant,store)).thenReturn(AppGateDecision.deny("reservation_queue",tenant,store,null,reason));
        assertThatThrownBy(()->controller.getSettings(store)).isInstanceOf(TenantAdminApiException.class);
        assertThatThrownBy(()->controller.saveSettings(store,null)).isInstanceOf(TenantAdminApiException.class);
        verifyNoInteractions(service,auth);
    }
    @Test void crossTenantPhysicalStoreIsRejectedBeforeCommercialCheck() {
        when(resolver.requireTenantAdminScope(store)).thenReturn(scope);
        when(stores.findActiveStoreProfile(scope)).thenReturn(Optional.empty());
        assertThatThrownBy(()->controller.saveSettings(store,null)).isInstanceOf(TenantAdminApiException.class)
            .satisfies(e->assertThat(((TenantAdminApiException)e).code()).isEqualTo(TenantAdminApiErrorCode.STORE_SCOPE_MISMATCH));
        verifyNoInteractions(gate,service);
    }
    @Test void invalidAdminScopeNeverReachesCommercialOrBusinessServices() {
        when(resolver.requireTenantAdminScope(store)).thenThrow(new TenantAdminApiException(TenantAdminApiErrorCode.FORBIDDEN));
        assertThatThrownBy(()->controller.getSettings(store)).isInstanceOf(TenantAdminApiException.class);
        verifyNoInteractions(stores,gate,service);
    }
    @Test void validStoreKeepsExistingReadAndWriteContract() {
        scoped();
        when(gate.evaluateCommercial("reservation_queue",tenant,store)).thenReturn(AppGateDecision.allow("reservation_queue",tenant,store,null));
        when(service.getSettings(scope)).thenReturn(PublicBookingSettings.disabled());
        when(service.saveSettings(scope,null)).thenReturn(PublicBookingSettings.disabled());
        assertThat(controller.getSettings(store).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.saveSettings(store,null).getStatusCode().value()).isEqualTo(200);
        verify(service).saveSettings(scope,null);
    }
    @Test void managementHttpContractReturnsForbiddenWithoutInvokingSettings() throws Exception {
        scoped();
        when(gate.evaluateCommercial("reservation_queue",tenant,store)).thenReturn(AppGateDecision.deny("reservation_queue",tenant,store,null,AppGateDenyReason.TENANT_APP_EXPIRED));
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        String path="/api/v1/stores/"+store+"/tenant-admin/public-booking/settings";
        mvc.perform(get(path)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(put(path).contentType("application/json").content("{\"enabled\":true}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(service);
    }
}
