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

@WebMvcTest({com.rpb.reservation.wallet.redemption.RedemptionController.class,WalletStaffController.class,WalletCustomerController.class,WalletSupportController.class,WalletCollectionSettingsController.class,WalletScanChargeController.class,com.rpb.reservation.wallet.personalpayment.WalletPersonalPaymentController.class,WalletPointsStaffController.class,com.rpb.reservation.wallet.transactions.TransactionController.class})
@Import({LocalRuntimeSecurityConfiguration.class,LocalRuntimeCurrentActorProvider.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local")
@TestPropertySource(properties="rpb.local-auth.enabled=true")
class WalletLocalRuntimeSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean com.rpb.reservation.wallet.redemption.RedemptionService redemptions;
    @MockBean WalletApiIdentity identity;
    @MockBean com.rpb.reservation.wallet.transactions.TransactionService transactions;
    @MockBean com.rpb.reservation.wallet.integration.WalletPointsAdapter points;
    @MockBean WalletAccountService accounts;
    @MockBean WalletTopupService topups;
    @MockBean WalletCheckoutService checkout;
    @MockBean WalletAuthorizationService authorization;
    @MockBean WalletScanChargeService charges;
    @MockBean com.rpb.reservation.wallet.personalpayment.WalletPersonalPaymentService personal;
    @MockBean WalletRefundService refunds;
    @MockBean WalletReconciliationService reports;
    @MockBean WalletCollectionPort port;
    @MockBean WalletCollectionExceptionService exceptions;
    @MockBean WalletCollectionSettingsService settings;
    @MockBean com.rpb.reservation.appgate.application.AppGateService gate;
    @MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService audit;
    @Test void retiredHistoryListsAreDeniedAndNewQueriesRemainReadOnly() throws Exception {
        var tenant=java.util.UUID.randomUUID();var store=java.util.UUID.fromString("20000000-0000-0000-0000-000000000983");
        var principal=new com.rpb.reservation.customerauth.application.CustomerAuthPrincipal(tenant,java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),"ui@example.invalid","UI");
        var actor=new com.rpb.reservation.walkin.api.CurrentActor(tenant,java.util.UUID.randomUUID(),"staff",java.util.Set.of("store_staff"),java.util.Set.of("wallet.view"),java.util.Set.of(store));
        var scope=new com.rpb.reservation.common.scope.StoreScope(new com.rpb.reservation.tenant.value.TenantId(tenant),store);
        when(identity.customer(any())).thenReturn(principal);when(identity.staff()).thenReturn(actor);when(identity.scope(tenant,store)).thenReturn(scope);
        mvc.perform(get("/api/v1/public/stores/"+store+"/wallet/bills?offset=-1")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/stores/"+store+"/wallet/topups?status=arbitrary")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/public/stores/"+store+"/wallet/transactions")).andExpect(status().isOk());
        verify(transactions).list(scope,null,principal,java.util.Map.of());
        verify(checkout,never()).create(any(),any(),any());verify(topups,never()).create(any(),any(),any());
    }
    @Test void onlyExplicitWalletMethodsReachExistingIdentityChecks() throws Exception {
        when(identity.staff()).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"请先登录"));
        when(identity.customer(any())).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"请先登录"));
        String staff="/api/v1/stores/20000000-0000-0000-0000-000000000983/wallet", id="20000000-0000-0000-0000-000000000983";
        for(String path:new String[]{"/context","/collection-profile","/customers/"+id,"/customers/"+id+"/accounts","/logo/"+id,"/collection-exceptions","/topups/"+id,"/transactions?status=pending"})mvc.perform(get(staff+path)).andExpect(status().isUnauthorized());
        mvc.perform(put(staff+"/collection-profile").header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(staff+"/payment-codes/resolve").header("X-RPB-Wallet","1").contentType("application/json").content("{\"code\":\"invalid\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post(staff+"/scan-charges").header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/public/stores/"+id+"/wallet/payment-codes/status").header("X-RPB-Wallet","1").contentType("application/json").content("{\"code\":\"invalid\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get(staff+"/scan-charges")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/public/stores/"+id+"/wallet/payment-codes/status")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/public/stores/"+id+"/wallet/bills/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/public/stores/"+id+"/wallet/transactions?status=all")).andExpect(status().isUnauthorized());
        mvc.perform(get(staff+"/points/context")).andExpect(status().isUnauthorized());
        mvc.perform(post(staff+"/points/context").header("X-RPB-Wallet","1")).andExpect(status().isForbidden());
        mvc.perform(get(staff+"/internal-debug")).andExpect(status().isForbidden());
        mvc.perform(put(staff+"/collection-exceptions").header("X-RPB-Wallet","1")).andExpect(status().isForbidden());
    }

    @Test void unifiedPersonalRoutesOnlyAllowExactMethodsAndReachIdentity() throws Exception {
        when(identity.staff()).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"login"));
        when(identity.customer(any())).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"login"));
        String staff="/api/v1/stores/20000000-0000-0000-0000-000000000983/wallet/",customer="/api/v1/public/stores/20000000-0000-0000-0000-000000000983/wallet/";
        for(String endpoint:new String[]{staff+"personal-scan-charges/result?idempotencyKey=x",customer+"personal-payment-consent"})mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        for(String endpoint:new String[]{staff+"personal-scan-charges",staff+"personal-scan-charges/reconcile",customer+"personal-payment-consent",customer+"personal-codes",customer+"personal-codes/status"})mvc.perform(post(endpoint).header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(put(staff+"personal-payment-policy").header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get(customer+"personal-codes")).andExpect(status().isForbidden());mvc.perform(post(staff+"personal-payment-policy").header("X-RPB-Wallet","1")).andExpect(status().isForbidden());
    }
    @Test void unifiedTransactionRoutesAreGetOnlyAndRequireRealIdentity() throws Exception {
        when(identity.staff()).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"login"));
        when(identity.customer(any())).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"login"));
        String id="20000000-0000-0000-0000-000000000983";
        for(String prefix:new String[]{"/api/v1/stores/","/api/v1/public/stores/"}) {
            String path=prefix+id+"/wallet/transactions";
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("WALLET_UNAUTHENTICATED"));
            mvc.perform(get(path+"/bill/"+id)).andExpect(status().isUnauthorized());
            mvc.perform(post(path).header("X-RPB-Wallet","1")).andExpect(status().isForbidden());
            mvc.perform(delete(path+"/bill/"+id).header("X-RPB-Wallet","1")).andExpect(status().isForbidden());
            mvc.perform(get(path+"/internal-debug")).andExpect(status().isForbidden());
            String base=prefix+id+"/wallet";
            for(String legacy:new String[]{"/bills","/topups","/refunds","/accounts/"+id+"/entries","/points/entries","/customers/"+id+"/points/entries"})mvc.perform(get(base+legacy)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(transactions);
    }
    @Test void merchantRedemptionRoutesRequireIdentityAndOnlyDeclaredMethods() throws Exception {
        when(identity.staff()).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"login"));
        when(identity.customer(any())).thenThrow(new WalletException("WALLET_UNAUTHENTICATED",401,"login"));
        String id="20000000-0000-0000-0000-000000000983",staff="/api/v1/stores/"+id+"/wallet/",customer="/api/v1/public/stores/"+id+"/wallet/";
        for(String path:new String[]{staff+"points-redemptions/"+id,customer+"points-redemptions/"+id})mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for(String path:new String[]{staff+"points-redemptions",staff+"points-redemptions/"+id+"/code",staff+"points-redemptions/"+id+"/cancel",customer+"points-redemption-code",customer+"points-redemptions/"+id+"/confirm"})mvc.perform(post(path).header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get(staff+"points-redemptions")).andExpect(status().isForbidden());
        mvc.perform(post(customer+"points-redemptions/"+id+"/confirm").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(customer+"points-redemptions/"+id+"/confirm").header("X-RPB-Wallet","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(redemptions);
    }

    @Test void redemptionPayloadRejectsFractionsUnknownFieldsAndCustomerOverrides() throws Exception {
        String id="20000000-0000-0000-0000-000000000983",staff="/api/v1/stores/"+id+"/wallet/points-redemptions",customer="/api/v1/public/stores/"+id+"/wallet/points-redemptions/"+id+"/confirm";
        mvc.perform(post(staff).header("X-RPB-Wallet","1").contentType("application/json").content("{\"quantity\":1.5}")).andExpect(status().isBadRequest());
        mvc.perform(post(customer).header("X-RPB-Wallet","1").contentType("application/json").content("{\"expectedVersion\":1,\"customerId\":\""+id+"\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(redemptions);
    }

}
