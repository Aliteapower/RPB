package com.rpb.reservation.wallet;

import com.rpb.reservation.customerauth.api.CustomerAuthCookieService;
import com.rpb.reservation.customerauth.application.*;
import com.rpb.reservation.wallet.api.*;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.walkin.api.*;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WalletApiIdentityTest {
    final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),customer=UUID.randomUUID();
    final CustomerAuthPrincipal principal=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),customer,"test@example.com","test");
    final CustomerAuthApplicationService auth=mock(CustomerAuthApplicationService.class);
    final CurrentActorProvider staff=mock(CurrentActorProvider.class);
    final WalletAccountService accounts=mock(WalletAccountService.class);
    final WalletCheckoutService checkout=mock(WalletCheckoutService.class);
    final WalletAuthorizationService authorization=mock(WalletAuthorizationService.class);
    final WalletScanChargeService charges=mock(WalletScanChargeService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        var identity=new WalletApiIdentity(staff,auth,new CustomerAuthCookieService(false));
        mvc=MockMvcBuilders.standaloneSetup(new WalletCustomerController(identity,accounts,checkout,authorization),new WalletStaffController(identity,accounts,null,null,null,null,null,null),new WalletScanChargeController(identity,charges))
            .setControllerAdvice(new WalletErrorAdvice()).addFilters(new WalletRequestSecurityFilter()).build();
        when(staff.currentActor()).thenReturn(Optional.empty());when(auth.authenticateSession("valid")).thenReturn(Optional.of(principal));
        when(accounts.ownAccounts(any(),any())).thenReturn(List.of());when(checkout.pending(any(),any())).thenReturn(List.of());
    }
    @Test void customerCannotUseEmployeeClaimsOrMissingAndExpiredCookies() throws Exception {
        mvc.perform(get(path("/me")).header("X-Test-Tenant-Id",tenant).header("X-Test-Actor-Id",customer)).andExpect(status().isUnauthorized());
        mvc.perform(get(path("/me")).cookie(new Cookie(CustomerAuthCookieService.COOKIE_NAME,"expired"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(accounts,checkout,authorization);
    }
    @Test void existingCustomerCookieDeterminesScopeAndUnknownReceiptDoesNotLeak() throws Exception {
        mvc.perform(get(path("/me")).cookie(cookie()).header("X-Test-Tenant-Id",UUID.randomUUID())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"));
        verify(accounts).ownAccounts(argThat(s->s.tenantId().value().equals(tenant)&&s.storeId().value().equals(store)),eq(principal));
        when(checkout.ownBill(any(),any(),any())).thenThrow(com.rpb.reservation.wallet.domain.WalletException.missing());
        mvc.perform(get(path("/bills/"+UUID.randomUUID())).cookie(cookie())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }
    @Test void noStaffSessionAndPlatformAdministratorCannotAccessWalletFunds() throws Exception {
        mvc.perform(get("/api/v1/stores/"+store+"/wallet/context")).andExpect(status().isUnauthorized());
        when(staff.currentActor()).thenReturn(Optional.of(new CurrentActor(null,UUID.randomUUID(),"platform_admin",Set.of("platform_admin"),Set.of("wallet.view"),Set.of(store))));
        mvc.perform(get("/api/v1/stores/"+store+"/wallet/context")).andExpect(status().isForbidden());verifyNoInteractions(accounts);
    }
    @Test void scanChargeUsesStaffSessionAndCodeStatusUsesOnlyTheExistingCustomerCookie() throws Exception {
        mvc.perform(post("/api/v1/stores/"+store+"/wallet/scan-charges").header("X-RPB-Wallet","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(path("/payment-codes/status")).header("X-RPB-Wallet","1").contentType("application/json").content("{\"code\":\"invalid\"}")).andExpect(status().isUnauthorized());
        when(charges.status(any(),any(),any())).thenThrow(com.rpb.reservation.wallet.domain.WalletException.missing());
        mvc.perform(post(path("/payment-codes/status")).header("X-RPB-Wallet","1").header("X-Test-Tenant-Id",UUID.randomUUID()).cookie(cookie()).contentType("application/json").content("{\"code\":\"invalid\"}")).andExpect(status().isNotFound());
        verify(charges).status(argThat(s->s.tenantId().value().equals(tenant)&&s.storeId().value().equals(store)),eq(principal),eq("invalid"));
        verify(charges,never()).charge(any(),any(),any());
    }
    String path(String suffix) {return "/api/v1/public/stores/"+store+"/wallet"+suffix;}
    Cookie cookie() {return new Cookie(CustomerAuthCookieService.COOKIE_NAME,"valid");}
}
