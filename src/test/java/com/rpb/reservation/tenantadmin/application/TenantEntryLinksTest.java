package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.publicbooking.application.*;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.tenantadmin.persistence.TenantEntryLinksRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TenantEntryLinksTest {
    final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();
    final StoreScope scope=new StoreScope(new TenantId(tenant),store);
    final TenantEntryLinksRepository repository=mock(TenantEntryLinksRepository.class);
    final AppGateService gate=mock(AppGateService.class);
    final PublicBookingAdminService booking=mock(PublicBookingAdminService.class);
    final TenantEntryLinksService service=new TenantEntryLinksService(repository,gate,booking);
    TenantEntryLinksContext.Identity identity(String tenantStatus,String storeStatus){return new TenantEntryLinksContext.Identity(tenant,"tenant-a","统一租户名称",tenantStatus,store,"shop-a","另一个门店名称",storeStatus);}

    @org.junit.jupiter.api.BeforeEach void provisioned(){when(gate.isStoreProductProvisioned(anyString(),eq(tenant),eq(store))).thenReturn(true);}
    @Test void readsSharedTenantNameAndCommercialAccessWithoutCreatingSubscriptions() {
        when(repository.find(scope)).thenReturn(Optional.of(identity("active","active")));
        when(gate.evaluateCommercial(anyString(),eq(tenant),eq(store))).thenAnswer(i->AppGateDecision.allow(i.getArgument(0),tenant,store,null));
        when(booking.getSettings(scope)).thenReturn(PublicBookingSettings.disabled());
        var result=service.get(scope);
        assertThat(result.identity().tenantName()).isEqualTo("统一租户名称");assertThat(result.identity().storeName()).isEqualTo("另一个门店名称");
        assertThat(result.products()).hasSize(4).allMatch(TenantEntryLinksContext.Product::available);assertThat(result.publicBookingEnabled()).isFalse();
        verify(repository).find(scope);verifyNoMoreInteractions(repository);
    }
    @Test void disabledExpiredAndMissingProductsStayUnavailableAndDoNotAdvertiseBooking() {
        when(repository.find(scope)).thenReturn(Optional.of(identity("active","active")));
        for(var reason:List.of(AppGateDenyReason.TENANT_APP_NOT_ENABLED,AppGateDenyReason.STORE_APP_EXPIRED,AppGateDenyReason.APP_DISABLED)) {
            when(gate.evaluateCommercial(anyString(),eq(tenant),eq(store))).thenAnswer(i->AppGateDecision.deny(i.getArgument(0),tenant,store,null,reason));
            var result=service.get(scope);assertThat(result.products()).allMatch(p->!p.available()&&p.denialReason().equals(reason.name()));assertThat(result.publicBookingEnabled()).isFalse();
        }
        verifyNoInteractions(booking);
    }
    @Test void inactiveIdentityOverridesCommercialFlagsAndMissingScopeReturnsNotFound() {
        when(repository.find(scope)).thenReturn(Optional.of(identity("active","inactive")));
        assertThat(service.get(scope).products()).allMatch(p->!p.available()&&p.denialReason().equals("STORE_INACTIVE"));
        when(repository.find(scope)).thenReturn(Optional.of(identity("suspended","active")));
        assertThat(service.get(scope).products()).allMatch(p->!p.available()&&p.denialReason().equals("TENANT_INACTIVE"));
        when(repository.find(scope)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.get(scope)).isInstanceOf(TenantAdminServiceException.class);
        verifyNoInteractions(gate,booking);
    }
}
