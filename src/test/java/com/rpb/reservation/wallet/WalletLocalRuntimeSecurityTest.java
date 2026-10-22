package com.rpb.reservation.wallet;

import com.rpb.reservation.wallet.api.*;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.domain.WalletException;
import com.rpb.reservation.walkin.auth.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({WalletStaffController.class,WalletCustomerController.class,WalletSupportController.class,WalletCollectionSettingsController.class,WalletScanChargeController.class})
@Import({LocalRuntimeSecurityConfiguration.class,LocalRuntimeCurrentActorProvider.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local")
@TestPropertySource(properties="rpb.local-auth.enabled=true")
class WalletLocalRuntimeSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean WalletApiIdentity identity;
    @MockBean WalletAccountService accounts;
    @MockBean WalletTopupService topups;
    @MockBean WalletCheckoutService checkout;
    @MockBean WalletAuthorizationService authorization;
    @MockBean WalletScanChargeService charges;
    @MockBean WalletRefundService refunds;
    @MockBean WalletReconciliationService reports;
    @MockBean WalletCollectionPort port;
    @MockBean WalletCollectionExceptionService exceptions;
    @MockBean WalletCollectionSettingsService settings;
    @MockBean com.rpb.reservation.appgate.application.AppGateService gate;
    @MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService audit;
    @Test void historyQueriesValidateParametersAfterIdentityAndRemainReadOnly() throws Exception {
        var tenant=java.util.UUID.randomUUID();var store=java.util.UUID.fromString("20000000-0000-0000-0000-000000000983");
        var principal=new com.rpb.reservation.customerauth.application.CustomerAuthPrincipal(tenant,java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),"ui@example.invalid","UI");
        var actor=new com.rpb.reservation.walkin.api.CurrentActor(tenant,java.util.UUID.randomUUID(),"staff",java.util.Set.of("store_staff"),java.util.Set.of("wallet.view"),java.util.Set.of(store));
        var scope=new com.rpb.reservation.common.scope.StoreScope(new com.rpb.reservation.tenant.value.TenantId(tenant),store);
        when(identity.customer(any())).thenReturn(principal);when(identity.staff()).thenReturn(actor);when(identity.scope(tenant,store)).thenReturn(scope);
        mvc.perform(get("/api/v1/public/stores/"+store+"/wallet/bills?offset=-1")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WALLET_REQUEST_INVALID"));
        mvc.perform(get("/api/v1/stores/"+store+"/wallet/topups?status=arbitrary")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/public/stores/"+store+"/wallet/bills")).andExpect(status().isOk());
        verify(checkout).customerHistory(scope,principal,new WalletHistoryQuery(0,"all"));
        verify(checkout,never()).create(any(),any(),any());verify(topups,never()).create(any(),any(),any());
    }
    @Test void onlyExplicitWalletMethodsReachExistingIdentityChecks() throws Exception {
        when(identity.staff()).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"请先登录"));
        when(identity.customer(any())).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"请先登录"));
        String staff="/api/v1/stores/20000000-0000-0000-0000-000000000983/wallet", id="20000000-0000-0000-0000-000000000983";
        for(String path:new String[]{"/context","/collection-profile","/customers/"+id,"/customers/"+id+"/accounts","/logo/"+id,"/collection-exceptions","/topups/"+id,"/bills?status=pending"})mvc.perform(get(staff+path)).andExpect(status().isUnauthorized());
        mvc.perform(put(staff+"/collection-profile").header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(staff+"/payment-codes/resolve").header("X-RPB-Wallet","1").contentType("application/json").content("{\"code\":\"invalid\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post(staff+"/scan-charges").header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/public/stores/"+id+"/wallet/payment-codes/status").header("X-RPB-Wallet","1").contentType("application/json").content("{\"code\":\"invalid\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get(staff+"/scan-charges")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/public/stores/"+id+"/wallet/payment-codes/status")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/public/stores/"+id+"/wallet/bills/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/public/stores/"+id+"/wallet/bills?status=all")).andExpect(status().isUnauthorized());
        mvc.perform(get(staff+"/internal-debug")).andExpect(status().isForbidden());
        mvc.perform(put(staff+"/collection-exceptions").header("X-RPB-Wallet","1")).andExpect(status().isForbidden());
    }
}
