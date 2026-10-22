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

@WebMvcTest({WalletStaffController.class,WalletCustomerController.class,WalletSupportController.class,WalletCollectionSettingsController.class})
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
    @MockBean WalletRefundService refunds;
    @MockBean WalletReconciliationService reports;
    @MockBean WalletCollectionPort port;
    @MockBean WalletCollectionExceptionService exceptions;
    @MockBean WalletCollectionSettingsService settings;
    @MockBean com.rpb.reservation.appgate.application.AppGateService gate;
    @MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService audit;
    @Test void onlyExplicitWalletMethodsReachExistingIdentityChecks() throws Exception {
        when(identity.staff()).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"请先登录"));
        when(identity.customer(any())).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"请先登录"));
        String staff="/api/v1/stores/20000000-0000-0000-0000-000000000983/wallet", id="20000000-0000-0000-0000-000000000983";
        for(String path:new String[]{"/context","/collection-profile","/customers/"+id,"/customers/"+id+"/accounts","/logo/"+id,"/collection-exceptions"})mvc.perform(get(staff+path)).andExpect(status().isUnauthorized());
        mvc.perform(put(staff+"/collection-profile").header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(staff+"/payment-codes/resolve").header("X-RPB-Wallet","1").contentType("application/json").content("{\"code\":\"invalid\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/public/stores/"+id+"/wallet/bills/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(get(staff+"/internal-debug")).andExpect(status().isForbidden());
        mvc.perform(put(staff+"/collection-exceptions").header("X-RPB-Wallet","1")).andExpect(status().isForbidden());
    }
}
