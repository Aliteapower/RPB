package com.rpb.reservation.wallet;

import com.rpb.reservation.wallet.transactions.*;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.domain.WalletException;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalletBusinessReportTest {
    UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();
    StoreScope scope=new StoreScope(new TenantId(tenant),store);
    WalletDirectory directory=mock(WalletDirectory.class);
    WalletBusinessReportRepository repository=mock(WalletBusinessReportRepository.class);
    CurrentActor actor(UUID t,Set<String> permissions,Set<UUID> stores){return new CurrentActor(t,UUID.randomUUID(),"tenant_admin",Set.of("tenant_admin"),permissions,stores);}
    WalletBusinessReportService service(){
        when(directory.store(scope)).thenReturn(new WalletViews.StoreIdentity(tenant,store,UUID.randomUUID(),"主体","店","test","Asia/Singapore","zh-CN",null));
        return new WalletBusinessReportService(new WalletAccess(mock(AppGateService.class),directory),directory,repository);
    }
    @Test void naturalPeriodsHandleLeapYearAndStoreMidnight(){
        var s=service();var a=actor(tenant,Set.of("wallet.reconcile.view","wallet.points.view"),Set.of(store));
        when(repository.query(any(),any(),any(),anyString(),anyString(),anyBoolean())).thenReturn(List.of());
        var month=s.query(scope,a,Map.of("period","month","date","2024-02-18"));
        assertThat(month.startDate()).isEqualTo(LocalDate.of(2024,2,1));assertThat(month.endDate()).isEqualTo(LocalDate.of(2024,2,29));
        verify(repository).query(scope,Instant.parse("2024-01-31T16:00:00Z"),Instant.parse("2024-02-29T16:00:00Z"),"Asia/Singapore","day",true);
        var year=s.query(scope,a,Map.of("period","year","date","2024-02-18"));
        assertThat(year.startDate()).isEqualTo(LocalDate.of(2024,1,1));assertThat(year.endDate()).isEqualTo(LocalDate.of(2024,12,31));
    }
    @Test void invalidDatesAndScopeOverridesAreRejectedBeforeQuery(){
        var s=service();var a=actor(tenant,Set.of("wallet.reconcile.view"),Set.of(store));
        for(var q:List.of(Map.of("period","all","date","2026-01-01"),Map.of("period","day","date","2026-02-29"),Map.of("period","day","date","2026-1-01"),Map.of("period","day","date","2026-01-01","tenantId",tenant.toString()),Map.of("period","day","date","0001-01-01")))assertThatThrownBy(()->s.query(scope,a,q)).isInstanceOf(WalletException.class);
        verifyNoInteractions(repository);
    }
    @Test void requiresReportPermissionAndOwnTenantAndStoreAndDoesNotLeakPoints(){
        var s=service();
        for(var a:List.of(actor(tenant,Set.of("wallet.view"),Set.of(store)),actor(UUID.randomUUID(),Set.of("wallet.reconcile.view"),Set.of(store)),actor(tenant,Set.of("wallet.reconcile.view"),Set.of(UUID.randomUUID()))))assertThatThrownBy(()->s.query(scope,a,Map.of("period","day","date","2026-10-25"))).isInstanceOf(WalletException.class);
        verifyNoInteractions(repository);
        when(repository.query(any(),any(),any(),anyString(),anyString(),anyBoolean())).thenReturn(List.of());
        s.query(scope,actor(tenant,Set.of("wallet.reconcile.view"),Set.of(store)),Map.of("period","day","date","2026-10-25"));
        verify(repository).query(scope,Instant.parse("2026-10-24T16:00:00Z"),Instant.parse("2026-10-25T16:00:00Z"),"Asia/Singapore","day",false);
    }
    @Test void endpointIsPrivateReadOnlyAndUsesAuthenticatedTenant()throws Exception{
        var identity=mock(com.rpb.reservation.wallet.api.WalletApiIdentity.class);var service=mock(WalletBusinessReportService.class);
        var a=actor(tenant,Set.of("wallet.reconcile.view"),Set.of(store));when(identity.staff()).thenReturn(a);when(identity.scope(tenant,store)).thenReturn(scope);
        when(service.query(eq(scope),eq(a),anyMap())).thenReturn(new WalletBusinessReport("day",LocalDate.of(2026,10,25),LocalDate.of(2026,10,25),"Asia/Singapore","SGD",List.of()));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new WalletBusinessReportController(identity,service)).setControllerAdvice(new com.rpb.reservation.wallet.api.WalletErrorAdvice()).addFilters(new com.rpb.reservation.wallet.api.WalletRequestSecurityFilter()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/stores/"+store+"/wallet/business-report").param("period","day").param("date","2026-10-25")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","private, no-store")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.rows").isEmpty());
        when(identity.staff()).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"请先登录"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/stores/"+store+"/wallet/business-report")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }
}
