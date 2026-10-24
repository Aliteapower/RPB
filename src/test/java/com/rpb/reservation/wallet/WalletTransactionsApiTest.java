package com.rpb.reservation.wallet;

import com.rpb.reservation.wallet.transactions.*;
import com.rpb.reservation.wallet.api.*;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.wallet.domain.WalletException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WalletTransactionsApiTest {
    final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),customer=UUID.randomUUID();
    final WalletApiIdentity identity=mock(WalletApiIdentity.class);final TransactionService service=mock(TransactionService.class);
    MockMvc mvc;StoreScope scope;CustomerAuthPrincipal principal;
    @BeforeEach void setup(){
        scope=new StoreScope(new TenantId(tenant),store);principal=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),customer,"a@example.invalid","A");
        when(identity.customer(any())).thenReturn(principal);when(identity.scope(tenant,store)).thenReturn(scope);
        mvc=MockMvcBuilders.standaloneSetup(new TransactionController(identity,service)).setControllerAdvice(new WalletErrorAdvice()).addFilters(new WalletRequestSecurityFilter()).build();
    }
    @Test void customerScopeUsesSessionAndListIsPrivateAndReadOnly()throws Exception {
        mvc.perform(get("/api/v1/public/stores/"+store+"/wallet/transactions").param("period","year").param("year","2026").header("X-Test-Tenant-Id",UUID.randomUUID())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"));
        verify(service).list(scope,null,principal,Map.of("period","year","year","2026"));
    }
    @Test void detailsAreIndependentOfDefaultDateFiltersAndErrorsUseWalletCodes()throws Exception {
        UUID id=UUID.randomUUID();when(service.detail(scope,null,principal,"refund",id)).thenThrow(WalletException.missing());
        mvc.perform(get("/api/v1/public/stores/"+store+"/wallet/transactions/refund/"+id)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }
    @Test void strictDateAndFilterValidationRejectsIdentityOverridesAndInvalidLimits(){
        var clock=Clock.fixed(Instant.parse("2026-12-31T18:00:00Z"),ZoneOffset.UTC);
        var q=TransactionQuery.parse(Map.of(),clock,"Asia/Singapore",true);assertThat(q.period().startDate()).isEqualTo(LocalDate.of(2027,1,1));
        for(var input:List.of(Map.of("customerId",customer.toString()),Map.of("customerQuery","A"),Map.of("limit","101"),Map.of("period","range","startDate","2026-12-31","endDate","2026-01-01"),Map.of("month","13"),Map.of("type","unknown"),Map.of("category","unknown"),Map.of("direction","unknown")))assertThatThrownBy(()->TransactionQuery.parse(input,clock,"Asia/Singapore",true)).isInstanceOf(WalletException.class);
    }
}
